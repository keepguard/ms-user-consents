package com.keepguard.ms_user_consents.application.port.out.persistence;

import com.keepguard.ms_user_consents.domain.entity.UserConsent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserConsentRepositoryPort {
    
    UserConsent save(UserConsent userConsent);
    
    Optional<UserConsent> findById(UUID id);
    
    List<UserConsent> findByUserId(UUID userId);
    
    List<UserConsent> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    
    Optional<UserConsent> findLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    
    boolean existsByUserIdAndConsentDocumentIdAndVersion(UUID userId, UUID consentDocumentId, Integer version);
    
    void deleteAllByUserId(UUID userId);
}

