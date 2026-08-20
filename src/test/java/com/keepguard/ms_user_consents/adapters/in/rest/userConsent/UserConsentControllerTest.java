package com.keepguard.ms_user_consents.adapters.in.rest.userConsent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentAcceptAllResponseDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.port.in.UserConsentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes unitários para UserConsentController
 * Testa os endpoints REST e mapeamento de DTOs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Consent Controller Tests")
class UserConsentControllerTest {

    private MockMvc mockMvc;
    
    private ObjectMapper objectMapper;

    @Mock
    private UserConsentPort userConsentPort;

    @Mock
    private com.keepguard.ms_user_consents.adapters.in.rest.userConsent.mapper.UserConsentAdapterMapper mapper;

    @InjectMocks
    private UserConsentController userConsentController;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(userConsentController).build();
    }

    @Test
    @DisplayName("Deve aceitar consentimento com sucesso")
    void shouldAcceptConsentSuccessfully() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentDocumentId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();

        UserConsentViewDTO viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        String requestBody = """
                {
                    "userId": "%s",
                    "email": "user@example.com",
                    "consentDocumentId": "%s",
                    "version": 1,
                    "acceptedAt": "2025-01-13T21:00:00.000Z",
                    "geolocation": "São Paulo, BR"
                }
                """.formatted(userId, consentDocumentId);

        when(userConsentPort.accept(any(UserConsentCreateCommandDTO.class))).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(UserConsentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.consentDocumentId").value(consentDocumentId.toString()));
    }

    @Test
    @DisplayName("Deve buscar consentimento por ID com sucesso")
    void shouldFindConsentByIdSuccessfully() throws Exception {
        // Given
        UUID consentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID consentDocumentId = UUID.randomUUID();

        UserConsentViewDTO viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        when(userConsentPort.findById(consentId)).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(UserConsentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(get("/api/v1/user-consents/{id}", consentId)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.consentDocumentId").value(consentDocumentId.toString()));
    }

    @Test
    @DisplayName("Deve buscar consentimentos por userId com sucesso")
    void shouldFindConsentsByUserIdSuccessfully() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentDocumentId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();

        UserConsentViewDTO viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO.builder()
                .id(consentId)
                .userId(userId)
                .email("user@example.com")
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .geolocation("São Paulo, BR")
                .build();

        when(userConsentPort.findByUserId(userId)).thenReturn(List.of(viewDTO));
        when(mapper.toResponseDTO(any(UserConsentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(get("/api/v1/user-consents/user/{userId}", userId)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()));
    }

    @Test
    @DisplayName("Deve verificar se usuário aceitou versão específica")
    void shouldCheckIfUserAcceptedSpecificVersion() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentDocumentId = UUID.randomUUID();

        when(userConsentPort.hasAccepted(userId, consentDocumentId, 1)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/v1/user-consents/user/{userId}/document/{consentDocumentId}/version/{version}/check",
                        userId, consentDocumentId, 1)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    @DisplayName("Deve retornar 400 quando dados inválidos")
    void shouldReturn400WhenInvalidData() throws Exception {
        // Given
        String invalidRequestBody = """
                {
                    "userId": "invalid-uuid",
                    "consentDocumentId": "invalid-uuid",
                    "version": 1
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .content(invalidRequestBody))
                        .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve aceitar todos os consentimentos com sucesso")
    void shouldAcceptAllSuccessfully() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();

        UserConsentViewDTO viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
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

        var responseDTO = UserConsentAcceptAllResponseDTO.builder()
                .acceptedConsents(List.of(
                        UserConsentAcceptAllResponseDTO.AcceptedConsentItemDTO.builder()
                                .id(consentId)
                                .userId(userId)
                                .email("user@example.com")
                                .consentDocumentId(UUID.randomUUID())
                                .version(1)
                                .acceptedAt(LocalDateTime.now())
                                .createdAt(LocalDateTime.now())
                                .ipAddress("192.168.1.1")
                                .userAgent("Mozilla/5.0")
                                .geolocation("São Paulo, BR")
                                .build()
                ))
                .totalAccepted(1)
                .build();

        String requestBody = """
                {
                    "userId": "%s",
                    "email": "user@example.com",
                    "acceptedAt": "2025-01-13T21:00:00.000Z",
                    "geolocation": "São Paulo, BR"
                }
                """.formatted(userId);

        when(userConsentPort.acceptAll(any(UserConsentAcceptAllCommandDTO.class)))
                .thenReturn(UserConsentAcceptAllResultDTO.builder()
                        .acceptedConsents(List.of(viewDTO))
                        .build());
        when(mapper.toAcceptAllResponseDTO(any(UserConsentAcceptAllResultDTO.class)))
                .thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .header("User-Agent", "Mozilla/5.0")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAccepted").value(1))
                .andExpect(jsonPath("$.acceptedConsents").isArray())
                .andExpect(jsonPath("$.acceptedConsents[0].userId").value(userId.toString()));
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há documentos publicados")
    void shouldAcceptAllWithEmptyResult() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();

        var responseDTO = UserConsentAcceptAllResponseDTO.builder()
                .acceptedConsents(Collections.emptyList())
                .totalAccepted(0)
                .build();

        String requestBody = """
                {
                    "userId": "%s",
                    "email": "user@example.com",
                    "acceptedAt": "2025-01-13T21:00:00.000Z"
                }
                """.formatted(userId);

        when(userConsentPort.acceptAll(any(UserConsentAcceptAllCommandDTO.class)))
                .thenReturn(UserConsentAcceptAllResultDTO.builder()
                        .acceptedConsents(Collections.emptyList())
                        .build());
        when(mapper.toAcceptAllResponseDTO(any(UserConsentAcceptAllResultDTO.class)))
                .thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAccepted").value(0))
                .andExpect(jsonPath("$.acceptedConsents").isArray())
                .andExpect(jsonPath("$.acceptedConsents").isEmpty());
    }

    @Test
    @DisplayName("Deve capturar IP address corretamente")
    void shouldAcceptAllAndCaptureIpAddress() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "userId": "%s",
                    "email": "user@example.com",
                    "acceptedAt": "2025-01-13T21:00:00.000Z"
                }
                """.formatted(userId);

        when(userConsentPort.acceptAll(any(UserConsentAcceptAllCommandDTO.class)))
                .thenReturn(UserConsentAcceptAllResultDTO.builder()
                        .acceptedConsents(Collections.emptyList())
                        .build());
        when(mapper.toAcceptAllResponseDTO(any(UserConsentAcceptAllResultDTO.class)))
                .thenReturn(UserConsentAcceptAllResponseDTO.builder()
                        .acceptedConsents(Collections.emptyList())
                        .totalAccepted(0)
                        .build());

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .content(requestBody))
                .andExpect(status().isCreated());

        verify(userConsentPort).acceptAll(argThat(command -> 
                command.getIpAddress().equals("203.0.113.195")));
    }

    @Test
    @DisplayName("Deve validar X-Tenant-Id header obrigatório")
    void shouldAcceptAllValidateTenantId() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "userId": "%s",
                    "email": "user@example.com",
                    "acceptedAt": "2025-01-13T21:00:00.000Z"
                }
                """.formatted(userId);

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userConsentPort);
    }

    @Test
    @DisplayName("Deve retornar 400 quando dados de entrada inválidos")
    void shouldAcceptAllReturnBadRequestWithInvalidData() throws Exception {
        // Given
        String invalidRequestBody = """
                {
                    "userId": "invalid-uuid",
                    "email": "",
                    "acceptedAt": "invalid-date"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/user-consents/accept-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userConsentPort);
    }
}

