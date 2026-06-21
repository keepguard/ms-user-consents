package com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response;

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
public class UserConsentResponseDTO {
    private UUID id;
    private UUID userId;
    private String email;
    private UUID consentDocumentId;
    private Integer version;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
    private String ipAddress;
    private String userAgent;
    private String geolocation;
}

