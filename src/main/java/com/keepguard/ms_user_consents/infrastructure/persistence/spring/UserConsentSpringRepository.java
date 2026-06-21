package com.keepguard.ms_user_consents.infrastructure.persistence.spring;

import com.keepguard.ms_user_consents.infrastructure.persistence.entity.UserConsentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConsentSpringRepository extends JpaRepository<UserConsentJpaEntity, UUID> {
    
    List<UserConsentJpaEntity> findByUserId(UUID userId);
    
    List<UserConsentJpaEntity> findByUserIdAndConsentDocumentId(UUID userId, UUID consentDocumentId);
    
    @Query("SELECT uc FROM UserConsentJpaEntity uc WHERE uc.userId = :userId AND uc.consentDocumentId = :consentDocumentId ORDER BY uc.createdAt DESC")
    Optional<UserConsentJpaEntity> findLatestByUserIdAndConsentDocumentId(
            @Param("userId") UUID userId,
            @Param("consentDocumentId") UUID consentDocumentId
    );
    
    boolean existsByUserIdAndConsentDocumentIdAndVersion(UUID userId, UUID consentDocumentId, Integer version);
    
    @Query("DELETE FROM UserConsentJpaEntity uc WHERE uc.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}

