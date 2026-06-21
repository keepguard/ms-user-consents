package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.application.dto.compliance.ConsentStatusDetailViewDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ComplianceCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Compliance Query Service Tests")
class ComplianceQueryServiceTest {

    private ComplianceQueryService service;

    @Mock
    private ConsentDocumentRepositoryPort consentDocumentRepository;

    @Mock
    private UserConsentRepositoryPort userConsentRepository;

    @Mock
    private ComplianceCachePort cachePort;

    @Mock
    private MetricsPort metricsPort;

    private UUID userId;
    private ConsentDocument publishedDocument;
    private UserConsent userConsent;

    @BeforeEach
    void setUp() {
        service = new ComplianceQueryService(consentDocumentRepository, userConsentRepository, cachePort, metricsPort);

        userId = UUID.randomUUID();

        publishedDocument = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "published/terms_v1.pdf",
                "hash123",
                1024L,
                "application/pdf",
                1
        );

        publishedDocument = publishedDocument.publish("admin@test.com", "published/terms_v1.pdf");

        userConsent = UserConsent.accept(
                userId,
                "user@example.com",
                publishedDocument.getId(),
                1,
                LocalDateTime.now(),
                "192.168.1.1",
                "Mozilla/5.0",
                "São Paulo, BR"
        );
    }

    @Test
    @DisplayName("Deve verificar compliance do usuário do cache")
    void shouldCheckUserComplianceFromCache() {
        // Given
        ComplianceStatusViewDTO cachedCompliance = ComplianceStatusViewDTO.builder()
                .userId(userId)
                .compliant(true)
                .consents(List.of())
                .missingMandatory(List.of())
                .build();

        when(cachePort.getUserCompliance(userId)).thenReturn(cachedCompliance);

        // When
        ComplianceStatusViewDTO result = service.checkUserCompliance(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isCompliant());
        verify(cachePort, times(1)).getUserCompliance(userId);
        verify(consentDocumentRepository, never()).findByStatus(any());
        verify(userConsentRepository, never()).findByUserId(any());
        verify(metricsPort, times(1)).incrementCounter(eq("compliance_queries_total"), any());
    }

    @Test
    @DisplayName("Deve verificar compliance do usuário do repositório quando não está em cache")
    void shouldCheckUserComplianceFromRepositoryWhenNotInCache() {
        // Given
        when(cachePort.getUserCompliance(userId)).thenReturn(null);
        when(consentDocumentRepository.findByStatus(ConsentDocumentStatus.PUBLISHED))
                .thenReturn(List.of(publishedDocument));
        when(userConsentRepository.findByUserId(userId)).thenReturn(List.of(userConsent));

        // When
        ComplianceStatusViewDTO result = service.checkUserCompliance(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertTrue(result.isCompliant());
        assertNotNull(result.getConsents());
        assertTrue(result.getMissingMandatory().isEmpty());
        verify(cachePort, times(1)).getUserCompliance(userId);
        verify(consentDocumentRepository, times(1)).findByStatus(ConsentDocumentStatus.PUBLISHED);
        verify(userConsentRepository, times(1)).findByUserId(userId);
        verify(cachePort, times(1)).cacheUserCompliance(eq(userId), any());
        verify(metricsPort, times(2)).incrementCounter(anyString(), any());
    }

    @Test
    @DisplayName("Deve identificar usuário não compliant")
    void shouldIdentifyNonCompliantUser() {
        // Given
        ConsentDocument privacyDocument = ConsentDocument.create(
                "Política de Privacidade",
                "Descrição",
                ConsentType.PRIVACY_POLICY,
                "admin@test.com",
                "published/privacy_v1.pdf",
                "hash456",
                1024L,
                "application/pdf",
                1
        );
        privacyDocument = privacyDocument.publish("admin@test.com", "published/privacy_v1.pdf");

        when(cachePort.getUserCompliance(userId)).thenReturn(null);
        when(consentDocumentRepository.findByStatus(ConsentDocumentStatus.PUBLISHED))
                .thenReturn(List.of(publishedDocument, privacyDocument));
        when(userConsentRepository.findByUserId(userId)).thenReturn(List.of(userConsent));

        // When
        ComplianceStatusViewDTO result = service.checkUserCompliance(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertFalse(result.isCompliant());
        assertNotNull(result.getMissingMandatory());
        assertFalse(result.getMissingMandatory().isEmpty());
        verify(metricsPort, times(1)).incrementCounter(eq("compliance_status_total"), any());
    }

    @Test
    @DisplayName("Deve verificar consentimentos obrigatórios")
    void shouldCheckMandatoryConsents() {
        // Given
        ConsentDocument mandatoryDocument = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "published/terms_v1.pdf",
                "hash123",
                1024L,
                "application/pdf",
                1
        );
        mandatoryDocument = mandatoryDocument.publish("admin@test.com", "published/terms_v1.pdf");

        when(consentDocumentRepository.findLatestPublishedByType(ConsentType.TERMS_OF_USE))
                .thenReturn(java.util.Optional.of(mandatoryDocument));
        when(userConsentRepository.existsByUserIdAndConsentDocumentIdAndVersion(
                userId, mandatoryDocument.getId(), mandatoryDocument.getVersion()))
                .thenReturn(true);

        // When
        boolean result = service.hasMandatoryConsents(userId);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Deve retornar false quando usuário não aceitou consentimento obrigatório")
    void shouldReturnFalseWhenUserDidNotAcceptMandatoryConsent() {
        // Given
        ConsentDocument mandatoryDocument = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "published/terms_v1.pdf",
                "hash123",
                1024L,
                "application/pdf",
                1
        );
        mandatoryDocument = mandatoryDocument.publish("admin@test.com", "published/terms_v1.pdf");

        when(consentDocumentRepository.findLatestPublishedByType(ConsentType.TERMS_OF_USE))
                .thenReturn(java.util.Optional.of(mandatoryDocument));
        when(userConsentRepository.existsByUserIdAndConsentDocumentIdAndVersion(
                userId, mandatoryDocument.getId(), mandatoryDocument.getVersion()))
                .thenReturn(false);

        // When
        boolean result = service.hasMandatoryConsents(userId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Deve listar todos os tipos de consentimento")
    void shouldListAllConsentTypes() {
        // When
        List<ConsentType> result = service.listAllConsentTypes();

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains(ConsentType.TERMS_OF_USE));
        assertTrue(result.contains(ConsentType.PRIVACY_POLICY));
    }

    @Test
    @DisplayName("Deve listar apenas tipos de consentimento obrigatórios")
    void shouldListMandatoryConsentTypes() {
        // When
        List<ConsentType> result = service.listMandatoryConsentTypes();

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        result.forEach(type -> assertTrue(type.isMandatory()));
    }
}

