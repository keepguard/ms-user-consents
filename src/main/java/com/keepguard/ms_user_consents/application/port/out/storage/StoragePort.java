package com.keepguard.ms_user_consents.application.port.out.storage;

import java.io.InputStream;

public interface StoragePort {
    
    /**
     * Faz upload de um arquivo para o bucket
     * @param bucket Nome do bucket
     * @param objectKey Chave do objeto (caminho)
     * @param inputStream Stream do arquivo
     * @param contentType Tipo de conteúdo
     * @param fileSize Tamanho do arquivo
     * @return URL do arquivo armazenado
     */
    String uploadFile(String bucket, String objectKey, InputStream inputStream, String contentType, long fileSize);
    
    /**
     * Verifica se um arquivo existe no bucket
     * @param bucket Nome do bucket
     * @param objectKey Chave do objeto
     * @return true se existe, false caso contrário
     */
    boolean fileExists(String bucket, String objectKey);
    
    /**
     * Faz download de um arquivo do bucket
     * @param bucket Nome do bucket
     * @param objectKey Chave do objeto
     * @return Stream do arquivo
     */
    InputStream downloadFile(String bucket, String objectKey);
    
    /**
     * Deleta um arquivo do bucket
     * @param bucket Nome do bucket
     * @param objectKey Chave do objeto
     */
    void deleteFile(String bucket, String objectKey);
    
    /**
     * Gera URL pré-assinada para acesso temporário
     * @param bucket Nome do bucket
     * @param objectKey Chave do objeto
     * @param expirationMinutes Tempo de expiração em minutos
     * @return URL pré-assinada
     */
    String generatePresignedUrl(String bucket, String objectKey, int expirationMinutes);
}

