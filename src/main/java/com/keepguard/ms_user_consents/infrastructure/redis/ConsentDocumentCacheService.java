package com.keepguard.ms_user_consents.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ConsentDocumentCachePort;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentDocumentCacheService implements ConsentDocumentCachePort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.redis.ttl.consent_document:86400}")
    private long consentDocumentTtlSeconds;

    @Value("${cache.redis.prefix.consent_document:consent_doc_cache}")
    private String consentDocumentCachePrefix;

    // By ID
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getByIdFallback")
    @Retry(name = "redisCache")
    public ConsentDocumentViewDTO getById(UUID id) {
        try {
            String key = String.format("%s:id:%s", consentDocumentCachePrefix, id);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ConsentDocumentViewDTO.class);
        } catch (Exception e) {
            log.warn("Erro ao buscar documento do cache por ID: {}", id, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheById(UUID id, ConsentDocumentViewDTO document) {
        try {
            String key = String.format("%s:id:%s", consentDocumentCachePrefix, id);
            String value = objectMapper.writeValueAsString(document);
            redisTemplate.opsForValue().set(key, value, consentDocumentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear documento por ID: {}", id, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeById(UUID id) {
        try {
            String key = String.format("%s:id:%s", consentDocumentCachePrefix, id);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover documento do cache por ID: {}", id, e);
        }
    }

    // Latest Published by Type
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getLatestPublishedByTypeFallback")
    @Retry(name = "redisCache")
    public ConsentDocumentViewDTO getLatestPublishedByType(ConsentType type) {
        try {
            String key = String.format("%s:latest_published:%s", consentDocumentCachePrefix, type);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ConsentDocumentViewDTO.class);
        } catch (Exception e) {
            log.warn("Erro ao buscar documento do cache por tipo: {}", type, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheLatestPublishedByType(ConsentType type, ConsentDocumentViewDTO document) {
        try {
            String key = String.format("%s:latest_published:%s", consentDocumentCachePrefix, type);
            String value = objectMapper.writeValueAsString(document);
            redisTemplate.opsForValue().set(key, value, consentDocumentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear documento por tipo: {}", type, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeLatestPublishedByType(ConsentType type) {
        try {
            String key = String.format("%s:latest_published:%s", consentDocumentCachePrefix, type);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover documento do cache por tipo: {}", type, e);
        }
    }

    // All Published
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getAllPublishedFallback")
    @Retry(name = "redisCache")
    public List<ConsentDocumentViewDTO> getAllPublished() {
        try {
            String key = String.format("%s:all_published", consentDocumentCachePrefix);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, new TypeReference<List<ConsentDocumentViewDTO>>() {});
        } catch (Exception e) {
            log.warn("Erro ao buscar documentos publicados do cache", e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheAllPublished(List<ConsentDocumentViewDTO> documents) {
        try {
            String key = String.format("%s:all_published", consentDocumentCachePrefix);
            String value = objectMapper.writeValueAsString(documents);
            redisTemplate.opsForValue().set(key, value, consentDocumentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear documentos publicados", e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeAllPublished() {
        try {
            String key = String.format("%s:all_published", consentDocumentCachePrefix);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover documentos publicados do cache", e);
        }
    }

    // By Status
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getByStatusFallback")
    @Retry(name = "redisCache")
    public List<ConsentDocumentViewDTO> getByStatus(ConsentDocumentStatus status) {
        try {
            String key = String.format("%s:status:%s", consentDocumentCachePrefix, status);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, new TypeReference<List<ConsentDocumentViewDTO>>() {});
        } catch (Exception e) {
            log.warn("Erro ao buscar documentos do cache por status: {}", status, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheByStatus(ConsentDocumentStatus status, List<ConsentDocumentViewDTO> documents) {
        try {
            String key = String.format("%s:status:%s", consentDocumentCachePrefix, status);
            String value = objectMapper.writeValueAsString(documents);
            redisTemplate.opsForValue().set(key, value, consentDocumentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear documentos por status: {}", status, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeByStatus(ConsentDocumentStatus status) {
        try {
            String key = String.format("%s:status:%s", consentDocumentCachePrefix, status);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover documentos do cache por status: {}", status, e);
        }
    }

    // By Type
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getByTypeFallback")
    @Retry(name = "redisCache")
    public List<ConsentDocumentViewDTO> getByType(ConsentType type) {
        try {
            String key = String.format("%s:type:%s", consentDocumentCachePrefix, type);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, new TypeReference<List<ConsentDocumentViewDTO>>() {});
        } catch (Exception e) {
            log.warn("Erro ao buscar documentos do cache por tipo: {}", type, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheByType(ConsentType type, List<ConsentDocumentViewDTO> documents) {
        try {
            String key = String.format("%s:type:%s", consentDocumentCachePrefix, type);
            String value = objectMapper.writeValueAsString(documents);
            redisTemplate.opsForValue().set(key, value, consentDocumentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear documentos por tipo: {}", type, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeByType(ConsentType type) {
        try {
            String key = String.format("%s:type:%s", consentDocumentCachePrefix, type);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover documentos do cache por tipo: {}", type, e);
        }
    }

    // Clear all
    @Override
    @CircuitBreaker(name = "redisCache")
    public void clearAll() {
        try {
            String pattern = consentDocumentCachePrefix + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                var deletedCount = redisTemplate.delete(keys);
                log.info("Cache de documentos de consentimento limpo. {} chave(s) removida(s)", deletedCount);
            }
        } catch (Exception e) {
            log.warn("Erro ao limpar cache de documentos de consentimento", e);
        }
    }

    // Fallback methods
    private ConsentDocumentViewDTO getByIdFallback(UUID id, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getById");
        return null;
    }

    private ConsentDocumentViewDTO getLatestPublishedByTypeFallback(ConsentType type, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getLatestPublishedByType");
        return null;
    }

    private List<ConsentDocumentViewDTO> getAllPublishedFallback(Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getAllPublished");
        return null;
    }

    private List<ConsentDocumentViewDTO> getByStatusFallback(ConsentDocumentStatus status, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getByStatus");
        return null;
    }

    private List<ConsentDocumentViewDTO> getByTypeFallback(ConsentType type, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getByType");
        return null;
    }
}

