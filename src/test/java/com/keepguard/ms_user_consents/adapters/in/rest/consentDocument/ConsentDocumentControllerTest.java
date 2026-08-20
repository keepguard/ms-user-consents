package com.keepguard.ms_user_consents.adapters.in.rest.consentDocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.application.port.in.ConsentDocumentPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes unitários para ConsentDocumentController
 * Testa os endpoints REST e mapeamento de DTOs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Consent Document Controller Tests")
class ConsentDocumentControllerTest {

    private MockMvc mockMvc;
    
    private ObjectMapper objectMapper;

    @Mock
    private ConsentDocumentPort consentDocumentPort;

    @Mock
    private com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.mapper.ConsentDocumentAdapterMapper mapper;

    @InjectMocks
    private ConsentDocumentController consentDocumentController;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(consentDocumentController).build();
    }

    @Test
    @DisplayName("Deve criar documento de consentimento com sucesso")
    void shouldCreateConsentDocumentSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.DRAFT)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.DRAFT)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "terms.pdf",
                "application/pdf",
                "test content".getBytes()
        );

        when(consentDocumentPort.create(any(ConsentDocumentCreateCommandDTO.class))).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(multipart("/api/v1/consent-documents")
                        .file(file)
                        .param("title", "Termos de Uso")
                        .param("description", "Descrição")
                        .param("type", "TERMS_OF_USE")
                        .param("createdBy", "admin@test.com")
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Termos de Uso"))
                .andExpect(jsonPath("$.type").value("TERMS_OF_USE"));
    }

    @Test
    @DisplayName("Deve publicar documento de consentimento com sucesso")
    void shouldPublishConsentDocumentSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        when(consentDocumentPort.publish(documentId, "admin@test.com")).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/consent-documents/{id}/publish", documentId)
                        .param("updatedBy", "admin@test.com")
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("Deve arquivar documento de consentimento com sucesso")
    void shouldArchiveConsentDocumentSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.ARCHIVED)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.ARCHIVED)
                .build();

        when(consentDocumentPort.archive(documentId, "admin@test.com")).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/consent-documents/{id}/archive", documentId)
                        .param("updatedBy", "admin@test.com")
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("Deve buscar documento por ID com sucesso")
    void shouldFindDocumentByIdSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        when(consentDocumentPort.findById(documentId)).thenReturn(viewDTO);
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(get("/api/v1/consent-documents/{id}", documentId)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.title").value("Termos de Uso"));
    }

    @Test
    @DisplayName("Deve buscar documentos por status com sucesso")
    void shouldFindDocumentsByStatusSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        when(consentDocumentPort.findByStatus(ConsentDocumentStatus.PUBLISHED))
                .thenReturn(List.of(viewDTO));
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(get("/api/v1/consent-documents/status/PUBLISHED")
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("Deve buscar documentos por tipo com sucesso")
    void shouldFindDocumentsByTypeSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        ConsentDocumentViewDTO viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        var responseDTO = com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        when(consentDocumentPort.findByType(ConsentType.TERMS_OF_USE))
                .thenReturn(List.of(viewDTO));
        when(mapper.toResponseDTO(any(ConsentDocumentViewDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(get("/api/v1/consent-documents/type/TERMS_OF_USE")
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("TERMS_OF_USE"));
    }

    @Test
    @DisplayName("Deve deletar documento com sucesso")
    void shouldDeleteDocumentSuccessfully() throws Exception {
        // Given
        UUID documentId = UUID.randomUUID();
        doNothing().when(consentDocumentPort).delete(documentId);

        // When & Then
        mockMvc.perform(delete("/api/v1/consent-documents/{id}", documentId)
                        .header("X-Tenant-Id", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isNoContent());
    }
}

