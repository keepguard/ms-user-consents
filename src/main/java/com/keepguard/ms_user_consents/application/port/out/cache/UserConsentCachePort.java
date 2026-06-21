package com.keepguard.ms_user_consents.application.port.out.cache;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;

import java.util.List;
import java.util.UUID;

public interface UserConsentCachePort {

    // Latest by User and Document
    UserConsentViewDTO getLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    void cacheLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId, UserConsentViewDTO consent);
    void removeLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);

    // Has Accepted
    Boolean hasAccepted(UUID userId, UUID consentDocumentId, Integer version);
    void cacheHasAccepted(UUID userId, UUID consentDocumentId, Integer version, Boolean accepted);
    void removeHasAccepted(UUID userId, UUID consentDocumentId, Integer version);

    // By User ID
    List<UserConsentViewDTO> getByUserId(UUID userId);
    void cacheByUserId(UUID userId, List<UserConsentViewDTO> consents);
    void removeByUserId(UUID userId);

    // Clear all
    void clearAll();
}

