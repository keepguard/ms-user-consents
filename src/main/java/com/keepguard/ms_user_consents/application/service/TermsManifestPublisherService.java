package com.keepguard.ms_user_consents.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.manifest.TermsManifestDTO;
import com.keepguard.ms_user_consents.application.port.out.persistence.ConsentDocumentRepositoryPort;
import com.keepguard.ms_user_consents.application.port.out.storage.StoragePort;
import com.keepguard.ms_user_consents.domain.entity.ConsentDocument;
import com.keepguard.ms_user_consents.domain.enums.ConsentCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermsManifestPublisherService {

    private final ConsentDocumentRepositoryPort repositoryPort;
    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Value("${storage.minio.bucket.consents}")
    private String consentsBucket;

    @Value("${storage.minio.endpoint}")
    private String minioEndpoint;

    public TermsManifestDTO publishManifest(UUID companyId) {
        log.info("Gerando e publicando terms-manifest.json no MinIO para o tenant: {}", companyId);

        List<ConsentDocument> publishedDocs = repositoryPort.findAllPublished();

        List<TermsManifestDTO.TermsManifestDocumentDTO> manifestDocs = publishedDocs.stream()
                .map(doc -> {
                    boolean mandatory = doc.getType().getCategory() == ConsentCategory.ESSENTIAL;
                    String docUrl = String.format("%s/%s/%s", minioEndpoint, consentsBucket, doc.getS3Key());

                    return TermsManifestDTO.TermsManifestDocumentDTO.builder()
                            .id(doc.getId())
                            .type(doc.getType())
                            .category(doc.getType().getCategory())
                            .title(doc.getTitle())
                            .version(doc.getVersion())
                            .mandatory(mandatory)
                            .contentHash(doc.getContentHash())
                            .url(docUrl)
                            .build();
                })
                .sorted(Comparator.comparing((TermsManifestDTO.TermsManifestDocumentDTO d) -> !d.isMandatory())
                        .thenComparing(TermsManifestDTO.TermsManifestDocumentDTO::getTitle))
                .collect(Collectors.toList());

        // Calcula a versão global do manifesto combinando o maior versionamento ou data
        Integer maxDocVersion = publishedDocs.stream()
                .map(ConsentDocument::getVersion)
                .max(Integer::compareTo)
                .orElse(1);

        String manifestVersion = String.format("%d.0", maxDocVersion);
        LocalDateTime now = LocalDateTime.now();

        TermsManifestDTO manifest = TermsManifestDTO.builder()
                .companyId(companyId)
                .version(manifestVersion)
                .publishedAt(now)
                .effectiveAt(now)
                .gracePeriodDays(15)
                .documents(manifestDocs)
                .build();

        try {
            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            byte[] jsonBytes = manifestJson.getBytes(StandardCharsets.UTF_8);

            // Upload para o MinIO particionado por tenant
            String s3Key = companyId != null 
                    ? String.format("public-legal/%s/terms-manifest.json", companyId)
                    : "public-legal/global/terms-manifest.json";

            storagePort.uploadFile(
                    consentsBucket,
                    s3Key,
                    new ByteArrayInputStream(jsonBytes),
                    "application/json",
                    jsonBytes.length
            );

            log.info("terms-manifest.json publicado com sucesso no MinIO - S3Key: {}, Versao: {}", s3Key, manifestVersion);
        } catch (Exception e) {
            log.error("Erro ao gerar/publicar terms-manifest.json no MinIO para o tenant: {}", companyId, e);
            // Não impede a transação principal caso storage falhe temporariamente
        }

        return manifest;
    }
}
