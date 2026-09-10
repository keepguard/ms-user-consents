package com.keepguard.ms_user_consents.infrastructure.persistence.mapper;

import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import com.keepguard.ms_user_consents.infrastructure.persistence.entity.UserConsentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserConsentJpaMapper {
    
    public UserConsent toDomain(UserConsentJpaEntity entity) {
        return UserConsent.fromJpa(
                entity.getId(),
                entity.getCompanyId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getConsentDocumentId(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getAcceptedAt(),
                entity.getRevokedAt(),
                entity.getRevocationReason(),
                entity.getCreatedAt(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getGeolocation()
        );
    }

    public UserConsentJpaEntity toEntity(UserConsent domain) {
        return UserConsentJpaEntity.builder()
                .id(domain.getId())
                .companyId(domain.getCompanyId())
                .tenantId(domain.getTenantId())
                .userId(domain.getUserId())
                .email(domain.getEmail())
                .consentDocumentId(domain.getConsentDocumentId())
                .version(domain.getVersion())
                .status(domain.getStatus())
                .acceptedAt(domain.getAcceptedAt())
                .revokedAt(domain.getRevokedAt())
                .revocationReason(domain.getRevocationReason())
                .createdAt(domain.getCreatedAt())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .geolocation(domain.getGeolocation())
                .build();
    }
}

