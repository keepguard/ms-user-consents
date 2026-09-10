package com.keepguard.ms_user_consents.infrastructure.persistence.entity;

import com.keepguard.ms_user_consents.domain.enums.UserConsentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

@Entity
@Table(
    name = "user_consents",
    schema = "ms_user_consents",
    indexes = {
        @Index(name = "idx_user_consents_company_user_doc", columnList = "company_id, user_id, consent_document_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentJpaEntity implements Persistable<UUID> {
    
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || createdAt == null;
    }

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "consent_document_id", nullable = false)
    private UUID consentDocumentId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserConsentStatus status = UserConsentStatus.ACCEPTED;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

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

