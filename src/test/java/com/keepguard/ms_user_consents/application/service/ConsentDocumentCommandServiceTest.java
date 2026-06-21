package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ConsentDocumentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.storage.StoragePort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Consent Document Command Service Tests")
class ConsentDocumentCommandServiceTest {

    private ConsentDocumentCommandService service;

    @Mock
    private ConsentDocumentRepositoryPort repositoryPort;

    @Mock
    private StoragePort storagePort;

    @Mock
    private ConsentDocumentCachePort cachePort;

    @Mock
    private MetricsPort metricsPort;

    private ConsentDocumentCreateCommandDTO createCommand;
    private ConsentDocument savedDocument;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        service = new ConsentDocumentCommandService(repositoryPort, storagePort, cachePort, metricsPort);

        documentId = UUID.randomUUID();

        createCommand = ConsentDocumentCreateCommandDTO.builder()
                .title("Termos de Uso")
                .description("Termos de uso da plataforma")
                .type(ConsentType.TERMS_OF_USE)
                .createdBy("admin@test.com")
                .fileContent(new ByteArrayInputStream("test content".getBytes()))
                .fileName("terms.pdf")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .build();

        savedDocument = ConsentDocument.create(
                createCommand.getTitle(),
                createCommand.getDescription(),
                createCommand.getType(),
                createCommand.getCreatedBy(),
                "drafts/terms_v1_uuid.pdf",
                "abc123",
                createCommand.getFileSize(),
                createCommand.getMimeType(),
                1
        );
    }

    @Test
    @DisplayName("Deve criar um ConsentDocument com sucesso")
    void shouldCreateConsentDocumentSuccessfully() throws IOException {
        // Given
        when(repositoryPort.findByType(ConsentType.TERMS_OF_USE)).thenReturn(List.of());
        when(repositoryPort.save(any(ConsentDocument.class))).thenReturn(savedDocument);

        // When
        ConsentDocument result = service.create(createCommand);

        // Then
        assertNotNull(result);
        verify(storagePort, times(1)).uploadFile(any(), anyString(), any(), anyString(), anyLong());
        verify(repositoryPort, times(1)).save(any(ConsentDocument.class));
        verify(cachePort, times(1)).removeByType(ConsentType.TERMS_OF_USE);
        verify(cachePort, times(1)).removeByStatus(ConsentDocumentStatus.DRAFT);
        verify(cachePort, times(1)).removeAllPublished();
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_created_total"), any());
    }

    @Test
    @DisplayName("Deve incrementar versão corretamente")
    void shouldIncrementVersionCorrectly() throws IOException {
        // Given
        ConsentDocument existingDoc = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "drafts/terms_v1.pdf",
                "hash1",
                1024L,
                "application/pdf",
                1
        );

        when(repositoryPort.findByType(ConsentType.TERMS_OF_USE)).thenReturn(List.of(existingDoc));
        when(repositoryPort.save(any(ConsentDocument.class))).thenReturn(savedDocument);

        // When
        service.create(createCommand);

        // Then
        ArgumentCaptor<ConsentDocument> captor = ArgumentCaptor.forClass(ConsentDocument.class);
        verify(repositoryPort).save(captor.capture());
        assertEquals(2, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("Deve publicar um ConsentDocument com sucesso")
    void shouldPublishConsentDocumentSuccessfully() {
        // Given
        ConsentDocument draftDocument = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "drafts/terms_v1.pdf",
                "hash1",
                1024L,
                "application/pdf",
                1
        );

        ConsentDocument publishedDocument = draftDocument.publish("admin@test.com", "published/terms_v1.pdf");

        when(repositoryPort.findById(documentId)).thenReturn(java.util.Optional.of(draftDocument));
        when(storagePort.fileExists(any(), any())).thenReturn(true);
        when(storagePort.downloadFile(any(), any())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(repositoryPort.findByTypeAndStatus(ConsentType.TERMS_OF_USE, ConsentDocumentStatus.PUBLISHED)).thenReturn(List.of());
        when(repositoryPort.save(any(ConsentDocument.class))).thenReturn(publishedDocument);

        // When
        ConsentDocument result = service.publish(documentId, "admin@test.com");

        // Then
        assertNotNull(result);
        verify(storagePort, times(1)).uploadFile(any(), contains("published/"), any(), anyString(), anyLong());
        verify(storagePort, times(1)).deleteFile(any(), contains("drafts/"));
        verify(cachePort, times(1)).removeById(any());
        verify(cachePort, times(1)).removeByType(ConsentType.TERMS_OF_USE);
        verify(cachePort, times(1)).removeByStatus(ConsentDocumentStatus.PUBLISHED);
        verify(cachePort, times(1)).removeAllPublished();
        verify(cachePort, times(1)).removeLatestPublishedByType(ConsentType.TERMS_OF_USE);
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_published_total"), any());
    }

    @Test
    @DisplayName("Deve arquivar um ConsentDocument com sucesso")
    void shouldArchiveConsentDocumentSuccessfully() {
        // Given
        ConsentDocument publishedDocument = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "published/terms_v1.pdf",
                "hash1",
                1024L,
                "application/pdf",
                1
        );
        ConsentDocument published = publishedDocument.publish("admin@test.com", "published/terms_v1.pdf");
        ConsentDocument archived = published.archive("admin@test.com");

        when(repositoryPort.findById(documentId)).thenReturn(java.util.Optional.of(published));
        when(repositoryPort.save(any(ConsentDocument.class))).thenReturn(archived);

        // When
        ConsentDocument result = service.archive(documentId, "admin@test.com");

        // Then
        assertNotNull(result);
        assertEquals(ConsentDocumentStatus.ARCHIVED, result.getStatus());
        verify(repositoryPort, times(1)).save(any(ConsentDocument.class));
        verify(cachePort, times(1)).removeById(any());
        verify(cachePort, times(1)).removeByType(ConsentType.TERMS_OF_USE);
        verify(cachePort, times(1)).removeByStatus(ConsentDocumentStatus.ARCHIVED);
        verify(cachePort, times(1)).removeAllPublished();
        verify(cachePort, times(1)).removeLatestPublishedByType(ConsentType.TERMS_OF_USE);
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_archived_total"), any());
    }

    @Test
    @DisplayName("Deve deletar um ConsentDocument com sucesso")
    void shouldDeleteConsentDocumentSuccessfully() {
        // Given
        ConsentDocument document = ConsentDocument.create(
                "Termos de Uso",
                "Descrição",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "drafts/terms_v1.pdf",
                "hash1",
                1024L,
                "application/pdf",
                1
        );

        when(repositoryPort.findById(documentId)).thenReturn(java.util.Optional.of(document));

        // When
        service.delete(documentId);

        // Then
        verify(storagePort, times(1)).deleteFile(any(), eq("drafts/terms_v1.pdf"));
        verify(repositoryPort, times(1)).deleteById(documentId);
        verify(cachePort, times(1)).removeById(any());
        verify(cachePort, times(1)).removeByType(ConsentType.TERMS_OF_USE);
        verify(cachePort, times(1)).removeByStatus(ConsentDocumentStatus.DRAFT);
        verify(cachePort, times(1)).removeAllPublished();
        verify(metricsPort, times(1)).incrementCounter(eq("consent_document_deleted_total"), any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar criar documento com erro de IO")
    void shouldThrowExceptionWhenCreateFailsWithIOError() {
        // Given
        ConsentDocumentCreateCommandDTO invalidCommand = ConsentDocumentCreateCommandDTO.builder()
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .createdBy("admin@test.com")
                .fileContent(new ByteArrayInputStream("test".getBytes()))
                .fileName("terms.pdf")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .build();

        when(repositoryPort.findByType(ConsentType.TERMS_OF_USE)).thenThrow(new RuntimeException("Erro de conexão"));

        // When & Then
        assertThrows(RuntimeException.class, () -> service.create(invalidCommand));
    }
}

