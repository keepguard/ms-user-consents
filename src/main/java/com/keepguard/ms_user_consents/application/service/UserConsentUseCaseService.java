package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.UserConsentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.in.UserConsentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsentUseCaseService implements UserConsentPort {

    private final UserConsentCommandService commandService;
    private final UserConsentQueryService queryService;
    private final UserConsentApplicationMapper mapper;

    @Override
    @Transactional
    public UserConsentViewDTO accept(UserConsentCreateCommandDTO command) {
        var accepted = commandService.accept(command);
        return mapper.toViewDTO(accepted);
    }

    @Override
    @Transactional(readOnly = true)
    public UserConsentViewDTO findById(UUID id) {
        return mapper.toViewDTO(queryService.findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserConsentViewDTO> findByUserId(UUID userId) {
        return queryService.findByUserId(userId).stream()
                .map(mapper::toViewDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserConsentViewDTO> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        return queryService.findByUserIdAndConsentDocumentId(userId, consentDocumentId).stream()
                .map(mapper::toViewDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserConsentViewDTO findLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        return mapper.toViewDTO(queryService.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAccepted(UUID userId, UUID consentDocumentId, Integer version) {
        return queryService.hasAccepted(userId, consentDocumentId, version);
    }

    @Override
    @Transactional
    public UserConsentAcceptAllResultDTO acceptAll(UserConsentAcceptAllCommandDTO command) {
        return commandService.acceptAll(command);
    }

    @Override
    @Transactional
    public void deleteAllByUserId(UUID userId) {
        commandService.deleteAllByUserId(userId);
    }
}

