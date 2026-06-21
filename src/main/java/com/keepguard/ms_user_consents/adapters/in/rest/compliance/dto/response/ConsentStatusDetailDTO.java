package com.keepguard.ms_user_consents.adapters.in.rest.compliance.dto.response;

import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentStatusDetailDTO {
    private UUID documentId;
    private ConsentType type;
    private String typeDisplayName;
    private Integer version;
    private boolean accepted;
    private LocalDateTime acceptedAt;
    private boolean mandatory;
}

