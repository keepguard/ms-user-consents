package com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.request;

import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentDocumentCreateRequestDTO {
    
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @NotNull(message = "Type is required")
    private ConsentType type;
    
    @NotBlank(message = "CreatedBy is required")
    private String createdBy;
}

