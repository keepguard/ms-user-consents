package com.keepguard.ms_user_consents.application.dto.userConsent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentAcceptAllResultDTO {
    
    private List<UserConsentViewDTO> acceptedConsents;
}
