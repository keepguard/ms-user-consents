package com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.mapper;

import com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsentDocumentAdapterMapper {

    @Value("${storage.minio.endpoint}")
    private String minioEndpoint;

    @Value("${storage.minio.bucket.consents}")
    private String consentsBucket;

    public ConsentDocumentResponseDTO toResponseDTO(ConsentDocumentViewDTO viewDTO) {
        String s3Url = viewDTO.getS3Key() != null 
                ? String.format("%s/%s/%s", minioEndpoint, consentsBucket, viewDTO.getS3Key())
                : null;

        return ConsentDocumentResponseDTO.builder()
                .id(viewDTO.getId())
                .title(viewDTO.getTitle())
                .description(viewDTO.getDescription())
                .version(viewDTO.getVersion())
                .status(viewDTO.getStatus())
                .type(viewDTO.getType())
                .createdAt(viewDTO.getCreatedAt())
                .publishedAt(viewDTO.getPublishedAt())
                .createdBy(viewDTO.getCreatedBy())
                .updatedBy(viewDTO.getUpdatedBy())
                .s3Url(s3Url)
                .contentHash(viewDTO.getContentHash())
                .fileSizeBytes(viewDTO.getFileSizeBytes())
                .mimeType(viewDTO.getMimeType())
                .build();
    }
}

