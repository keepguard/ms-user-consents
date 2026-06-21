package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.application.port.in.CompliancePort;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceUseCaseService implements CompliancePort {

    private final ComplianceQueryService queryService;

    @Override
    @Transactional(readOnly = true)
    public ComplianceStatusViewDTO checkUserCompliance(UUID userId) {
        return queryService.checkUserCompliance(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasMandatoryConsents(UUID userId) {
        return queryService.hasMandatoryConsents(userId);
    }

    @Override
    public List<ConsentType> listAllConsentTypes() {
        return queryService.listAllConsentTypes();
    }

    @Override
    public List<ConsentType> listMandatoryConsentTypes() {
        return queryService.listMandatoryConsentTypes();
    }
}

