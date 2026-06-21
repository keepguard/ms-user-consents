package com.keepguard.ms_user_consents.application.mapper;

import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.domain.entity.UserConsent;
import org.springframework.stereotype.Component;

@Component
public class UserConsentApplicationMapper {
    
    public UserConsentViewDTO toViewDTO(UserConsent domain) {
        return UserConsentViewDTO.builder()
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
    
    public UserConsent toDomain(UserConsentViewDTO dto) {
        return UserConsent.fromJpa(
                dto.getId(),
                dto.getUserId(),
                dto.getEmail(),
                dto.getConsentDocumentId(),
                dto.getVersion(),
                dto.getAcceptedAt(),
                dto.getCreatedAt(),
                dto.getIpAddress(),
                dto.getUserAgent(),
                dto.getGeolocation()
        );
    }
}

