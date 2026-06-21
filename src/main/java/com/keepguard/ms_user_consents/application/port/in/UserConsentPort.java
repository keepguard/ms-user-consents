package com.keepguard.ms_user_consents.application.port.in;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;

import java.util.List;
import java.util.UUID;

public interface UserConsentPort {
    
    // Commands
    UserConsentViewDTO accept(UserConsentCreateCommandDTO command);
    UserConsentAcceptAllResultDTO acceptAll(UserConsentAcceptAllCommandDTO command);
    void deleteAllByUserId(UUID userId);
    
    // Queries
    UserConsentViewDTO findById(UUID id);
    List<UserConsentViewDTO> findByUserId(UUID userId);
    List<UserConsentViewDTO> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    UserConsentViewDTO findLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    boolean hasAccepted(UUID userId, UUID consentDocumentId, Integer version);
}

