package com.keepguard.ms_user_consents.infrastructure.persistence.entity;

import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "consent_documents", schema = "ms_user_consents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentDocumentJpaEntity implements Persistable<UUID> {
    
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || createdAt == null;
    }

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConsentDocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ConsentType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    // Campos S3/MinIO
    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;
}

