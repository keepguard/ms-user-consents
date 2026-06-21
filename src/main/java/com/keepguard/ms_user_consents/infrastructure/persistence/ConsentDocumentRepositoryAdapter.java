package com.keepguard.ms_user_consents.infrastructure.persistence;

import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import com.keepguard.ms_user_consents.infrastructure.persistence.mapper.ConsentDocumentJpaMapper;
import com.keepguard.ms_user_consents.infrastructure.persistence.spring.ConsentDocumentSpringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentDocumentRepositoryAdapter implements ConsentDocumentRepositoryPort {

    private final ConsentDocumentSpringRepository springRepository;
    private final ConsentDocumentJpaMapper mapper;

    @Override
    public ConsentDocument save(ConsentDocument consentDocument) {
        log.debug("Salvando ConsentDocument: {}", consentDocument.getId());
        var entity = mapper.toEntity(consentDocument);
        var saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ConsentDocument> findById(UUID id) {
        return springRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<ConsentDocument> findByStatus(ConsentDocumentStatus status) {
        return springRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ConsentDocument> findByType(ConsentType type) {
        return springRepository.findByType(type).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ConsentDocument> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status) {
        return springRepository.findByTypeAndStatus(type, status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ConsentDocument> findLatestPublishedByType(ConsentType type) {
        var documents = springRepository.findByTypeAndStatusOrderByPublishedAtDesc(type);
        return documents.stream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<ConsentDocument> findAllPublished() {
        return springRepository.findAllPublished().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        log.debug("Deletando ConsentDocument: {}", id);
        springRepository.deleteById(id);
    }
}

