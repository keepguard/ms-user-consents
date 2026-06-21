package com.keepguard.ms_user_consents.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.UserConsentCachePort;
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
public class UserConsentCacheService implements UserConsentCachePort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.redis.ttl.user_consent:3600}")
    private long userConsentTtlSeconds;

    @Value("${cache.redis.prefix.user_consent:user_consent_cache}")
    private String userConsentCachePrefix;

    // Latest by User and Document
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getLatestByUserIdAndConsentDocumentIdFallback")
    @Retry(name = "redisCache")
    public UserConsentViewDTO getLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        try {
            String key = String.format("%s:latest:%s:%s", userConsentCachePrefix, userId, consentDocumentId);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, UserConsentViewDTO.class);
        } catch (Exception e) {
            log.warn("Erro ao buscar consentimento do cache: userId={}, consentDocumentId={}", userId, consentDocumentId, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId, UserConsentViewDTO consent) {
        try {
            String key = String.format("%s:latest:%s:%s", userConsentCachePrefix, userId, consentDocumentId);
            String value = objectMapper.writeValueAsString(consent);
            redisTemplate.opsForValue().set(key, value, userConsentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear consentimento: userId={}, consentDocumentId={}", userId, consentDocumentId, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeLatestByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId) {
        try {
            String key = String.format("%s:latest:%s:%s", userConsentCachePrefix, userId, consentDocumentId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover consentimento do cache: userId={}, consentDocumentId={}", userId, consentDocumentId, e);
        }
    }

    // Has Accepted
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "hasAcceptedFallback")
    @Retry(name = "redisCache")
    public Boolean hasAccepted(UUID userId, UUID consentDocumentId, Integer version) {
        try {
            String key = String.format("%s:accepted:%s:%s:%d", userConsentCachePrefix, userId, consentDocumentId, version);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            log.warn("Erro ao buscar aceite do cache: userId={}, consentDocumentId={}, version={}", userId, consentDocumentId, version, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheHasAccepted(UUID userId, UUID consentDocumentId, Integer version, Boolean accepted) {
        try {
            String key = String.format("%s:accepted:%s:%s:%d", userConsentCachePrefix, userId, consentDocumentId, version);
            redisTemplate.opsForValue().set(key, String.valueOf(accepted), userConsentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear aceite: userId={}, consentDocumentId={}, version={}", userId, consentDocumentId, version, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeHasAccepted(UUID userId, UUID consentDocumentId, Integer version) {
        try {
            String key = String.format("%s:accepted:%s:%s:%d", userConsentCachePrefix, userId, consentDocumentId, version);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover aceite do cache: userId={}, consentDocumentId={}, version={}", userId, consentDocumentId, version, e);
        }
    }

    // By User ID
    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getByUserIdFallback")
    @Retry(name = "redisCache")
    public List<UserConsentViewDTO> getByUserId(UUID userId) {
        try {
            String key = String.format("%s:user:%s", userConsentCachePrefix, userId);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, new TypeReference<List<UserConsentViewDTO>>() {});
        } catch (Exception e) {
            log.warn("Erro ao buscar consentimentos do cache por userId: {}", userId, e);
            return null;
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void cacheByUserId(UUID userId, List<UserConsentViewDTO> consents) {
        try {
            String key = String.format("%s:user:%s", userConsentCachePrefix, userId);
            String value = objectMapper.writeValueAsString(consents);
            redisTemplate.opsForValue().set(key, value, userConsentTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Erro ao cachear consentimentos por userId: {}", userId, e);
        }
    }

    @Override
    @CircuitBreaker(name = "redisCache")
    public void removeByUserId(UUID userId) {
        try {
            String key = String.format("%s:user:%s", userConsentCachePrefix, userId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Erro ao remover consentimentos do cache por userId: {}", userId, e);
        }
    }

    // Clear all
    @Override
    @CircuitBreaker(name = "redisCache")
    public void clearAll() {
        try {
            String pattern = userConsentCachePrefix + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                var deletedCount = redisTemplate.delete(keys);
                log.info("Cache de consentimentos de usuário limpo. {} chave(s) removida(s)", deletedCount);
            }
        } catch (Exception e) {
            log.warn("Erro ao limpar cache de consentimentos de usuário", e);
        }
    }

    // Fallback methods
    private UserConsentViewDTO getLatestByUserIdAndConsentDocumentIdFallback(UUID userId, UUID consentDocumentId, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getLatestByUserIdAndConsentDocumentId");
        return null;
    }

    private Boolean hasAcceptedFallback(UUID userId, UUID consentDocumentId, Integer version, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para hasAccepted");
        return null;
    }

    private List<UserConsentViewDTO> getByUserIdFallback(UUID userId, Exception ex) {
        log.warn("FALLBACK: Redis indisponível para getByUserId");
        return null;
    }
}

