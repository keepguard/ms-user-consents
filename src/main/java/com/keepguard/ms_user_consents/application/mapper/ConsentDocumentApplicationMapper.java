package com.keepguard.ms_user_consents.application.mapper;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import org.springframework.stereotype.Component;

@Component
public class ConsentDocumentApplicationMapper {
    
    public ConsentDocumentViewDTO toViewDTO(ConsentDocument domain) {
        return ConsentDocumentViewDTO.builder()
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
    
    public ConsentDocument toDomain(ConsentDocumentViewDTO dto) {
        return ConsentDocument.fromJpa(
                dto.getId(),
                dto.getTitle(),
                dto.getDescription(),
                dto.getVersion(),
                dto.getStatus(),
                dto.getType(),
                dto.getCreatedAt(),
                dto.getPublishedAt(),
                dto.getCreatedBy(),
                dto.getUpdatedBy(),
                dto.getS3Key(),
                dto.getContentHash(),
                dto.getFileSizeBytes(),
                dto.getMimeType()
        );
    }
}

