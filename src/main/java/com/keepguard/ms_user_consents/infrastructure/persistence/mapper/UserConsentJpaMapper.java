package com.keepguard.ms_user_consents.infrastructure.persistence.mapper;

import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import com.keepguard.ms_user_consents.infrastructure.persistence.entity.UserConsentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserConsentJpaMapper {
    
    public UserConsent toDomain(UserConsentJpaEntity entity) {
        return UserConsent.fromJpa(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getConsentDocumentId(),
                entity.getVersion(),
                entity.getAcceptedAt(),
                entity.getCreatedAt(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getGeolocation()
        );
    }

    public UserConsentJpaEntity toEntity(UserConsent domain) {
        return UserConsentJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .email(domain.getEmail())
                .consentDocumentId(domain.getConsentDocumentId())
                .version(domain.getVersion())
                .acceptedAt(domain.getAcceptedAt())
                .createdAt(domain.getCreatedAt())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .geolocation(domain.getGeolocation())
                .build();
    }
}

