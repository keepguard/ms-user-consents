package com.keepguard.ms_user_consents.infrastructure.persistence.spring;

import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import com.keepguard.ms_user_consents.infrastructure.persistence.entity.ConsentDocumentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentDocumentSpringRepository extends JpaRepository<ConsentDocumentJpaEntity, UUID> {
    
    List<ConsentDocumentJpaEntity> findByStatus(ConsentDocumentStatus status);
    
    List<ConsentDocumentJpaEntity> findByType(ConsentType type);
    
    List<ConsentDocumentJpaEntity> findByTypeAndStatus(ConsentType type, ConsentDocumentStatus status);
    
    @Query("SELECT c FROM ConsentDocumentJpaEntity c WHERE c.type = :type AND c.status = 'PUBLISHED' ORDER BY c.publishedAt DESC")
    List<ConsentDocumentJpaEntity> findByTypeAndStatusOrderByPublishedAtDesc(@Param("type") ConsentType type);
    
    @Query("SELECT c FROM ConsentDocumentJpaEntity c WHERE c.status = 'PUBLISHED' ORDER BY c.type, c.version DESC")
    List<ConsentDocumentJpaEntity> findAllPublished();
}

