package com.keepguard.ms_user_consents.infrastructure.storage;

import com.keepguard.ms_user_consents.application.port.out.storage.StoragePort;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinIOStorageAdapter implements StoragePort {

    private final MinioClient minioClient;
    
    @Value("${storage.minio.endpoint}")
    private String minioEndpoint;

    @Override
    public String uploadFile(String bucket, String objectKey, InputStream inputStream, String contentType, long fileSize) {
        try {
            log.info("Iniciando upload para MinIO - Bucket: {}, Key: {}, ContentType: {}, Size: {}",
                    bucket, objectKey, contentType, fileSize);

            // Verifica se o bucket existe, se não, cria
            ensureBucketExists(bucket);

            // Faz upload do arquivo
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, fileSize, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("Upload concluído com sucesso - Bucket: {}, Key: {}", bucket, objectKey);
            return String.format("%s/%s/%s", minioEndpoint, bucket, objectKey);

        } catch (Exception e) {
            log.error("Erro ao fazer upload para MinIO - Bucket: {}, Key: {}", bucket, objectKey, e);
            throw new RuntimeException("Falha ao fazer upload do arquivo: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.debug("Arquivo não encontrado - Bucket: {}, Key: {}", bucket, objectKey);
            return false;
        }
    }

    @Override
    public InputStream downloadFile(String bucket, String objectKey) {
        try {
            log.info("Baixando arquivo do MinIO - Bucket: {}, Key: {}", bucket, objectKey);
            
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erro ao baixar arquivo do MinIO - Bucket: {}, Key: {}", bucket, objectKey, e);
            throw new RuntimeException("Falha ao baixar arquivo: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String bucket, String objectKey) {
        try {
            log.info("Deletando arquivo do MinIO - Bucket: {}, Key: {}", bucket, objectKey);
            
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            
            log.info("Arquivo deletado com sucesso - Bucket: {}, Key: {}", bucket, objectKey);
        } catch (Exception e) {
            log.error("Erro ao deletar arquivo do MinIO - Bucket: {}, Key: {}", bucket, objectKey, e);
            throw new RuntimeException("Falha ao deletar arquivo: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String objectKey, int expirationMinutes) {
        try {
            log.info("Gerando URL pré-assinada - Bucket: {}, Key: {}, Expiration: {} minutos",
                    bucket, objectKey, expirationMinutes);
            
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirationMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erro ao gerar URL pré-assinada - Bucket: {}, Key: {}", bucket, objectKey, e);
            throw new RuntimeException("Falha ao gerar URL pré-assinada: " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!exists) {
                log.info("Bucket {} não existe, criando...", bucket);
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
                log.info("Bucket {} criado com sucesso", bucket);
            }
        } catch (Exception e) {
            log.error("Erro ao verificar/criar bucket: {}", bucket, e);
            throw new RuntimeException("Falha ao verificar/criar bucket: " + e.getMessage(), e);
        }
    }
}

