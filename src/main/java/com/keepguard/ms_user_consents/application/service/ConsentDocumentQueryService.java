package com.keepguard.ms_user_consents.application.service;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.application.mapper.ConsentDocumentApplicationMapper;
import com.keepguard.ms_user_consents.application.port.out.cache.ConsentDocumentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
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
public class ConsentDocumentQueryService {

    private final ConsentDocumentRepositoryPort repositoryPort;
    private final ConsentDocumentCachePort cachePort;
    private final ConsentDocumentApplicationMapper mapper;
    private final MetricsPort metricsPort;

    public ConsentDocument findById(UUID id) {
        log.debug("Buscando ConsentDocument por ID: {}", id);
        
        // Tenta buscar do cache
        ConsentDocumentViewDTO cached = cachePort.getById(id);
        if (cached != null) {
            log.debug("Cache HIT para ConsentDocument ID: {}", id);
            metricsPort.incrementCounter("consent_document_queries_total",
                Map.of("query_type", "GET_BY_ID", "status", "CACHE_HIT"));
            return mapper.toDomain(cached);
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para ConsentDocument ID: {}", id);
        ConsentDocument document = repositoryPort.findById(id)
                .orElseThrow(() -> {
                    metricsPort.incrementCounter("consent_document_not_found_total",
                        Map.of("entity_id", id.toString(), "operation", "get_by_id"));
                    return new RuntimeException("ConsentDocument não encontrado: " + id);
                });
        
        // Cacheia o resultado
        ConsentDocumentViewDTO viewDTO = mapper.toViewDTO(document);
        cachePort.cacheById(id, viewDTO);
        
        metricsPort.incrementCounter("consent_document_queries_total",
            Map.of("query_type", "GET_BY_ID", "status", "SUCCESS"));
        
        return document;
    }

    public List<ConsentDocument> findByStatus(ConsentDocumentStatus status) {
        log.debug("Buscando ConsentDocuments por status: {}", status);
        
        // Tenta buscar do cache
        List<ConsentDocumentViewDTO> cached = cachePort.getByStatus(status);
        if (cached != null) {
            log.debug("Cache HIT para ConsentDocuments por status: {}", status);
            return cached.stream().map(mapper::toDomain).collect(Collectors.toList());
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para ConsentDocuments por status: {}", status);
        List<ConsentDocument> documents = repositoryPort.findByStatus(status);
        
        // Cacheia o resultado
        List<ConsentDocumentViewDTO> viewDTOs = documents.stream()
                .map(mapper::toViewDTO)
                .collect(Collectors.toList());
        cachePort.cacheByStatus(status, viewDTOs);
        
        return documents;
    }

    public List<ConsentDocument> findByType(ConsentType type) {
        log.debug("Buscando ConsentDocuments por tipo: {}", type);
        
        // Tenta buscar do cache
        List<ConsentDocumentViewDTO> cached = cachePort.getByType(type);
        if (cached != null) {
            log.debug("Cache HIT para ConsentDocuments por tipo: {}", type);
            return cached.stream().map(mapper::toDomain).collect(Collectors.toList());
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para ConsentDocuments por tipo: {}", type);
        List<ConsentDocument> documents = repositoryPort.findByType(type);
        
        // Cacheia o resultado
        List<ConsentDocumentViewDTO> viewDTOs = documents.stream()
                .map(mapper::toViewDTO)
                .collect(Collectors.toList());
        cachePort.cacheByType(type, viewDTOs);
        
        return documents;
    }

    public List<ConsentDocument> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status) {
        log.debug("Buscando ConsentDocuments por tipo: {} e status: {}", type, status);
        return repositoryPort.findByTypeAndStatus(type, status);
    }

    public ConsentDocument findLatestPublishedByType(ConsentType type) {
        log.debug("Buscando última versão publicada do tipo: {}", type);
        
        // Tenta buscar do cache
        ConsentDocumentViewDTO cached = cachePort.getLatestPublishedByType(type);
        if (cached != null) {
            log.debug("Cache HIT para última versão publicada do tipo: {}", type);
            metricsPort.incrementCounter("consent_document_queries_total",
                Map.of("query_type", "GET_LATEST_PUBLISHED_BY_TYPE", "status", "CACHE_HIT"));
            return mapper.toDomain(cached);
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para última versão publicada do tipo: {}", type);
        ConsentDocument document = repositoryPort.findLatestPublishedByType(type)
                .orElseThrow(() -> {
                    metricsPort.incrementCounter("consent_document_not_found_total",
                        Map.of("type", type.name(), "operation", "get_latest_published_by_type"));
                    return new RuntimeException("Nenhum ConsentDocument publicado encontrado para o tipo: " + type);
                });
        
        // Cacheia o resultado
        ConsentDocumentViewDTO viewDTO = mapper.toViewDTO(document);
        cachePort.cacheLatestPublishedByType(type, viewDTO);
        
        metricsPort.incrementCounter("consent_document_queries_total",
            Map.of("query_type", "GET_LATEST_PUBLISHED_BY_TYPE", "status", "SUCCESS"));
        
        return document;
    }

    public List<ConsentDocument> findAllPublished() {
        log.debug("Buscando todos os ConsentDocuments publicados");
        
        // Tenta buscar do cache
        List<ConsentDocumentViewDTO> cached = cachePort.getAllPublished();
        if (cached != null) {
            log.debug("Cache HIT para todos os ConsentDocuments publicados");
            metricsPort.incrementCounter("consent_document_queries_total",
                Map.of("query_type", "GET_ALL_PUBLISHED", "status", "CACHE_HIT"));
            return cached.stream().map(mapper::toDomain).collect(Collectors.toList());
        }
        
        // Cache MISS - busca do repositório
        log.debug("Cache MISS para todos os ConsentDocuments publicados");
        List<ConsentDocument> documents = repositoryPort.findAllPublished();
        
        // Cacheia o resultado
        List<ConsentDocumentViewDTO> viewDTOs = documents.stream()
                .map(mapper::toViewDTO)
                .collect(Collectors.toList());
        cachePort.cacheAllPublished(viewDTOs);
        
        metricsPort.incrementCounter("consent_document_queries_total",
            Map.of("query_type", "GET_ALL_PUBLISHED", "status", "SUCCESS"));
        
        return documents;
    }
}

