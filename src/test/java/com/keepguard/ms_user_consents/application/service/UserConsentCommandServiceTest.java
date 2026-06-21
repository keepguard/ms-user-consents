package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ComplianceCachePort;
import com.keepguard.ms_user_consents.application.port.out.cache.UserConsentCachePort;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Consent Command Service Tests")
class UserConsentCommandServiceTest {

    private UserConsentCommandService service;

    @Mock
    private UserConsentRepositoryPort repositoryPort;

    @Mock
    private ConsentDocumentRepositoryPort consentDocumentRepositoryPort;

    @Mock
    private UserConsentCachePort userConsentCachePort;

    @Mock
    private ComplianceCachePort complianceCachePort;

    @Mock
    private MetricsPort metricsPort;

    private UserConsentCreateCommandDTO acceptCommand;
    private UserConsent savedConsent;
    private UUID userId;
    private UUID consentDocumentId;

    @BeforeEach
    void setUp() {
        service = new UserConsentCommandService(repositoryPort, consentDocumentRepositoryPort, userConsentCachePort, complianceCachePort, metricsPort);

        userId = UUID.randomUUID();
        consentDocumentId = UUID.randomUUID();

        acceptCommand = UserConsentCreateCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        savedConsent = UserConsent.accept(
                userId,
                "user@example.com",
                consentDocumentId,
                1,
                LocalDateTime.now(),
                "192.168.1.1",
                "Mozilla/5.0",
                "São Paulo, BR"
        );
    }

