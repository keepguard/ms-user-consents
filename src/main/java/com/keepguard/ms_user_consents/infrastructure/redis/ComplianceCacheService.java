package com.keepguard.ms_user_consents.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ComplianceCachePort;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceCacheService implements ComplianceCachePort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.redis.ttl.compliance:3600}")
    private long complianceTtlSeconds;

    @Value("${cache.redis.prefix.compliance:compliance_cache}")
    private String complianceCachePrefix;

    // User Compliance
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getUserComplianceFallback")
    @Retry(name = "redisCache")
    public ComplianceStatusViewDTO getUserCompliance(UUID userId) {
        try {
            String key = userKey(userId);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ComplianceStatusViewDTO.class);
        } catch (Exception e) {
            log.warn("Erro ao buscar compliance do cache para userId: {}", userId, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheUserCompliance(UUID userId, ComplianceStatusViewDTO compliance) {
        try {
            String key = userKey(userId);
            String value = objectMapper.writeValueAsString(compliance);
            redisTemplate.opsForValue().set(key, value, complianceTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear compliance para userId: {}", userId, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeUserCompliance(UUID userId) {
        try {
            String key = userKey(userId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover compliance do cache para userId: {}", userId, e);
        }
    }

    // User Compliance by Type
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getUserComplianceByTypeFallback")
    @Retry(name = "redisCache")
    public ComplianceStatusViewDTO getUserComplianceByType(UUID userId, ConsentType type) {
        try {
            String key = userTypeKey(userId, type);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, ComplianceStatusViewDTO.class);
        } catch (Exception e) {
            log.warn("Erro ao buscar compliance do cache para userId: {}, type: {}", userId, type, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheUserComplianceByType(UUID userId, ConsentType type, ComplianceStatusViewDTO compliance) {
        try {
            String key = userTypeKey(userId, type);
            String value = objectMapper.writeValueAsString(compliance);
            redisTemplate.opsForValue().set(key, value, complianceTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear compliance para userId: {}, type: {}", userId, type, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeUserComplianceByType(UUID userId, ConsentType type) {
        try {
            String key = userTypeKey(userId, type);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover compliance do cache para userId: {}, type: {}", userId, type, e);
        }
    }

    // Clear all
    @Override
    @CircuitBreaker(name = "redisCache")
    public void clearAll() {
        try {
            String pattern = basePrefix() + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                var deletedCount = redisTemplate.delete(keys);
                log.info("Cache de compliance limpo. {} chave(s) removida(s)", deletedCount);
            }
        } catch (Exception e) {
            log.warn("Erro ao limpar cache de compliance", e);
        }
    }

    // Fallback methods
    private ComplianceStatusViewDTO getUserComplianceFallback(UUID userId, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getUserCompliance");
        return null;
    }

    private ComplianceStatusViewDTO getUserComplianceByTypeFallback(UUID userId, ConsentType type, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getUserComplianceByType");
        return null;
    }

    private String basePrefix() {
        if (complianceCachePrefix == null || complianceCachePrefix.isBlank()) {
            return "compliance_cache";
        }
        return complianceCachePrefix.replaceAll(":+$", "");
    }

    private String userKey(UUID userId) {
        return basePrefix() + ":user:" + userId;
    }

    private String userTypeKey(UUID userId, ConsentType type) {
        return basePrefix() + ":user:" + userId + ":type:" + enumName(type);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase();
    }
}

