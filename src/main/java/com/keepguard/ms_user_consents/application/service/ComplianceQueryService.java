package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.application.dto.compliance.ConsentStatusDetailViewDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ComplianceCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.UserConsentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceQueryService {

    private final ConsentDocumentRepositoryPort consentDocumentRepository;
    private final UserConsentRepositoryPort userConsentRepository;
    private final ComplianceCachePort cachePort;
    private final MetricsPort metricsPort;

    public ComplianceStatusViewDTO checkUserCompliance(UUID userId) {
        log.info("Verificando compliance do usuário: {}", userId);

        // Tenta buscar do cache
        ComplianceStatusViewDTO cached = cachePort.getUserCompliance(userId);
        if (cached != null) {
            log.debug("Cache HIT para compliance do usuário: {}", userId);
            metricsPort.incrementCounter("compliance_queries_total",
                Map.of("query_type", "CHECK_USER_COMPLIANCE", "status", "CACHE_HIT"));
            return cached;
        }

        // Cache MISS - busca do repositório
        log.debug("Cache MISS para compliance do usuário: {}", userId);

        // Busca todos os documentos publicados
        List<ConsentDocument> publishedDocuments = consentDocumentRepository.findByStatus(ConsentDocumentStatus.PUBLISHED);

        // Busca todos os consentimentos do usuário
        var userConsents = userConsentRepository.findByUserId(userId);

        // Mapeia consentimentos do usuário por documentId
        Map<UUID, Boolean> userConsentsMap = userConsents.stream()
                .collect(Collectors.toMap(
                        consent -> consent.getConsentDocumentId(),
                        consent -> true,
                        (a, b) -> a
                ));

        // Verifica status de cada documento
        List<ConsentStatusDetailViewDTO> consentDetails = publishedDocuments.stream()
                .map(doc -> ConsentStatusDetailViewDTO.builder()
                        .documentId(doc.getId())
                        .type(doc.getType())
                        .version(doc.getVersion())
                        .accepted(userConsentsMap.containsKey(doc.getId()))
                        .acceptedAt(userConsentsMap.containsKey(doc.getId()) ?
                                userConsents.stream()
                                        .filter(c -> c.getConsentDocumentId().equals(doc.getId()))
                                        .findFirst()
                                        .map(c -> c.getAcceptedAt())
                                        .orElse(null) : null)
                        .mandatory(doc.getType().isMandatory())
                        .build())
                .toList();

        // Identifica obrigatórios faltantes
        List<String> missingMandatory = consentDetails.stream()
                .filter(detail -> detail.isMandatory() && !detail.isAccepted())
                .map(detail -> detail.getType().name())
                .toList();

        boolean compliant = missingMandatory.isEmpty();

        ComplianceStatusViewDTO result = ComplianceStatusViewDTO.builder()
                .userId(userId)
                .compliant(compliant)
                .consents(consentDetails)
                .missingMandatory(missingMandatory)
                .build();

        // Cacheia o resultado
        cachePort.cacheUserCompliance(userId, result);
        
        // Métricas
        metricsPort.incrementCounter("compliance_queries_total",
            Map.of("query_type", "CHECK_USER_COMPLIANCE", "status", "SUCCESS"));
        metricsPort.incrementCounter("compliance_status_total",
            Map.of("user_id", userId.toString(), "compliant", String.valueOf(result.isCompliant())));

        return result;
    }

    public boolean hasMandatoryConsents(UUID userId) {
        log.info("Verificando consentimentos obrigatórios do usuário: {}", userId);

        List<ConsentType> mandatoryTypes = Arrays.stream(ConsentType.values())
                .filter(ConsentType::isMandatory)
                .toList();

        for (ConsentType type : mandatoryTypes) {
            Optional<ConsentDocument> latestPublished = consentDocumentRepository.findLatestPublishedByType(type);
            
            if (latestPublished.isEmpty()) {
                log.warn("Nenhum documento publicado encontrado para tipo obrigatório: {}", type);
                continue;
            }

            boolean hasAccepted = userConsentRepository.existsByUserIdAndConsentDocumentIdAndVersion(
                    userId,
                    latestPublished.get().getId(),
                    latestPublished.get().getVersion()
            );

            if (!hasAccepted) {
                log.info("Usuário {} não aceitou consentimento obrigatório: {}", userId, type);
                return false;
            }
        }

        return true;
    }

    public List<ConsentType> listAllConsentTypes() {
        return Arrays.asList(ConsentType.values());
    }

    public List<ConsentType> listMandatoryConsentTypes() {
        return Arrays.stream(ConsentType.values())
                .filter(ConsentType::isMandatory)
                .toList();
    }
}