    @Test
    @DisplayName("Deve aceitar consentimento com sucesso")
    void shouldAcceptConsentSuccessfully() {
        // Given
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(savedConsent);

        // When
        UserConsent result = service.accept(acceptCommand);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(consentDocumentId, result.getConsentDocumentId());
        assertEquals(1, result.getVersion());
        verify(repositoryPort, times(1)).save(any(UserConsent.class));
        verify(userConsentCachePort, times(1)).removeByUserId(userId);
        verify(userConsentCachePort, times(1)).removeLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        verify(userConsentCachePort, times(1)).removeHasAccepted(userId, consentDocumentId, 1);
        verify(complianceCachePort, times(1)).removeUserCompliance(userId);
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_accepted_total"), any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar aceitar consentimento já aceito")
    void shouldThrowExceptionWhenAcceptingAlreadyAcceptedConsent() {
        // Given
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1))
                .thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.accept(acceptCommand));
        assertEquals("Usuário já aceitou esta versão do documento", exception.getMessage());
        verify(repositoryPort, never()).save(any(UserConsent.class));
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_business_errors_total"), any());
    }

    @Test
    @DisplayName("Deve aceitar consentimento sem informações de auditoria")
    void shouldAcceptConsentWithoutAuditInfo() {
        // Given
        UserConsentCreateCommandDTO commandWithoutAudit = UserConsentCreateCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress(null)
                .userAgent(null)
                .geolocation(null)
                .build();

        UserConsent consentWithoutAudit = UserConsent.accept(
                userId, "user@example.com", consentDocumentId, 1, LocalDateTime.now(), null, null, null
        );

        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(consentWithoutAudit);

        // When
        UserConsent result = service.accept(commandWithoutAudit);

        // Then
        assertNotNull(result);
        assertNull(result.getIpAddress());
        assertNull(result.getUserAgent());
        assertNull(result.getGeolocation());
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_accepted_total"), any());
    }

    @Test
    @DisplayName("Deve invalidar cache corretamente após aceitar")
    void shouldInvalidateCacheCorrectlyAfterAccept() {
        // Given
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(savedConsent);

        // When
        service.accept(acceptCommand);

        // Then
        verify(userConsentCachePort).removeByUserId(userId);
        verify(userConsentCachePort).removeLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        verify(userConsentCachePort).removeHasAccepted(userId, consentDocumentId, 1);
        verify(complianceCachePort).removeUserCompliance(userId);
    }

    @Test
    @DisplayName("Deve aceitar todos os documentos publicados com sucesso")
    void shouldAcceptAllPublishedDocumentsSuccessfully() {
        // Given
        UUID doc1Id = UUID.randomUUID();
        UUID doc2Id = UUID.randomUUID();
        
        ConsentDocument doc1 = ConsentDocument.fromJpa(doc1Id, "Termos", "Desc", 1, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.TERMS_OF_USE, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        ConsentDocument doc2 = ConsentDocument.fromJpa(doc2Id, "Privacidade", "Desc", 2, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.PRIVACY_POLICY, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        
        List<ConsentDocument> publishedDocuments = Arrays.asList(doc1, doc2);
        
        UserConsentAcceptAllCommandDTO command = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();
        
        UserConsent savedConsent1 = UserConsent.accept(userId, "user@example.com", doc1Id, 1, 
                LocalDateTime.now(), "192.168.1.1", "Mozilla/5.0", "São Paulo, BR");
        UserConsent savedConsent2 = UserConsent.accept(userId, "user@example.com", doc2Id, 2, 
                LocalDateTime.now(), "192.168.1.1", "Mozilla/5.0", "São Paulo, BR");
        
        when(consentDocumentRepositoryPort.findAllPublished()).thenReturn(publishedDocuments);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc1Id, 1))
                .thenReturn(false);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc2Id, 2))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class)))
                .thenReturn(savedConsent1)
                .thenReturn(savedConsent2);
        
        // When
        UserConsentAcceptAllResultDTO result = service.acceptAll(command);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getAcceptedConsents());
        assertEquals(2, result.getAcceptedConsents().size());
        verify(repositoryPort, times(2)).save(any(UserConsent.class));
        verify(metricsPort).incrementCounter(eq("user_consent_accept_all_total"), any());
        verify(metricsPort).incrementCounter(eq("user_consent_accepted_batch_total"), any());
    }

    @Test
    @DisplayName("Deve ignorar documentos já aceitos")
    void shouldAcceptAllIgnoringAlreadyAccepted() {
        // Given
        UUID doc1Id = UUID.randomUUID();
        UUID doc2Id = UUID.randomUUID();
        
        ConsentDocument doc1 = ConsentDocument.fromJpa(doc1Id, "Termos", "Desc", 1, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.TERMS_OF_USE, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        ConsentDocument doc2 = ConsentDocument.fromJpa(doc2Id, "Privacidade", "Desc", 2, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.PRIVACY_POLICY, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        
        List<ConsentDocument> publishedDocuments = Arrays.asList(doc1, doc2);
        
        UserConsentAcceptAllCommandDTO command = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();
        
        UserConsent savedConsent2 = UserConsent.accept(userId, "user@example.com", doc2Id, 2, 
                LocalDateTime.now(), "192.168.1.1", "Mozilla/5.0", "São Paulo, BR");
        
        when(consentDocumentRepositoryPort.findAllPublished()).thenReturn(publishedDocuments);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc1Id, 1))
                .thenReturn(true); // Já aceito
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc2Id, 2))
                .thenReturn(false); // Não aceito
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(savedConsent2);
        
        // When
        UserConsentAcceptAllResultDTO result = service.acceptAll(command);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getAcceptedConsents());
        assertEquals(1, result.getAcceptedConsents().size());
        verify(repositoryPort, times(1)).save(any(UserConsent.class));
        verify(metricsPort).incrementCounter(eq("user_consent_ignored_batch_total"), any());
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há documentos publicados")
    void shouldAcceptAllWithEmptyPublishedDocuments() {
        // Given
        UserConsentAcceptAllCommandDTO command = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();
        
        when(consentDocumentRepositoryPort.findAllPublished()).thenReturn(Collections.emptyList());
        
        // When
        UserConsentAcceptAllResultDTO result = service.acceptAll(command);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getAcceptedConsents());
        assertTrue(result.getAcceptedConsents().isEmpty());
        verify(repositoryPort, never()).save(any(UserConsent.class));
        verify(metricsPort).incrementCounter(eq("user_consent_accept_all_total"), any());
    }

    @Test
    @DisplayName("Deve invalidar cache corretamente após aceite em lote")
    void shouldAcceptAllAndInvalidateCaches() {
        // Given
        UUID doc1Id = UUID.randomUUID();
        ConsentDocument doc1 = ConsentDocument.fromJpa(doc1Id, "Termos", "Desc", 1, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.TERMS_OF_USE, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        
        List<ConsentDocument> publishedDocuments = Arrays.asList(doc1);
        
        UserConsentAcceptAllCommandDTO command = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();
        
        UserConsent savedConsent = UserConsent.accept(userId, "user@example.com", doc1Id, 1, 
                LocalDateTime.now(), "192.168.1.1", "Mozilla/5.0", "São Paulo, BR");
        
        when(consentDocumentRepositoryPort.findAllPublished()).thenReturn(publishedDocuments);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc1Id, 1))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(savedConsent);
        
        // When
        service.acceptAll(command);
        
        // Then
        verify(userConsentCachePort).removeByUserId(userId);
        verify(userConsentCachePort).removeLatestByUserIdAndConsentDocumentId(userId, doc1Id);
        verify(userConsentCachePort).removeHasAccepted(userId, doc1Id, 1);
        verify(complianceCachePort).removeUserCompliance(userId);
    }

    @Test
    @DisplayName("Deve registrar métricas corretamente após aceite em lote")
    void shouldAcceptAllAndRegisterMetrics() {
        // Given
        UUID doc1Id = UUID.randomUUID();
        ConsentDocument doc1 = ConsentDocument.fromJpa(doc1Id, "Termos", "Desc", 1, 
                ConsentDocumentStatus.PUBLISHED, ConsentType.TERMS_OF_USE, LocalDateTime.now(), 
                LocalDateTime.now(), "admin", "admin", null, null, null, null);
        
        List<ConsentDocument> publishedDocuments = Arrays.asList(doc1);
        
        UserConsentAcceptAllCommandDTO command = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();
        
        UserConsent savedConsent = UserConsent.accept(userId, "user@example.com", doc1Id, 1, 
                LocalDateTime.now(), "192.168.1.1", "Mozilla/5.0", "São Paulo, BR");
        
        when(consentDocumentRepositoryPort.findAllPublished()).thenReturn(publishedDocuments);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, doc1Id, 1))
                .thenReturn(false);
        when(repositoryPort.save(any(UserConsent.class))).thenReturn(savedConsent);
        
        // When
        service.acceptAll(command);
        
        // Then
        verify(metricsPort).incrementCounter(eq("user_consent_accept_all_total"), any());
        verify(metricsPort).incrementCounter(eq("user_consent_accepted_batch_total"), any());
        verify(metricsPort, never()).incrementCounter(eq("user_consent_ignored_batch_total"), any());
    }
}

