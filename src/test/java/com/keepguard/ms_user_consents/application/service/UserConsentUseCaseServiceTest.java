package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.UserConsentApplicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Consent UseCase Service Tests")
class UserConsentUseCaseServiceTest {

    private UserConsentUseCaseService service;

    @Mock
    private UserConsentCommandService commandService;

    @Mock
    private UserConsentQueryService queryService;

    @Mock
    private UserConsentApplicationMapper mapper;

    private UserConsentAcceptAllCommandDTO acceptAllCommand;
    private UserConsentAcceptAllResultDTO acceptAllResult;
    private UserConsentViewDTO userConsentViewDTO;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new UserConsentUseCaseService(commandService, queryService, mapper);

        userId = UUID.randomUUID();

        acceptAllCommand = UserConsentAcceptAllCommandDTO.builder()
                .userId(userId)
                .email("user@example.com")
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        userConsentViewDTO = UserConsentViewDTO.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(UUID.randomUUID())
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        acceptAllResult = UserConsentAcceptAllResultDTO.builder()
                .acceptedConsents(List.of(userConsentViewDTO))
                .build();
    }

    @Test
    @DisplayName("Deve aceitar todos os consentimentos com sucesso")
    void shouldAcceptAllSuccessfully() {
        // Given
        when(commandService.acceptAll(acceptAllCommand)).thenReturn(acceptAllResult);

        // When
        UserConsentAcceptAllResultDTO result = service.acceptAll(acceptAllCommand);

        // Then
        assertNotNull(result);
        assertNotNull(result.getAcceptedConsents());
        assertEquals(1, result.getAcceptedConsents().size());
        assertEquals(userId, result.getAcceptedConsents().get(0).getUserId());
        verify(commandService).acceptAll(acceptAllCommand);
    }

    @Test
    @DisplayName("Deve retornar resultado vazio quando não há consentimentos aceitos")
    void shouldAcceptAllWithEmptyResult() {
        // Given
        UserConsentAcceptAllResultDTO emptyResult = UserConsentAcceptAllResultDTO.builder()
                .acceptedConsents(Collections.emptyList())
                .build();
        when(commandService.acceptAll(acceptAllCommand)).thenReturn(emptyResult);

        // When
        UserConsentAcceptAllResultDTO result = service.acceptAll(acceptAllCommand);

        // Then
        assertNotNull(result);
        assertNotNull(result.getAcceptedConsents());
        assertTrue(result.getAcceptedConsents().isEmpty());
        verify(commandService).acceptAll(acceptAllCommand);
    }

    @Test
    @DisplayName("Deve delegar corretamente para o command service")
    void shouldDelegateCorrectlyToCommandService() {
        // Given
        when(commandService.acceptAll(any(UserConsentAcceptAllCommandDTO.class))).thenReturn(acceptAllResult);

        // When
        service.acceptAll(acceptAllCommand);

        // Then
        verify(commandService, times(1)).acceptAll(acceptAllCommand);
        verifyNoInteractions(queryService);
        verifyNoInteractions(mapper);
    }
}
