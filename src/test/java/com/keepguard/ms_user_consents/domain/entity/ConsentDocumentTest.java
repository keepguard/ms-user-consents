package com.keepguard.ms_user_consents.domain.entity;

import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsentDocument Entity Tests")
class ConsentDocumentTest {

    @Test
    @DisplayName("Deve criar um ConsentDocument em DRAFT")
    void shouldCreateConsentDocumentInDraftStatus() {
        // Given
        String title = "Termos de Uso";
        String description = "Termos de uso da plataforma";
        ConsentType type = ConsentType.TERMS_OF_USE;
        String createdBy = "admin@test.com";
        String s3Key = "drafts/terms_v1_uuid.pdf";
        String contentHash = "abc123";
        Long fileSize = 1024L;
        String mimeType = "application/pdf";
        Integer version = 1;

        // When
        ConsentDocument document = ConsentDocument.create(
                title, description, type, createdBy, s3Key, contentHash, fileSize, mimeType, version
        );

        // Then
        assertNotNull(document);
        assertNotNull(document.getId());
        assertEquals(title, document.getTitle());
        assertEquals(description, document.getDescription());
        assertEquals(type, document.getType());
        assertEquals(version, document.getVersion());
        assertEquals(ConsentDocumentStatus.DRAFT, document.getStatus());
        assertEquals(createdBy, document.getCreatedBy());
        assertEquals(s3Key, document.getS3Key());
        assertEquals(contentHash, document.getContentHash());
        assertEquals(fileSize, document.getFileSizeBytes());
        assertEquals(mimeType, document.getMimeType());
        assertNotNull(document.getCreatedAt());
        assertNull(document.getPublishedAt());
        assertEquals(createdBy, document.getUpdatedBy()); // updatedBy é definido como createdBy no factory method
    }

    @Test
    @DisplayName("Deve publicar um ConsentDocument")
    void shouldPublishConsentDocument() {
        // Given
        ConsentDocument document = createDraftDocument();
        String updatedBy = "admin@test.com";
        String newS3Key = "published/terms_v1_uuid.pdf";

        // When
        ConsentDocument published = document.publish(updatedBy, newS3Key);

        // Then
        assertEquals(ConsentDocumentStatus.PUBLISHED, published.getStatus());
        assertEquals(updatedBy, published.getUpdatedBy());
        assertEquals(newS3Key, published.getS3Key());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    @DisplayName("Deve arquivar um ConsentDocument")
    void shouldArchiveConsentDocument() {
        // Given
        ConsentDocument document = createPublishedDocument();
        String updatedBy = "admin@test.com";

        // When
        ConsentDocument archived = document.archive(updatedBy);

        // Then
        assertEquals(ConsentDocumentStatus.ARCHIVED, archived.getStatus());
        assertEquals(updatedBy, archived.getUpdatedBy());
    }

    @Test
    @DisplayName("Deve criar ConsentDocument a partir de JPA")
    void shouldCreateConsentDocumentFromJPA() {
        // Given
        UUID id = UUID.randomUUID();
        String title = "Termos de Uso";
        String description = "Termos de uso da plataforma";
        Integer version = 1;
        ConsentDocumentStatus status = ConsentDocumentStatus.PUBLISHED;
        ConsentType type = ConsentType.TERMS_OF_USE;
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime publishedAt = LocalDateTime.now();
        String createdBy = "admin@test.com";
        String updatedBy = "admin@test.com";
        String s3Key = "published/terms_v1_uuid.pdf";
        String contentHash = "abc123";
        Long fileSize = 1024L;
        String mimeType = "application/pdf";

        // When
        ConsentDocument document = ConsentDocument.fromJpa(
                id, title, description, version, status, type, createdAt, publishedAt,
                createdBy, updatedBy, s3Key, contentHash, fileSize, mimeType
        );

        // Then
        assertEquals(id, document.getId());
        assertEquals(title, document.getTitle());
        assertEquals(description, document.getDescription());
        assertEquals(version, document.getVersion());
        assertEquals(status, document.getStatus());
        assertEquals(type, document.getType());
        assertEquals(createdAt, document.getCreatedAt());
        assertEquals(publishedAt, document.getPublishedAt());
        assertEquals(createdBy, document.getCreatedBy());
        assertEquals(updatedBy, document.getUpdatedBy());
        assertEquals(s3Key, document.getS3Key());
        assertEquals(contentHash, document.getContentHash());
        assertEquals(fileSize, document.getFileSizeBytes());
        assertEquals(mimeType, document.getMimeType());
    }

    @Test
    @DisplayName("Deve retornar true se documento está publicado")
    void shouldReturnTrueIfDocumentIsPublished() {
        // Given
        ConsentDocument document = createPublishedDocument();

        // When
        boolean isPublished = document.getStatus() == ConsentDocumentStatus.PUBLISHED;

        // Then
        assertTrue(isPublished);
    }

    @Test
    @DisplayName("Deve retornar true se documento está arquivado")
    void shouldReturnTrueIfDocumentIsArchived() {
        // Given
        ConsentDocument draftDocument = createDraftDocument();
        ConsentDocument publishedDocument = draftDocument.publish("admin@test.com");
        ConsentDocument archived = publishedDocument.archive("admin@test.com");

        // When
        boolean isArchived = archived.getStatus() == ConsentDocumentStatus.ARCHIVED;

        // Then
        assertTrue(isArchived);
    }

    // Helper methods
    private ConsentDocument createDraftDocument() {
        return ConsentDocument.create(
                "Termos de Uso",
                "Termos de uso da plataforma",
                ConsentType.TERMS_OF_USE,
                "admin@test.com",
                "drafts/terms_v1_uuid.pdf",
                "abc123",
                1024L,
                "application/pdf",
                1
        );
    }

    private ConsentDocument createPublishedDocument() {
        ConsentDocument document = createDraftDocument();
        return document.publish("admin@test.com", "published/terms_v1_uuid.pdf");
    }
}

