package com.keepguard.ms_user_consents.application.port.out.cache;

import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;

import java.util.UUID;

public interface ComplianceCachePort {

    // User Compliance
    ComplianceStatusViewDTO getUserCompliance(UUID userId);
    void cacheUserCompliance(UUID userId, ComplianceStatusViewDTO compliance);
    void removeUserCompliance(UUID userId);

    // User Compliance by Type
    ComplianceStatusViewDTO getUserComplianceByType(UUID userId, ConsentType type);
    void cacheUserComplianceByType(UUID userId, ConsentType type, ComplianceStatusViewDTO compliance);
    void removeUserComplianceByType(UUID userId, ConsentType type);

    // Clear all
    void clearAll();
}

