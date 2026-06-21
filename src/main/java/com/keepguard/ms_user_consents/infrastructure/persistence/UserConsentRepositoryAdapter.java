package com.keepguard.ms_user_consents.infrastructure.persistence;

import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import com.keepguard.ms_user_consents.infrastructure.persistence.mapper.UserConsentJpaMapper;
import com.keepguard.ms_user_consents.infrastructure.persistence.spring.UserConsentSpringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserConsentRepositoryAdapter implements UserConsentRepositoryPort {

    private final UserConsentSpringRepository springRepository;
    private final UserConsentJpaMapper mapper;

    @Override
    public UserConsent save(UserConsent userConsent) {
        log.debug("Salvando UserConsent: {}", userConsent.getId());
        var entity = mapper.toEntity(userConsent);
        var saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserConsent> findById(UUID id) {
        return springRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<UserConsent> findByUserId(UUID userId) {
        return springRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<UserConsent> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        return springRepository.findByUserIdAndConsentDocumentId(userId, consentDocumentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UserConsent> findLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        return springRepository.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndConsentDocumentIdAndVersion(UUID userId, UUID consentDocumentId, Integer version) {
        return springRepository.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, version);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        log.debug("Deletando todos os consents para usuário: {}", userId);
        springRepository.deleteAllByUserId(userId);
    }
}

