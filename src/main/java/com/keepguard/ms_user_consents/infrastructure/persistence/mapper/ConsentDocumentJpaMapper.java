package com.keepguard.ms_user_consents.infrastructure.persistence.mapper;

import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.infrastructure.persistence.entity.ConsentDocumentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ConsentDocumentJpaMapper {
    
    public ConsentDocument toDomain(ConsentDocumentJpaEntity entity) {
        return ConsentDocument.fromJpa(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getType(),
                entity.getCreatedAt(),
                entity.getPublishedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getS3Key(),
                entity.getContentHash(),
                entity.getFileSizeBytes(),
                entity.getMimeType()
        );
    }

    public ConsentDocumentJpaEntity toEntity(ConsentDocument domain) {
        return ConsentDocumentJpaEntity.builder()
                .id(domain.getId())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .version(domain.getVersion())
                .status(domain.getStatus())
                .type(domain.getType())
                .createdAt(domain.getCreatedAt())
                .publishedAt(domain.getPublishedAt())
                .createdBy(domain.getCreatedBy())
                .updatedBy(domain.getUpdatedBy())
                .s3Key(domain.getS3Key())
                .contentHash(domain.getContentHash())
                .fileSizeBytes(domain.getFileSizeBytes())
                .mimeType(domain.getMimeType())
                .build();
    }
}

