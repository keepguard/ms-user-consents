package com.keepguard.ms_user_consents.infrastructure.filter;

import com.keepguard.ms_user_consents.infrastructure.context.CorrelationContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro para capturar e propagar o ID de correlação entre serviços.
 * Extrai o header X-Correlation-ID da requisição ou gera um novo UUID.
 */
@Slf4j
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Extrai ou gera o ID de correlação
            String correlationId = extractCorrelationId(httpRequest);
            
            // Define no contexto da thread
            CorrelationContext.setCorrelationId(correlationId);
            
            // Adiciona ao header da resposta para propagação
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            log.debug("Correlation ID: {}", correlationId);
            
            chain.doFilter(request, response);
            
        } finally {
            // Limpa o contexto após o processamento
            CorrelationContext.clear();
        }
    }

    /**
     * Extrai o ID de correlação do header da requisição.
     * Se não existir, gera um novo UUID.
     *
     * @param request Requisição HTTP
     * @return ID de correlação
     */
    private String extractCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Correlation ID não encontrado no header, gerando novo: {}", correlationId);
        }
        
        return correlationId;
    }
}

