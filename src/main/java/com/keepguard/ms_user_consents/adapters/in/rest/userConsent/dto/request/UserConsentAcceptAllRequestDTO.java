package com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
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
public class UserConsentAcceptAllRequestDTO {
    
    @NotNull(message = "UserId is required")
    private UUID userId;
    
    @NotNull(message = "Email is required")
    private String email;
    
    @NotNull(message = "AcceptedAt is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime acceptedAt;
    
    private String geolocation;
}
