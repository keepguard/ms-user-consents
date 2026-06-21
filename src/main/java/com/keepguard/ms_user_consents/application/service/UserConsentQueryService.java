package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.UserConsentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.out.cache.UserConsentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsentQueryService {

    private final UserConsentRepositoryPort repositoryPort;
    private final UserConsentCachePort cachePort;
    private final UserConsentApplicationMapper mapper;
    private final MetricsPort metricsPort;

    public UserConsent findById(UUID id) {
        log.debug("Buscando UserConsent por ID: {}", id);
        return repositoryPort.findById(id)
                .orElseThrow(() -> new RuntimeException("UserConsent não encontrado: " + id));
    }

    public List<UserConsent> findByUserId(UUID userId) {
        log.debug("Buscando UserConsents por userId: {}", userId);
        
        // Tenta buscar do cache
        List<UserConsentViewDTO> cached = cachePort.getByUserId(userId);
        if (cached != null) {
            log.debug("Cache HIT para UserConsents por userId: {}", userId);
            return cached.stream().map(mapper::toDomain).collect(Collectors.toList());
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para UserConsents por userId: {}", userId);
        List<UserConsent> consents = repositoryPort.findByUserId(userId);
        
        // Cacheia o resultado
        List<UserConsentViewDTO> viewDTOs = consents.stream()
                .map(mapper::toViewDTO)
                .collect(Collectors.toList());
        cachePort.cacheByUserId(userId, viewDTOs);
        
        return consents;
    }

    public List<UserConsent> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        log.debug("Buscando UserConsents por userId: {} e consentDocumentId: {}", userId, consentDocumentId);
        return repositoryPort.findByUserIdAndConsentDocumentId(userId, consentDocumentId);
    }

    public UserConsent findLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        log.debug("Buscando último UserConsent por userId: {} e consentDocumentId: {}", userId, consentDocumentId);
        
        // Tenta buscar do cache
        UserConsentViewDTO cached = cachePort.getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
        if (cached != null) {
            log.debug("Cache HIT para último UserConsent por userId: {} e consentDocumentId: {}", userId, consentDocumentId);
            metricsPort.incrementCounter("user_consent_queries_total",
                Map.of("query_type", "GET_LATEST_BY_USER_AND_DOCUMENT", "status", "CACHE_HIT"));
            return mapper.toDomain(cached);
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para último UserConsent por userId: {} e consentDocumentId: {}", userId, consentDocumentId);
        UserConsent consent = repositoryPort.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId)
                .orElseThrow(() -> {
                    metricsPort.incrementCounter("user_consent_not_found_total",
                        Map.of("user_id", userId.toString(), "operation", "get_latest_by_user_and_document"));
                    return new RuntimeException(
                            "Nenhum consentimento encontrado para userId: " + userId + " e consentDocumentId: " + consentDocumentId
                    );
                });
        
        // Cacheia o resultado
        UserConsentViewDTO viewDTO = mapper.toViewDTO(consent);
        cachePort.cacheLatestByUserIdAndConsentDocumentId(userId, consentDocumentId, viewDTO);
        
        metricsPort.incrementCounter("user_consent_queries_total",
            Map.of("query_type", "GET_LATEST_BY_USER_AND_DOCUMENT", "status", "SUCCESS"));
        
        return consent;
    }

    public boolean hasAccepted(UUID userId, UUID consentDocumentId, Integer version) {
        log.debug("Verificando se usuário {} aceitou versão {} do documento {}",
                userId, version, consentDocumentId);
        
        // Tenta buscar do cache
        Boolean cached = cachePort.hasAccepted(userId, consentDocumentId, version);
        if (cached != null) {
            log.debug("Cache HIT para hasAccepted: userId={}, consentDocumentId={}, version={}", userId, consentDocumentId, version);
            metricsPort.incrementCounter("user_consent_queries_total",
                Map.of("query_type", "HAS_ACCEPTED", "status", "CACHE_HIT"));
            return cached;
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para hasAccepted: userId={}, consentDocumentId={}, version={}", userId, consentDocumentId, version);
        boolean accepted = repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(userId, consentDocumentId, version);
        
        // Cacheia o resultado
        cachePort.cacheHasAccepted(userId, consentDocumentId, version, accepted);
        
        metricsPort.incrementCounter("user_consent_queries_total",
            Map.of("query_type", "HAS_ACCEPTED", "status", "SUCCESS"));
        
        return accepted;
    }
}

