package com.keepguard.ms_user_consents.domain.enums;

import lombok.Getter;

@Getter
public enum ConsentType {
    // Essenciais (obrigatórios)
    TERMS_OF_USE("Termos de Uso", "Aceite dos termos de uso da plataforma", ConsentCategory.ESSENTIAL),
    PRIVACY_POLICY("Política de Privacidade", "Aceite da política de privacidade", ConsentCategory.ESSENTIAL),
    LGPD_COMPLIANCE("Conformidade LGPD", "Consentimento para tratamento de dados pessoais", ConsentCategory.ESSENTIAL),
    
    // Funcionais (obrigatórios)
    DATA_PROCESSING("Processamento de Dados", "Autorização para processamento de dados essenciais", ConsentCategory.FUNCTIONAL),
    ESSENTIAL_COOKIES("Cookies Essenciais", "Uso de cookies essenciais para funcionamento", ConsentCategory.FUNCTIONAL),
    
    // Analytics (opcionais)
    ANALYTICS("Analytics", "Consentimento para coleta de dados analíticos", ConsentCategory.ANALYTICS),
    PERFORMANCE_COOKIES("Cookies de Performance", "Uso de cookies para análise de performance", ConsentCategory.ANALYTICS),
    
    // Marketing (opcionais)
    MARKETING_EMAIL("Email Marketing", "Receber emails promocionais e informativos", ConsentCategory.MARKETING),
    MARKETING_SMS("SMS Marketing", "Receber mensagens SMS promocionais", ConsentCategory.MARKETING),
    MARKETING_PUSH("Push Notifications", "Receber notificações push promocionais", ConsentCategory.MARKETING),
    THIRD_PARTY_SHARING("Compartilhamento com Terceiros", "Compartilhamento de dados com parceiros", ConsentCategory.MARKETING),
    PROFILING("Perfilamento", "Autorização para criação de perfil comportamental", ConsentCategory.MARKETING);

    private final String displayName;
    private final String description;
    private final ConsentCategory category;

    ConsentType(String displayName, String description, ConsentCategory category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    public boolean isMandatory() {
        return category.isMandatory();
    }

    public boolean canBeRevoked() {
        return category.isCanBeRevoked();
    }

    public int getDefaultExpirationDays() {
        return category.getDefaultExpirationDays();
    }
}

