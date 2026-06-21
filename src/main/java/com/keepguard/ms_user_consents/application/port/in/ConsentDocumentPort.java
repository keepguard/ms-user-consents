package com.keepguard.ms_user_consents.application.port.in;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;

import java.util.List;
import java.util.UUID;

public interface ConsentDocumentPort {
    
    // Commands
    ConsentDocumentViewDTO create(ConsentDocumentCreateCommandDTO command);
    ConsentDocumentViewDTO publish(UUID consentDocumentId, String updatedBy);
    ConsentDocumentViewDTO archive(UUID consentDocumentId, String updatedBy);
    void delete(UUID consentDocumentId);
    
    // Queries
    ConsentDocumentViewDTO findById(UUID id);
    List<ConsentDocumentViewDTO> findByStatus(ConsentDocumentStatus status);
    List<ConsentDocumentViewDTO> findByType(ConsentType type);
    List<ConsentDocumentViewDTO> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status);
    ConsentDocumentViewDTO findLatestPublishedByType(ConsentType type);
    List<ConsentDocumentViewDTO> findAllPublished();
}

