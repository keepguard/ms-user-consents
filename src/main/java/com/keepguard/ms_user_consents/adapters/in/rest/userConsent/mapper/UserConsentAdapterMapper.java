package com.keepguard.ms_user_consents.adapters.in.rest.userConsent.mapper;

import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentAcceptAllResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllResultDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import org.springframework.stereotype.Component;

@Component
public class UserConsentAdapterMapper {
    
    public UserConsentResponseDTO toResponseDTO(UserConsentViewDTO viewDTO) {
        return UserConsentResponseDTO.builder()
                .id(viewDTO.getId())
                .userId(viewDTO.getUserId())
                .email(viewDTO.getEmail())
                .consentDocumentId(viewDTO.getConsentDocumentId())
                .version(viewDTO.getVersion())
                .acceptedAt(viewDTO.getAcceptedAt())
                .createdAt(viewDTO.getCreatedAt())
                .ipAddress(viewDTO.getIpAddress())
                .userAgent(viewDTO.getUserAgent())
                .geolocation(viewDTO.getGeolocation())
                .build();
    }
    
    public UserConsentAcceptAllResponseDTO toAcceptAllResponseDTO(UserConsentAcceptAllResultDTO resultDTO) {
        var acceptedConsents = resultDTO.getAcceptedConsents().stream()
                .map(this::toAcceptedConsentItemDTO)
                .toList();
                
        return UserConsentAcceptAllResponseDTO.builder()
                .acceptedConsents(acceptedConsents)
                .totalAccepted(acceptedConsents.size())
                .build();
    }
    
    private UserConsentAcceptAllResponseDTO.AcceptedConsentItemDTO toAcceptedConsentItemDTO(UserConsentViewDTO viewDTO) {
        return UserConsentAcceptAllResponseDTO.AcceptedConsentItemDTO.builder()
                .id(viewDTO.getId())
                .userId(viewDTO.getUserId())
                .email(viewDTO.getEmail())
                .consentDocumentId(viewDTO.getConsentDocumentId())
                .version(viewDTO.getVersion())
                .acceptedAt(viewDTO.getAcceptedAt())
                .createdAt(viewDTO.getCreatedAt())
                .ipAddress(viewDTO.getIpAddress())
                .userAgent(viewDTO.getUserAgent())
                .geolocation(viewDTO.getGeolocation())
                .build();
    }
}

