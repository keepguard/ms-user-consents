package com.keepguard.ms_user_consents.domain.entity;

import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConsentDocument {
    private final UUID id;
    private final String title;
    private final String description;
    private final Integer version;
    private final ConsentDocumentStatus status;
    private final ConsentType type;
    private final LocalDateTime createdAt;
    private final LocalDateTime publishedAt;
    private final String createdBy;
    private final String updatedBy;
    
    // Campos S3/MinIO (NOVOS)
    private final String s3Key;           // Caminho no bucket (ex: published/terms_v1.pdf)
    private final String contentHash;     // SHA-256 hash do documento
    private final Long fileSizeBytes;     // Tamanho do arquivo em bytes
    private final String mimeType;        // Tipo MIME (application/pdf, text/html, etc)

    // Factory method para criar novo ConsentDocument (DRAFT)
    public static ConsentDocument create(
            String title,
            String description,
            ConsentType type,
            String createdBy,
            String s3Key,
            String contentHash,
            Long fileSizeBytes,
            String mimeType,
            Integer version
    ) {
        return new ConsentDocument(
                UUID.randomUUID(),
                title,
                description,
                version,
                ConsentDocumentStatus.DRAFT,
                type,
                LocalDateTime.now(),
                null,
                createdBy,
                createdBy,
                s3Key,
                contentHash,
                fileSizeBytes,
                mimeType
        );
    }

    // Factory method para publicar (PUBLISH)
    public ConsentDocument publish(String updatedBy) {
        if (this.status != ConsentDocumentStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT documents can be published");
        }
        
        return new ConsentDocument(
                this.id,
                this.title,
                this.description,
                this.version,
                ConsentDocumentStatus.PUBLISHED,
                this.type,
                this.createdAt,
                LocalDateTime.now(),
                this.createdBy,
                updatedBy,
                this.s3Key,
                this.contentHash,
                this.fileSizeBytes,
                this.mimeType
        );
    }

    // Factory method para publicar com novo s3Key (quando move de drafts/ para published/)
    public ConsentDocument publish(String updatedBy, String newS3Key) {
        if (this.status != ConsentDocumentStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT documents can be published");
        }
        
        return new ConsentDocument(
                this.id,
                this.title,
                this.description,
                this.version,
                ConsentDocumentStatus.PUBLISHED,
                this.type,
                this.createdAt,
                LocalDateTime.now(),
                this.createdBy,
                updatedBy,
                newS3Key,
                this.contentHash,
                this.fileSizeBytes,
                this.mimeType
        );
    }

    // Factory method para arquivar
    public ConsentDocument archive(String updatedBy) {
        if (this.status != ConsentDocumentStatus.PUBLISHED) {
            throw new IllegalStateException("Only PUBLISHED documents can be archived");
        }
        
        return new ConsentDocument(
                this.id,
                this.title,
                this.description,
                this.version,
                ConsentDocumentStatus.ARCHIVED,
                this.type,
                this.createdAt,
                this.publishedAt,
                this.createdBy,
                updatedBy,
                this.s3Key,
                this.contentHash,
                this.fileSizeBytes,
                this.mimeType
        );
    }

    // Factory method para reconstruir do JPA
    public static ConsentDocument fromJpa(
            UUID id,
            String title,
            String description,
            Integer version,
            ConsentDocumentStatus status,
            ConsentType type,
            LocalDateTime createdAt,
            LocalDateTime publishedAt,
            String createdBy,
            String updatedBy,
            String s3Key,
            String contentHash,
            Long fileSizeBytes,
            String mimeType
    ) {
        return new ConsentDocument(
                id,
                title,
                description,
                version,
                status,
                type,
                createdAt,
                publishedAt,
                createdBy,
                updatedBy,
                s3Key,
                contentHash,
                fileSizeBytes,
                mimeType
        );
    }
}

