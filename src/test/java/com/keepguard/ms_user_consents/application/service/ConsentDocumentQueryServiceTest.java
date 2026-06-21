package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.ConsentDocumentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.out.cache.ConsentDocumentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Consent Document Query Service Tests")
class ConsentDocumentQueryServiceTest {

    private ConsentDocumentQueryService service;

    @Mock
    private ConsentDocumentRepositoryPort repositoryPort;

    @Mock
    private ConsentDocumentCachePort cachePort;

    @Mock
    private ConsentDocumentApplicationMapper mapper;

    @Mock
    private MetricsPort metricsPort;

    private ConsentDocument document;
    private ConsentDocumentViewDTO viewDTO;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        service = new ConsentDocumentQueryService(repositoryPort, cachePort, mapper, metricsPort);

        documentId = UUID.randomUUID();

        document = ConsentDocument.create(
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

        viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();
    }

    @Test
    @DisplayName("Deve buscar ConsentDocument por ID do cache")
    void shouldFindByIdFromCache() {
        // Given
        when(cachePort.getById(documentId)).thenReturn(viewDTO);
        when(mapper.toDomain(viewDTO)).thenReturn(document);

        // When
        ConsentDocument result = service.findById(documentId);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getById(documentId);
        verify(repositoryPort, never()).findById(any());
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar ConsentDocument por ID do repositório quando não está em cache")
    void shouldFindByIdFromRepositoryWhenNotInCache() {
        // Given
        when(cachePort.getById(documentId)).thenReturn(null);
        when(repositoryPort.findById(documentId)).thenReturn(Optional.of(document));
        when(mapper.toViewDTO(document)).thenReturn(viewDTO);

        // When
        ConsentDocument result = service.findById(documentId);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getById(documentId);
        verify(repositoryPort, times(1)).findById(documentId);
        verify(cachePort, times(1)).cacheById(documentId, viewDTO);
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando documento não encontrado")
    void shouldThrowExceptionWhenDocumentNotFound() {
        // Given
        when(cachePort.getById(documentId)).thenReturn(null);
        when(repositoryPort.findById(documentId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.findById(documentId));
        assertEquals("ConsentDocument não encontrado: " + documentId, exception.getMessage());
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_not_found_total"), any());
    }

    @Test
    @DisplayName("Deve buscar última versão publicada por tipo do cache")
    void shouldFindLatestPublishedByTypeFromCache() {
        // Given
        when(cachePort.getLatestPublishedByType(ConsentType.TERMS_OF_USE)).thenReturn(viewDTO);
        when(mapper.toDomain(viewDTO)).thenReturn(document);

        // When
        ConsentDocument result = service.findLatestPublishedByType(ConsentType.TERMS_OF_USE);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getLatestPublishedByType(ConsentType.TERMS_OF_USE);
        verify(repositoryPort, never()).findLatestPublishedByType(any());
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar última versão publicada por tipo do repositório")
    void shouldFindLatestPublishedByTypeFromRepository() {
        // Given
        when(cachePort.getLatestPublishedByType(ConsentType.TERMS_OF_USE)).thenReturn(null);
        when(repositoryPort.findLatestPublishedByType(ConsentType.TERMS_OF_USE)).thenReturn(Optional.of(document));
        when(mapper.toViewDTO(document)).thenReturn(viewDTO);

        // When
        ConsentDocument result = service.findLatestPublishedByType(ConsentType.TERMS_OF_USE);

        // Then
        assertNotNull(result);
        verify(cachePort, times(1)).getLatestPublishedByType(ConsentType.TERMS_OF_USE);
        verify(repositoryPort, times(1)).findLatestPublishedByType(ConsentType.TERMS_OF_USE);
        verify(cachePort, times(1)).cacheLatestPublishedByType(ConsentType.TERMS_OF_USE, viewDTO);
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar todos os documentos publicados do cache")
    void shouldFindAllPublishedFromCache() {
        // Given
        List<ConsentDocumentViewDTO> cachedList = List.of(viewDTO);
        when(cachePort.getAllPublished()).thenReturn(cachedList);
        when(mapper.toDomain(viewDTO)).thenReturn(document);

        // When
        List<ConsentDocument> result = service.findAllPublished();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getAllPublished();
        verify(repositoryPort, never()).findAllPublished();
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar todos os documentos publicados do repositório")
    void shouldFindAllPublishedFromRepository() {
        // Given
        when(cachePort.getAllPublished()).thenReturn(null);
        when(repositoryPort.findAllPublished()).thenReturn(List.of(document));
        when(mapper.toViewDTO(document)).thenReturn(viewDTO);

        // When
        List<ConsentDocument> result = service.findAllPublished();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getAllPublished();
        verify(repositoryPort, times(1)).findAllPublished();
        verify(cachePort, times(1)).cacheAllPublished(any());
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_queries_total"), any());
    }

    @Test
    @DisplayName("Deve buscar documentos por status do cache")
    void shouldFindByStatusFromCache() {
        // Given
        List<ConsentDocumentViewDTO> cachedList = List.of(viewDTO);
        when(cachePort.getByStatus(ConsentDocumentStatus.PUBLISHED)).thenReturn(cachedList);
        when(mapper.toDomain(viewDTO)).thenReturn(document);

        // When
        List<ConsentDocument> result = service.findByStatus(ConsentDocumentStatus.PUBLISHED);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getByStatus(ConsentDocumentStatus.PUBLISHED);
        verify(repositoryPort, never()).findByStatus(any());
    }

    @Test
    @DisplayName("Deve buscar documentos por tipo do cache")
    void shouldFindByTypeFromCache() {
        // Given
        List<ConsentDocumentViewDTO> cachedList = List.of(viewDTO);
        when(cachePort.getByType(ConsentType.TERMS_OF_USE)).thenReturn(cachedList);
        when(mapper.toDomain(viewDTO)).thenReturn(document);

        // When
        List<ConsentDocument> result = service.findByType(ConsentType.TERMS_OF_USE);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(cachePort, times(1)).getByType(ConsentType.TERMS_OF_USE);
        verify(repositoryPort, never()).findByType(any());
    }
}

