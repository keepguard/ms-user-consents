package com.keepguard.ms_user_consents.application.port.out.cache;

import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;

import java.util.List;
import java.util.UUID;

public interface ConsentDocumentCachePort {

    // By ID
    ConsentDocumentViewDTO getById(UUID id);
    void cacheById(UUID id, ConsentDocumentViewDTO document);
    void removeById(UUID id);

    // Latest Published by Type
    ConsentDocumentViewDTO getLatestPublishedByType(ConsentType type);
    void cacheLatestPublishedByType(ConsentType type, ConsentDocumentViewDTO document);
    void removeLatestPublishedByType(ConsentType type);

    // All Published
    List<ConsentDocumentViewDTO> getAllPublished();
    void cacheAllPublished(List<ConsentDocumentViewDTO> documents);
    void removeAllPublished();

    // By Status
    List<ConsentDocumentViewDTO> getByStatus(ConsentDocumentStatus status);
    void cacheByStatus(ConsentDocumentStatus status, List<ConsentDocumentViewDTO> documents);
    void removeByStatus(ConsentDocumentStatus status);

    // By Type
    List<ConsentDocumentViewDTO> getByType(ConsentType type);
    void cacheByType(ConsentType type, List<ConsentDocumentViewDTO> documents);
    void removeByType(ConsentType type);

    // Clear all
    void clearAll();
}

