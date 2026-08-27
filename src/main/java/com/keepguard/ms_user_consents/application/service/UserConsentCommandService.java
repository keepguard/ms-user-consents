package com.keepguard.ms_user_consents.application.service;

import com.keepguard.lib_common.logging.annotation.LogOperation;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ComplianceCachePort;
import com.keepguard.ms_user_consents.application.port.out.cache.UserConsentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.application.service.exception.AlreadyExistsException;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsentCommandService {

    private final UserConsentRepositoryPort repositoryPort;
    private final ConsentDocumentRepositoryPort consentDocumentRepositoryPort;
    private final UserConsentCachePort userConsentCachePort;
    private final ComplianceCachePort complianceCachePort;
    private final MetricsPort metricsPort;

    @LogOperation(
        operation = "ACCEPT_USER_CONSENT",
        description = "Registrando aceite de consentimento para usuário: {command.userId}",
        audit = true,
        auditAction = "ACCEPT",
        auditEntityType = "USER_CONSENT"
    )
    public UserConsent accept(UserConsentCreateCommandDTO command) {
        log.info("Registrando aceite de consentimento - User: {}, Document: {}, Version: {}",
                command.getUserId(), command.getConsentDocumentId(), command.getVersion());

        // Verifica se já existe aceite para essa versão
        boolean alreadyAccepted = repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(
                command.getUserId(),
                command.getConsentDocumentId(),
                command.getVersion()
        );

        if (alreadyAccepted) {
            log.warn("Usuário {} já aceitou a versão {} do documento {}",
                    command.getUserId(), command.getVersion(), command.getConsentDocumentId());
            metricsPort.incrementCounter("user_consent_business_errors_total",
                Map.of("error_code", "ALREADY_ACCEPTED", "operation", "accept"));
            throw new AlreadyExistsException("Usuário já aceitou esta versão do documento");
        }

        // Cria novo aceite
        UserConsent consent = UserConsent.accept(
                command.getUserId(),
                command.getEmail(),
                command.getConsentDocumentId(),
                command.getVersion(),
                command.getAcceptedAt(),
                command.getIpAddress(),
                command.getUserAgent(),
                command.getGeolocation()
        );

        UserConsent saved = repositoryPort.save(consent);
        
        // Invalida cache relacionado
        invalidateCacheAfterAccept(saved);
        
        // Métricas
        metricsPort.incrementCounter("user_consent_accepted_total",
            Map.of("entity_id", saved.getId().toString(), "user_id", saved.getUserId().toString()));
        
        return saved;
    }

    // Métodos de invalidação de cache
    private void invalidateCacheAfterAccept(UserConsent consent) {
        log.debug("Invalidando cache após aceitar consentimento: userId={}, consentDocumentId={}, version={}",
                consent.getUserId(), consent.getConsentDocumentId(), consent.getVersion());
        
        // Invalida cache de consentimentos do usuário
        userConsentCachePort.removeByUserId(consent.getUserId());
        userConsentCachePort.removeLatestByUserIdAndConsentDocumentId(
                consent.getUserId(), 
                consent.getConsentDocumentId()
        );
        userConsentCachePort.removeHasAccepted(
                consent.getUserId(), 
                consent.getConsentDocumentId(), 
                consent.getVersion()
        );
        
        // Invalida cache de compliance do usuário
        complianceCachePort.removeUserCompliance(consent.getUserId());
    }
    
    @LogOperation(
        operation = "ACCEPT_ALL_USER_CONSENTS",
        description = "Registrando aceite de todos os documentos publicados para usuário: {command.userId}",
        audit = true,
        auditAction = "ACCEPT_ALL",
        auditEntityType = "USER_CONSENT"
    )
    public UserConsentAcceptAllResultDTO acceptAll(UserConsentAcceptAllCommandDTO command) {
        log.info("Registrando aceite de todos os documentos publicados - User: {}", command.getUserId());
        
        // Buscar todos os documentos publicados
        List<ConsentDocument> publishedDocuments = consentDocumentRepositoryPort.findAllPublished();
        
        if (publishedDocuments.isEmpty()) {
            log.info("Nenhum documento publicado encontrado para aceite - User: {}", command.getUserId());
            metricsPort.incrementCounter("user_consent_accept_all_total",
                Map.of("user_id", command.getUserId().toString(), "status", "NO_DOCUMENTS"));
            return UserConsentAcceptAllResultDTO.builder()
                    .acceptedConsents(new ArrayList<>())
                    .build();
        }
        
        List<UserConsent> acceptedConsents = new ArrayList<>();
        int ignoredCount = 0;
        
        // Para cada documento publicado
        for (ConsentDocument document : publishedDocuments) {
            // Verificar se usuário já aceitou essa versão
            boolean alreadyAccepted = repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(
                    command.getUserId(),
                    document.getId(),
                    document.getVersion()
            );
            
            if (alreadyAccepted) {
                log.debug("Usuário {} já aceitou versão {} do documento {} - ignorando",
                        command.getUserId(), document.getVersion(), document.getId());
                ignoredCount++;
                continue;
            }
            
            // Criar aceite para este documento
            UserConsent consent = UserConsent.accept(
                    command.getUserId(),
                    command.getEmail(),
                    document.getId(),
                    document.getVersion(),
                    command.getAcceptedAt(),
                    command.getIpAddress(),
                    command.getUserAgent(),
                    command.getGeolocation()
            );
            
            UserConsent saved = repositoryPort.save(consent);
            acceptedConsents.add(saved);
            
            log.debug("Aceite registrado para documento {} versão {} - User: {}",
                    document.getId(), document.getVersion(), command.getUserId());
        }
        
        // Invalidar caches relacionados após todos os aceites
        invalidateCacheAfterAcceptAll(command.getUserId(), acceptedConsents);
        
        // Métricas
        metricsPort.incrementCounter("user_consent_accept_all_total",
            Map.of("user_id", command.getUserId().toString(), "status", "SUCCESS"));
        metricsPort.incrementCounter("user_consent_accepted_batch_total",
            Map.of("user_id", command.getUserId().toString(), "accepted_count", String.valueOf(acceptedConsents.size())));
        
        if (ignoredCount > 0) {
            metricsPort.incrementCounter("user_consent_ignored_batch_total",
                Map.of("user_id", command.getUserId().toString(), "ignored_count", String.valueOf(ignoredCount)));
        }
        
        log.info("Aceite em lote concluído - User: {}, Aceitos: {}, Ignorados: {}",
                command.getUserId(), acceptedConsents.size(), ignoredCount);
        
        return UserConsentAcceptAllResultDTO.builder()
                .acceptedConsents(acceptedConsents.stream()
                        .map(this::toViewDTO)
                        .toList())
                .build();
    }

    @LogOperation(
        operation = "ACCEPT_BATCH_USER_CONSENTS",
        description = "Registrando aceite seletivo em lote de consentimentos para usuário: {command.userId}",
        audit = true,
        auditAction = "ACCEPT_BATCH",
        auditEntityType = "USER_CONSENT"
    )
    public UserConsentAcceptAllResultDTO acceptBatch(com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptBatchCommandDTO command) {
        log.info("Registrando aceite seletivo em lote - User: {}, Itens recebidos: {}", 
                command.getUserId(), command.getConsents().size());

        List<UserConsent> acceptedConsents = new ArrayList<>();
        int ignoredCount = 0;

        for (var item : command.getConsents()) {
            if (!item.isAccepted()) {
                log.info("Item de consentimento desmarcado/recusado pelo usuário - DocId: {}", item.getDocumentId());
                ignoredCount++;
                continue;
            }

            boolean alreadyAccepted = repositoryPort.existsByUserIdAndConsentDocumentIdAndVersion(
                    command.getUserId(),
                    item.getDocumentId(),
                    item.getVersion()
            );

            if (alreadyAccepted) {
                log.debug("Usuário {} já aceitou versão {} do documento {} - ignorando",
                        command.getUserId(), item.getVersion(), item.getDocumentId());
                ignoredCount++;
                continue;
            }

            UserConsent consent = UserConsent.accept(
                    command.getUserId(),
                    command.getEmail(),
                    item.getDocumentId(),
                    item.getVersion(),
                    command.getAcceptedAt(),
                    command.getIpAddress(),
                    command.getUserAgent(),
                    command.getGeolocation()
            );

            UserConsent saved = repositoryPort.save(consent);
            acceptedConsents.add(saved);

            metricsPort.incrementCounter("user_consent_accepted_total",
                Map.of("user_id", command.getUserId().toString(), 
                       "document_id", item.getDocumentId().toString(),
                       "version", String.valueOf(item.getVersion())));
        }

        invalidateCacheAfterAcceptAll(command.getUserId(), acceptedConsents);

        log.info("Aceite seletivo em lote concluído - User: {}, Aceitos: {}, Ignorados: {}",
                command.getUserId(), acceptedConsents.size(), ignoredCount);

        return UserConsentAcceptAllResultDTO.builder()
                .acceptedConsents(acceptedConsents.stream()
                        .map(this::toViewDTO)
                        .toList())
                .build();
    }
    
    private void invalidateCacheAfterAcceptAll(java.util.UUID userId, List<UserConsent> acceptedConsents) {
        log.debug("Invalidando cache após aceite em lote: userId={}, totalAccepted={}", userId, acceptedConsents.size());
        
        try {
            // Invalida cache de consentimentos do usuário
            userConsentCachePort.removeByUserId(userId);
            
            // Invalida cache específico para cada documento aceito
            for (UserConsent consent : acceptedConsents) {
                userConsentCachePort.removeLatestByUserIdAndConsentDocumentId(
                        consent.getUserId(), 
                        consent.getConsentDocumentId()
                );
                userConsentCachePort.removeHasAccepted(
                        consent.getUserId(), 
                        consent.getConsentDocumentId(), 
                        consent.getVersion()
                );
            }
            
            // Invalida cache de compliance do usuário
            complianceCachePort.removeUserCompliance(userId);
        } catch (Exception e) {
            log.warn("Erro ao invalidar cache após aceite em lote: userId={}", userId, e);
        }
    }
    
    @LogOperation(
        operation = "DELETE_ALL_USER_CONSENTS",
        description = "Deletando todos os consentimentos para usuário: {userId}",
        audit = true,
        auditAction = "DELETE_ALL",
        auditEntityType = "USER_CONSENT"
    )
    @Transactional
    public void deleteAllByUserId(UUID userId) {
        log.info("Deletando todos os consentimentos para usuário: {}", userId);
        
        // Verificar se existem consents para este usuário
        List<UserConsent> existingConsents = repositoryPort.findByUserId(userId);
        if (existingConsents.isEmpty()) {
            log.info("Nenhum consentimento encontrado para usuário: {} - operação idempotente", userId);
            return;
        }
        
        // Deletar todos os consents do usuário
        repositoryPort.deleteAllByUserId(userId);
        
        // Invalidar cache relacionado
        invalidateCacheAfterDeleteAll(userId);
        
        // Métricas
        metricsPort.incrementCounter("user_consent_deleted_all_total",
            Map.of("user_id", userId.toString(), "deleted_count", String.valueOf(existingConsents.size())));
        
        log.info("Todos os consentimentos deletados com sucesso para usuário: {} - Total: {}", userId, existingConsents.size());
    }
    
    private void invalidateCacheAfterDeleteAll(UUID userId) {
        log.debug("Invalidando cache após deletar todos os consents: userId={}", userId);
        
        try {
            // Invalida cache de consentimentos do usuário
            userConsentCachePort.removeByUserId(userId);
            
            // Invalida cache de compliance do usuário
            complianceCachePort.removeUserCompliance(userId);
        } catch (Exception e) {
            log.warn("Erro ao invalidar cache após deletar todos os consents: userId={}", userId, e);
        }
    }
    
    private com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO toViewDTO(UserConsent consent) {
        return com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO.builder()
                .id(consent.getId())
                .userId(consent.getUserId())
                .email(consent.getEmail())
                .consentDocumentId(consent.getConsentDocumentId())
                .version(consent.getVersion())
                .acceptedAt(consent.getAcceptedAt())
                .createdAt(consent.getCreatedAt())
                .ipAddress(consent.getIpAddress())
                .userAgent(consent.getUserAgent())
                .geolocation(consent.getGeolocation())
                .build();
    }
}

