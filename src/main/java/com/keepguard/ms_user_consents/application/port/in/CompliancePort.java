package com.keepguard.ms_user_consents.application.port.in;

import com.keepguard.ms_user_consents.application.dto.compliance.ComplianceStatusViewDTO;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;

import java.util.List;
import java.util.UUID;

public interface CompliancePort {
    
    /**
     * Verifica se o usuário está em compliance com todos os consentimentos obrigatórios
     */
    ComplianceStatusViewDTO checkUserCompliance(UUID userId);
    
    /**
     * Verifica se o usuário aceitou todos os consentimentos obrigatórios (essenciais)
     */
    boolean hasMandatoryConsents(UUID userId);
    
    /**
     * Lista todos os tipos de consentimento disponíveis
     */
    List<ConsentType> listAllConsentTypes();
    
    /**
     * Lista apenas os tipos de consentimento obrigatórios
     */
    List<ConsentType> listMandatoryConsentTypes();
}

