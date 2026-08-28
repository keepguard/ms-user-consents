package com.keepguard.ms_user_consents.application.dto.manifest;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.keepguard.ms_user_consents.domain.enums.ConsentCategory;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermsManifestDTO {

    private UUID companyId;
    private String version;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime effectiveAt;

    private Integer gracePeriodDays;
    private List<TermsManifestDocumentDTO> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermsManifestDocumentDTO {
        private UUID id;
        private ConsentType type;
        private ConsentCategory category;
        private String title;
        private Integer version;
        private boolean mandatory;
        private String contentHash;
        private String url;
    }
}
