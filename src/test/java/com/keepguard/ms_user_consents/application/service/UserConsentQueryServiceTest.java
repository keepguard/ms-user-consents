package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.UserConsentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.out.cache.UserConsentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Consent Query Service Tests")
class UserConsentQueryServiceTest {

    private UserConsentQueryService service;

    @Mock
    private UserConsentRepositoryPort repositoryPort;

    @Mock
    private UserConsentCachePort cachePort;

    @Mock
    private UserConsentApplicationMapper mapper;

    @Mock
    private MetricsPort metricsPort;

    private UserConsent consent;
    private UserConsentViewDTO viewDTO;
    private UUID userId;
    private UUID consentId;
    private UUID consentDocumentId;

    @BeforeEach
    void setUp() {
        service = new UserConsentQueryService(repositoryPort, cachePort, mapper, metricsPort);

        userId = UUID.randomUUID();
        consentId = UUID.randomUUID();
        consentDocumentId = UUID.randomUUID();

        consent = UserConsent.accept(
                userId,
                "user@example.com",
                consentDocumentId,
                1,
                LocalDateTime.now(),
                "192.168.1.1",
                "Mozilla/5.0",
                "São Paulo, BR"
        );

        viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
                .userId(userId)
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve buscar último consentimento do cache")
    void shouldFindLatestFromCache() {
        // Given
        when(cachePort.getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId)).thenReturn(viewDTO);
        when(mapper.toDomain(viewDTO)).thenReturn(consent);

        // When
        UserConsent result = service.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        verify(repositoryPort, never()).findLatestByUserIdAndConsentDocumentId(any(), any());
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar último consentimento do repositório quando não está em cache")
    void shouldFindLatestFromRepositoryWhenNotInCache() {
        // Given
        when(cachePort.getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId)).thenReturn(null);
        when(repositoryPort.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId))
                .thenReturn(Optional.of(consent));
        when(mapper.toViewDTO(consent)).thenReturn(viewDTO);

        // When
        UserConsent result = service.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        verify(repositoryPort, times(1)).findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        verify(cachePort, times(1)).cacheLatestByUserIdAndConsentDocumentId(userId, consentDocumentId, viewDTO);
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_queries_total"), any());
    }

    @Test
    @DisplayName("Deve verificar aceite do cache")
    void shouldCheckHasAcceptedFromCache() {
        // Given
        when(cachePort.hasAccepted(userId, consentDocumentId, 1)).thenReturn(true);

        // When
        boolean result = service.hasAccepted(userId, consentDocumentId, 1);

        // Then
        assertTrue(result);
        verify(cachePort, times(1)).hasAccepted(userId, consentDocumentId, 1);
        verify(repositoryPort, never()).existsByUserIdAndConsentDocumentIdAndVersion(any(), any(), anyInt());
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_queries_total"), any());
    }

    @Test
    @DisplayName("Deve verificar aceite do repositório quando não está em cache")
    void shouldCheckHasAcceptedFromRepositoryWhenNotInCache() {
        // Given
        when(cachePort.hasAccepted(userId, consentDocumentId, 1)).thenReturn(null);
        when(repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1))
                .thenReturn(true);

        // When
        boolean result = service.hasAccepted(userId, consentDocumentId, 1);

        // Then
        assertTrue(result);
        verify(cachePort, times(1)).hasAccepted(userId, consentDocumentId, 1);
        verify(repositoryPort, times(1)).existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, 1);
        verify(cachePort, times(1)).cacheHasAccepted(userId, consentDocumentId, 1, true);
        verify(metricsPort, times(1)).incrementCounter(eq("user_consent_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar consentimentos por userId do cache")
    void shouldFindByUserIdFromCache() {
        // Given
        List<UserConsentViewDTO> cachedList = List.of(viewDTO);
        when(cachePort.getByUserId(userId)).thenReturn(cachedList);
        when(mapper.toDomain(viewDTO)).thenReturn(consent);

        // When
        List<UserConsent> result = service.findByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getByUserId(userId);
        verify(repositoryPort, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Deve buscar consentimentos por userId do repositório")
    void shouldFindByUserIdFromRepository() {
        // Given
        when(cachePort.getByUserId(userId)).thenReturn(null);
        when(repositoryPort.findByUserId(userId)).thenReturn(List.of(consent));
        when(mapper.toViewDTO(consent)).thenReturn(viewDTO);

        // When
        List<UserConsent> result = service.findByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getByUserId(userId);
        verify(repositoryPort, times(1)).findByUserId(userId);
        verify(cachePort, times(1)).cacheByUserId(eq(userId), any());
    }
}

