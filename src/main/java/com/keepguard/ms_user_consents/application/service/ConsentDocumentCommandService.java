package com.keepguard.ms_user_consents.application.service;

import com.keepguard.lib_common.logging.annotation.LogOperation;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.port.out.cache.ConsentDocumentCachePort;
import com.keepguard.ms_user_consents.application.port.out.metrics.MetricsPort;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.storage.StoragePort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentDocumentCommandService {

    private final ConsentDocumentRepositoryPort repositoryPort;
    private final StoragePort storagePort;
    private final ConsentDocumentCachePort cachePort;
    private final MetricsPort metricsPort;

    @Value("${storage.minio.bucket.consents}")
    private String consentsBucket;

    @LogOperation(
        operation = "CREATE_CONSENT_DOCUMENT",
        description = "Criando novo documento de consentimento: {command.title}",
        audit = true,
        auditAction = "CREATE",
        auditEntityType = "CONSENT_DOCUMENT"
    )
    public ConsentDocument create(ConsentDocumentCreateCommandDTO command) {
        log.info("Criando novo ConsentDocument - Type: {}, Title: {}", command.getType(), command.getTitle());

        try {
            // Busca a última versão do mesmo tipo para incrementar
            Integer nextVersion = getNextVersion(command.getType());
            log.info("Nova versão para tipo {}: {}", command.getType(), nextVersion);
            
            // Lê o conteúdo do InputStream em um byte array
            byte[] fileBytes = command.getFileContent().readAllBytes();
            
            // Calcula hash do conteúdo
            String contentHash = DigestUtils.sha256Hex(fileBytes);
            
            // Gera chave S3 (drafts/terms_v{version}_uuid.pdf)
            String s3Key = String.format("drafts/%s_v%d_%s.%s", 
                    command.getType().name().toLowerCase(),
                    nextVersion,
                    UUID.randomUUID(),
                    getFileExtension(command.getFileName())
            );

            // Upload para MinIO usando ByteArrayInputStream
            storagePort.uploadFile(
                    consentsBucket,
                    s3Key,
                    new ByteArrayInputStream(fileBytes),
                    command.getMimeType(),
                    command.getFileSize()
            );

            // Cria entidade de domínio com a versão correta
            ConsentDocument document = ConsentDocument.create(
                    command.getTitle(),
                    command.getDescription(),
                    command.getType(),
                    command.getCreatedBy(),
                    s3Key,
                    contentHash,
                    command.getFileSize(),
                    command.getMimeType(),
                    nextVersion
            );

            // Persiste
            ConsentDocument saved = repositoryPort.save(document);
            
            // Invalida cache relacionado
            invalidateCacheAfterCreate(saved);
            
            // Métricas
            metricsPort.incrementCounter("consent_document_created_total",
                Map.of("entity_id", saved.getId().toString(), "type", saved.getType().name()));
            
            return saved;

        } catch (IOException e) {
            log.error("Erro ao ler conteúdo do arquivo", e);
            throw new RuntimeException("Falha ao ler conteúdo do arquivo: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro ao criar ConsentDocument", e);
            throw new RuntimeException("Falha ao criar ConsentDocument: " + e.getMessage(), e);
        }
    }
    
    private Integer getNextVersion(ConsentType type) {
        // Busca a última versão publicada ou arquivada do mesmo tipo
        List<ConsentDocument> existingDocs = repositoryPort.findByType(type);
        
        if (existingDocs.isEmpty()) {
            return 1; // Primeira versão
        }
        
        // Encontra a maior versão
        Integer maxVersion = existingDocs.stream()
                .map(ConsentDocument::getVersion)
                .max(Integer::compareTo)
                .orElse(0);
        
        return maxVersion + 1;
    }

    @LogOperation(
        operation = "PUBLISH_CONSENT_DOCUMENT",
        description = "Publicando documento de consentimento: {consentDocumentId}",
        audit = true,
        auditAction = "PUBLISH",
        auditEntityType = "CONSENT_DOCUMENT"
    )
    public ConsentDocument publish(UUID consentDocumentId, String updatedBy) {
        log.info("Publicando ConsentDocument - ID: {}, UpdatedBy: {}", consentDocumentId, updatedBy);

        ConsentDocument document = repositoryPort.findById(consentDocumentId)
                .orElseThrow(() -> new RuntimeException("ConsentDocument não encontrado: " + consentDocumentId));

        // Verifica se o arquivo existe no S3
        if (!storagePort.fileExists(consentsBucket, document.getS3Key())) {
            throw new RuntimeException("Arquivo não encontrado no storage: " + document.getS3Key());
        }

        // ARQUIVA AUTOMATICAMENTE o documento antigo do mesmo tipo (se existir)
        List<ConsentDocument> existingPublished = repositoryPort.findByTypeAndStatus(
                document.getType(), 
                ConsentDocumentStatus.PUBLISHED
        );
        
        if (!existingPublished.isEmpty()) {
            log.info("Encontrado {} documento(s) publicado(s) do tipo {}. Arquivando...", 
                    existingPublished.size(), document.getType());
            
            for (ConsentDocument oldDoc : existingPublished) {
                // Move o arquivo antigo de published/ para archived/
                String archivedS3Key = oldDoc.getS3Key().replace("published/", "archived/");
                log.info("Movendo documento antigo de {} para {}", oldDoc.getS3Key(), archivedS3Key);
                
                try {
                    // Baixa o arquivo da pasta published
                    InputStream fileStream = storagePort.downloadFile(consentsBucket, oldDoc.getS3Key());
                    
                    // Faz upload para a pasta archived
                    storagePort.uploadFile(
                            consentsBucket,
                            archivedS3Key,
                            fileStream,
                            oldDoc.getMimeType(),
                            oldDoc.getFileSizeBytes()
                    );
                    
                    // Deleta o arquivo da pasta published
                    storagePort.deleteFile(consentsBucket, oldDoc.getS3Key());
                    
                    log.info("Documento antigo movido com sucesso para {}", archivedS3Key);
                    
                } catch (Exception e) {
                    log.error("Erro ao mover documento antigo para pasta archived", e);
                    throw new RuntimeException("Falha ao mover documento antigo: " + e.getMessage(), e);
                }
                
                // Atualiza o documento antigo com status ARCHIVED e novo s3Key
                ConsentDocument archived = ConsentDocument.fromJpa(
                        oldDoc.getId(),
                        oldDoc.getTitle(),
                        oldDoc.getDescription(),
                        oldDoc.getVersion(),
                        ConsentDocumentStatus.ARCHIVED,
                        oldDoc.getType(),
                        oldDoc.getCreatedAt(),
                        oldDoc.getPublishedAt(),
                        oldDoc.getCreatedBy(),
                        updatedBy,
                        archivedS3Key,
                        oldDoc.getContentHash(),
                        oldDoc.getFileSizeBytes(),
                        oldDoc.getMimeType()
                );
                repositoryPort.save(archived);
            }
        }

        // Copia o arquivo de drafts/ para published/
        String newS3Key = document.getS3Key().replace("drafts/", "published/");
        log.info("Copiando arquivo de {} para {}", document.getS3Key(), newS3Key);
        
        try {
            // Baixa o arquivo da pasta drafts
            InputStream fileStream = storagePort.downloadFile(consentsBucket, document.getS3Key());
            
            // Faz upload para a pasta published
            storagePort.uploadFile(
                    consentsBucket,
                    newS3Key,
                    fileStream,
                    document.getMimeType(),
                    document.getFileSizeBytes()
            );
            
            // Deleta o arquivo da pasta drafts
            storagePort.deleteFile(consentsBucket, document.getS3Key());
            
            log.info("Arquivo copiado com sucesso para {}", newS3Key);
            
        } catch (Exception e) {
            log.error("Erro ao copiar arquivo para pasta published", e);
            throw new RuntimeException("Falha ao copiar arquivo para pasta published: " + e.getMessage(), e);
        }

        // Publica com o novo s3Key
        ConsentDocument published = document.publish(updatedBy, newS3Key);
        ConsentDocument saved = repositoryPort.save(published);
        
        // Invalida cache relacionado
        invalidateCacheAfterPublish(saved);
        
        // Métricas
        metricsPort.incrementCounter("consent_document_published_total",
            Map.of("entity_id", saved.getId().toString(), "type", saved.getType().name()));
        
        return saved;
    }

    @LogOperation(
        operation = "ARCHIVE_CONSENT_DOCUMENT",
        description = "Arquivando documento de consentimento: {consentDocumentId}",
        audit = true,
        auditAction = "ARCHIVE",
        auditEntityType = "CONSENT_DOCUMENT"
    )
    public ConsentDocument archive(UUID consentDocumentId, String updatedBy) {
        log.info("Arquivando ConsentDocument - ID: {}, UpdatedBy: {}", consentDocumentId, updatedBy);

        ConsentDocument document = repositoryPort.findById(consentDocumentId)
                .orElseThrow(() -> new RuntimeException("ConsentDocument não encontrado: " + consentDocumentId));

        ConsentDocument archived = document.archive(updatedBy);
        ConsentDocument saved = repositoryPort.save(archived);
        
        // Invalida cache relacionado
        invalidateCacheAfterArchive(saved);
        
        // Métricas
        metricsPort.incrementCounter("consent_document_archived_total",
            Map.of("entity_id", saved.getId().toString(), "type", saved.getType().name()));
        
        return saved;
    }

    @LogOperation(
        operation = "DELETE_CONSENT_DOCUMENT",
        description = "Deletando documento de consentimento: {consentDocumentId}",
        audit = true,
        auditAction = "DELETE",
        auditEntityType = "CONSENT_DOCUMENT"
    )
    public void delete(UUID consentDocumentId) {
        log.info("Deletando ConsentDocument - ID: {}", consentDocumentId);

        ConsentDocument document = repositoryPort.findById(consentDocumentId)
                .orElseThrow(() -> new RuntimeException("ConsentDocument não encontrado: " + consentDocumentId));

        // Deleta do S3
        if (document.getS3Key() != null) {
            storagePort.deleteFile(consentsBucket, document.getS3Key());
        }

        // Deleta do BD
        repositoryPort.deleteById(consentDocumentId);
        
        // Invalida cache relacionado
        invalidateCacheAfterDelete(document);
        
        // Métricas
        metricsPort.incrementCounter("consent_document_deleted_total",
            Map.of("entity_id", consentDocumentId.toString(), "type", document.getType().name()));
    }

    // Métodos de invalidação de cache
    private void invalidateCacheAfterCreate(ConsentDocument document) {
        log.debug("Invalidando cache após criar documento: {}", document.getId());
        cachePort.removeByType(document.getType());
        cachePort.removeByStatus(document.getStatus());
        cachePort.removeAllPublished();
    }

    private void invalidateCacheAfterPublish(ConsentDocument document) {
        log.debug("Invalidando cache após publicar documento: {}", document.getId());
        cachePort.removeById(document.getId());
        cachePort.removeByType(document.getType());
        cachePort.removeByStatus(ConsentDocumentStatus.PUBLISHED);
        cachePort.removeAllPublished();
        cachePort.removeLatestPublishedByType(document.getType());
        // Invalida cache de compliance para todos os usuários que podem ter aceitado
        // Isso será feito via evento ou invalidação em lote se necessário
    }

    private void invalidateCacheAfterArchive(ConsentDocument document) {
        log.debug("Invalidando cache após arquivar documento: {}", document.getId());
        cachePort.removeById(document.getId());
        cachePort.removeByType(document.getType());
        cachePort.removeByStatus(ConsentDocumentStatus.ARCHIVED);
        cachePort.removeAllPublished();
        cachePort.removeLatestPublishedByType(document.getType());
    }

    private void invalidateCacheAfterDelete(ConsentDocument document) {
        log.debug("Invalidando cache após deletar documento: {}", document.getId());
        cachePort.removeById(document.getId());
        cachePort.removeByType(document.getType());
        cachePort.removeByStatus(document.getStatus());
        cachePort.removeAllPublished();
        if (document.getStatus() == ConsentDocumentStatus.PUBLISHED) {
            cachePort.removeLatestPublishedByType(document.getType());
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}

