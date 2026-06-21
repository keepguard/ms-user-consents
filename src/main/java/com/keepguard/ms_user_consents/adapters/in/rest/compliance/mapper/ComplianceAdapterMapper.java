package com.keepguard.ms_user_consents.adapters.in.rest.compliance.mapper;

import com.keepguard.ms_user_consents.adapters.in.rest.compliance.dto.response.ComplianceStatusResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.compliance.dto.response.ConsentStatusDetailDTO;
import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.application.dto.compliance.ConsentStatusDetailViewDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ComplianceAdapterMapper {
    
    public ComplianceStatusResponseDTO toResponseDTO(ComplianceStatusViewDTO viewDTO) {
        List<ConsentStatusDetailDTO> consentDetails = viewDTO.getConsents().stream()
                .map(this::toConsentStatusDetailDTO)
                .toList();

        return ComplianceStatusResponseDTO.builder()
                .userId(viewDTO.getUserId())
                .compliant(viewDTO.isCompliant())
                .consents(consentDetails)
                .missingMandatory(viewDTO.getMissingMandatory())
                .build();
    }

    private ConsentStatusDetailDTO toConsentStatusDetailDTO(ConsentStatusDetailViewDTO viewDTO) {
        return ConsentStatusDetailDTO.builder()
                .documentId(viewDTO.getDocumentId())
                .type(viewDTO.getType())
                .typeDisplayName(viewDTO.getType().getDisplayName())
                .version(viewDTO.getVersion())
                .accepted(viewDTO.isAccepted())
                .acceptedAt(viewDTO.getAcceptedAt())
                .mandatory(viewDTO.isMandatory())
                .build();
    }
}

