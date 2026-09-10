package com.keepguard.ms_user_consents.application.dto.userConsent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentRevokeCommandDTO {
    private UUID companyId;
    private UUID tenantId;
    private UUID userId;
    private UUID consentDocumentId;
    private String reason;
}
