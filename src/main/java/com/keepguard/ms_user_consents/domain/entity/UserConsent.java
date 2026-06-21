package com.keepguard.ms_user_consents.domain.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserConsent {
    private final UUID id;
    private final UUID userId;
    private final String email;                // Email do usuário
    private final UUID consentDocumentId;
    private final Integer version;
    private final LocalDateTime acceptedAt;    // Data do aceite (vem da request)
    private final LocalDateTime createdAt;     // Data de criação no DB
    private final String ipAddress;            // IP do usuário
    private final String userAgent;            // User-Agent do browser
    private final String geolocation;          // Geolocalização (opcional)

    // Factory method para criar novo UserConsent
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
        return new UserConsent(
                UUID.randomUUID(),
                userId,
                email,
                consentDocumentId,
                version,
                acceptedAt,
                LocalDateTime.now(), // createdAt = NOW
                ipAddress,
                userAgent,
                geolocation
        );
    }

    // Factory method para reconstruir do JPA
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
        return new UserConsent(
                id,
                userId,
                email,
                consentDocumentId,
                version,
                acceptedAt,
                createdAt,
                ipAddress,
                userAgent,
                geolocation
        );
    }
}

