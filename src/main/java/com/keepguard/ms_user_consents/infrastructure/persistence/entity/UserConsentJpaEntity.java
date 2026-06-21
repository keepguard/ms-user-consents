package com.keepguard.ms_user_consents.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_consents", schema = "ms_user_consents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "consent_document_id", nullable = false)
    private UUID consentDocumentId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "geolocation", length = 100)
    private String geolocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_document_id", insertable = false, updatable = false)
    private ConsentDocumentJpaEntity consentDocument;
}

