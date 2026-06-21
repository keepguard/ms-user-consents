package com.keepguard.ms_user_consents.application.dto.consentDocument;

import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentDocumentCreateCommandDTO {
    private String title;
    private String description;
    private ConsentType type;
    private String createdBy;
    private InputStream fileContent;
    private String fileName;
    private String mimeType;
    private Long fileSize;
}

