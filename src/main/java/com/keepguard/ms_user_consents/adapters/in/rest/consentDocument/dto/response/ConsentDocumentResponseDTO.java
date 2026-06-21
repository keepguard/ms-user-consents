package com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response;

import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentDocumentResponseDTO {
    private UUID id;
    private String title;
    private String description;
    private Integer version;
    private ConsentDocumentStatus status;
    private ConsentType type;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String createdBy;
    private String updatedBy;
    private String s3Url;
    private String contentHash;
    private Long fileSizeBytes;
    private String mimeType;
}

