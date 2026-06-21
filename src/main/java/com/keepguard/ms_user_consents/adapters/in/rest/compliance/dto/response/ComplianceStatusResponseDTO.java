package com.keepguard.ms_user_consents.adapters.in.rest.compliance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceStatusResponseDTO {
    private UUID userId;
    private boolean compliant;
    private List<ConsentStatusDetailDTO> consents;
    private List<String> missingMandatory;
}

