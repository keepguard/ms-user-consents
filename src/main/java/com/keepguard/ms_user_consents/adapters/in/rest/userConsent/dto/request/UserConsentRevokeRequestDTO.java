package com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentRevokeRequestDTO {
    
    @NotNull(message = "UserId is required")
    private UUID userId;
    
    @NotNull(message = "ConsentDocumentId is required")
    private UUID consentDocumentId;
    
    private String reason;
}
