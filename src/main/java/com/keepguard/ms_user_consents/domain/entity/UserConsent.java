package com.keepguard.ms_user_consents.domain.entity;

import com.keepguard.ms_user_consents.domain.enums.UserConsentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserConsent {
    private final UUID id;
    private final UUID companyId;
    private final UUID tenantId;
    private final UUID userId;
    private final String email;                // Email do usuário
    private final UUID consentDocumentId;
    private final Integer version;
    private final UserConsentStatus status;
    private final LocalDateTime acceptedAt;    // Data do aceite (vem da request)
    private final LocalDateTime revokedAt;     // Data da revogação se houver
    private final String revocationReason;     // Motivo da revogação
    private final LocalDateTime createdAt;     // Data de criação no DB
    private final String ipAddress;            // IP do usuário
    private final String userAgent;            // User-Agent do browser
    private final String geolocation;          // Geolocalização (opcional)

    // Factory method para criar novo UserConsent com companyId
    public static UserConsent accept(
            UUID companyId,
            UUID tenantId,
            UUID userId,
            String email,
            UUID consentDocumentId,
            Integer version,
            LocalDateTime acceptedAt,
            String ipAddress,
            String userAgent,
            String geolocation
    ) {
        return new UserConsent(
                UUID.randomUUID(),
                companyId,
                tenantId,
                userId,
                email,
                consentDocumentId,
                version,
                UserConsentStatus.ACCEPTED,
                acceptedAt,
                null,
                null,
                LocalDateTime.now(),
                ipAddress,
                userAgent,
                geolocation
        );
    }

    // Overload sem companyId (para retrocompatibilidade nos testes)
    public static UserConsent accept(
            UUID userId,
            String email,
            UUID consentDocumentId,
            Integer version,
            LocalDateTime acceptedAt,
            String ipAddress,
            String userAgent,
            String geolocation
    ) {
        return accept(null, null, userId, email, consentDocumentId, version, acceptedAt, ipAddress, userAgent, geolocation);
    }

    // Cria nova instância revogada
    public UserConsent revoke(String reason) {
        return new UserConsent(
                this.id,
                this.companyId,
                this.tenantId,
                this.userId,
                this.email,
                this.consentDocumentId,
                this.version,
                UserConsentStatus.REVOKED,
                this.acceptedAt,
                LocalDateTime.now(),
                reason,
                this.createdAt,
                this.ipAddress,
                this.userAgent,
                this.geolocation
        );
    }

    // Factory method para reconstruir do JPA completo
    public static UserConsent fromJpa(
            UUID id,
            UUID companyId,
            UUID tenantId,
            UUID userId,
            String email,
            UUID consentDocumentId,
            Integer version,
            UserConsentStatus status,
            LocalDateTime acceptedAt,
            LocalDateTime revokedAt,
            String revocationReason,
            LocalDateTime createdAt,
            String ipAddress,
            String userAgent,
            String geolocation
    ) {
        return new UserConsent(
                id,
                companyId,
                tenantId,
                userId,
                email,
                consentDocumentId,
                version,
                status != null ? status : UserConsentStatus.ACCEPTED,
                acceptedAt,
                revokedAt,
                revocationReason,
                createdAt,
                ipAddress,
                userAgent,
                geolocation
        );
    }

    // Factory method para reconstruir do JPA (compatibilidade)
    public static UserConsent fromJpa(
            UUID id,
            UUID userId,
            String email,
            UUID consentDocumentId,
            Integer version,
            LocalDateTime acceptedAt,
            LocalDateTime createdAt,
            String ipAddress,
            String userAgent,
            String geolocation
    ) {
        return fromJpa(id, null, null, userId, email, consentDocumentId, version, UserConsentStatus.ACCEPTED, acceptedAt, null, null, createdAt, ipAddress, userAgent, geolocation);
    }
}

