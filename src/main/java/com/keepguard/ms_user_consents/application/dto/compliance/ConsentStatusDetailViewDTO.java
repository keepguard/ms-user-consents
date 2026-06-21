package com.keepguard.ms_user_consents.application.dto.compliance;

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
public class ConsentStatusDetailViewDTO {
    private UUID documentId;
    private ConsentType type;
    private Integer version;
    private boolean accepted;
    private LocalDateTime acceptedAt;
    private boolean mandatory;
}

