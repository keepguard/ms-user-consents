package com.keepguard.ms_user_consents.application.dto.userConsent;

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
public class UserConsentAcceptBatchCommandDTO {

    private UUID userId;
    private String email;
    private LocalDateTime acceptedAt;
    private String ipAddress;
    private String userAgent;
    private String geolocation;
    private List<ConsentItemCommandDTO> consents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentItemCommandDTO {
        private UUID documentId;
        private Integer version;
        private boolean accepted;
        private String contentHash;
    }
}
