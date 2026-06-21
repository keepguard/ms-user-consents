package com.keepguard.ms_user_consents.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserConsent Entity Tests")
class UserConsentTest {

    @Test
    @DisplayName("Deve criar um UserConsent ao aceitar")
    void shouldCreateUserConsentOnAccept() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        UUID consentDocumentId = UUID.randomUUID();
        Integer version = 1;
        LocalDateTime acceptedAt = LocalDateTime.now();
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        String geolocation = "São Paulo, BR";

        // When
        UserConsent consent = UserConsent.accept(
                userId, email, consentDocumentId, version, acceptedAt, ipAddress, userAgent, geolocation
        );

        // Then
        assertNotNull(consent);
        assertNotNull(consent.getId());
        assertEquals(userId, consent.getUserId());
        assertEquals(email, consent.getEmail());
        assertEquals(consentDocumentId, consent.getConsentDocumentId());
        assertEquals(version, consent.getVersion());
        assertEquals(acceptedAt, consent.getAcceptedAt());
        assertEquals(ipAddress, consent.getIpAddress());
        assertEquals(userAgent, consent.getUserAgent());
        assertEquals(geolocation, consent.getGeolocation());
        assertNotNull(consent.getCreatedAt());
    }

    @Test
    @DisplayName("Deve criar UserConsent a partir de JPA")
    void shouldCreateUserConsentFromJPA() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        UUID consentDocumentId = UUID.randomUUID();
        Integer version = 1;
        LocalDateTime acceptedAt = LocalDateTime.now();
        LocalDateTime createdAt = LocalDateTime.now();
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        String geolocation = "São Paulo, BR";

        // When
        UserConsent consent = UserConsent.fromJpa(
                id, userId, email, consentDocumentId, version, acceptedAt, createdAt,
                ipAddress, userAgent, geolocation
        );

        // Then
        assertEquals(id, consent.getId());
        assertEquals(userId, consent.getUserId());
        assertEquals(email, consent.getEmail());
        assertEquals(consentDocumentId, consent.getConsentDocumentId());
        assertEquals(version, consent.getVersion());
        assertEquals(acceptedAt, consent.getAcceptedAt());
        assertEquals(createdAt, consent.getCreatedAt());
        assertEquals(ipAddress, consent.getIpAddress());
        assertEquals(userAgent, consent.getUserAgent());
        assertEquals(geolocation, consent.getGeolocation());
    }

    @Test
    @DisplayName("Deve criar UserConsent com valores nulos opcionais")
    void shouldCreateUserConsentWithNullOptionalValues() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        UUID consentDocumentId = UUID.randomUUID();
        Integer version = 1;
        LocalDateTime acceptedAt = LocalDateTime.now();

        // When
        UserConsent consent = UserConsent.accept(
                userId, email, consentDocumentId, version, acceptedAt, null, null, null
        );

        // Then
        assertNotNull(consent);
        assertEquals(email, consent.getEmail());
        assertNull(consent.getIpAddress());
        assertNull(consent.getUserAgent());
        assertNull(consent.getGeolocation());
    }

    @Test
    @DisplayName("Deve retornar valores corretos de auditoria")
    void shouldReturnCorrectAuditValues() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";
        UUID consentDocumentId = UUID.randomUUID();
        Integer version = 1;
        LocalDateTime acceptedAt = LocalDateTime.now();
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        String geolocation = "São Paulo, BR";

        // When
        UserConsent consent = UserConsent.accept(
                userId, email, consentDocumentId, version, acceptedAt, ipAddress, userAgent, geolocation
        );

        // Then
        assertEquals(email, consent.getEmail());
        assertEquals(ipAddress, consent.getIpAddress());
        assertEquals(userAgent, consent.getUserAgent());
        assertEquals(geolocation, consent.getGeolocation());
        assertNotNull(consent.getCreatedAt());
    }
}

