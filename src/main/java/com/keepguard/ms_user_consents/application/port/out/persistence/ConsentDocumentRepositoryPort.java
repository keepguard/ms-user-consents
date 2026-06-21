package com.keepguard.ms_user_consents.application.port.out.persistence;

import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentDocumentRepositoryPort {
    
    ConsentDocument save(ConsentDocument consentDocument);
    
    Optional<ConsentDocument> findById(UUID id);
    
    List<ConsentDocument> findByStatus(ConsentDocumentStatus status);
    
    List<ConsentDocument> findByType(ConsentType type);
    
    List<ConsentDocument> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status);
    
    Optional<ConsentDocument> findLatestPublishedByType(ConsentType type);
    
    List<ConsentDocument> findAllPublished();
    
    void deleteById(UUID id);
}

