package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.ConsentDocumentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.in.ConsentDocumentPort;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
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
public class ConsentDocumentUseCaseService implements ConsentDocumentPort {

    private final ConsentDocumentCommandService commandService;
    private final ConsentDocumentQueryService queryService;
    private final ConsentDocumentApplicationMapper mapper;

    @Override
    @Transactional
    public ConsentDocumentViewDTO create(ConsentDocumentCreateCommandDTO command) {
        var created = commandService.create(command);
        return mapper.toViewDTO(created);
    }

    @Override
    @Transactional
    public ConsentDocumentViewDTO publish(UUID consentDocumentId, String updatedBy) {
        var published = commandService.publish(consentDocumentId, updatedBy);
        return mapper.toViewDTO(published);
    }

    @Override
    @Transactional
    public ConsentDocumentViewDTO archive(UUID consentDocumentId, String updatedBy) {
        var archived = commandService.archive(consentDocumentId, updatedBy);
        return mapper.toViewDTO(archived);
    }

    @Override
    @Transactional
    public void delete(UUID consentDocumentId) {
        commandService.delete(consentDocumentId);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentDocumentViewDTO findById(UUID id) {
        return mapper.toViewDTO(queryService.findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentDocumentViewDTO> findByStatus(ConsentDocumentStatus status) {
        return queryService.findByStatus(status).stream()
                .map(mapper::toViewDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentDocumentViewDTO> findByType(ConsentType type) {
        return queryService.findByType(type).stream()
                .map(mapper::toViewDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentDocumentViewDTO> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status) {
        return queryService.findByTypeAndStatus(type, status).stream()
                .map(mapper::toViewDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentDocumentViewDTO findLatestPublishedByType(ConsentType type) {
        return mapper.toViewDTO(queryService.findLatestPublishedByType(type));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentDocumentViewDTO> findAllPublished() {
        return queryService.findAllPublished().stream()
                .map(mapper::toViewDTO)
                .toList();
    }
}

