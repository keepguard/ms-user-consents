package com.keepguard.ms_user_consents.infrastructure.context;

import java.util.UUID;

/**
 * Contexto de correlação para rastreamento de requisições entre serviços.
 * Armazena o ID de correlação da requisição atual.
 */
public class CorrelationContext {

    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();

    /**
     * Obtém o ID de correlação da requisição atual.
     * Se não existir, gera um novo UUID.
     *
     * @return ID de correlação
     */
    public static String getCorrelationId() {
        String id = correlationId.get();
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            correlationId.set(id);
        }
        return id;
    }

    /**
     * Define o ID de correlação da requisição atual.
     *
     * @param id ID de correlação
     */
    public static void setCorrelationId(String id) {
        correlationId.set(id);
    }

    /**
     * Limpa o ID de correlação da thread atual.
     * Deve ser chamado após o processamento da requisição.
     */
    public static void clear() {
        correlationId.remove();
    }
}

