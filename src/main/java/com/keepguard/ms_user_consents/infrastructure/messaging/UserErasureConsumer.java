package com.keepguard.ms_user_consents.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.service.UserConsentCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserErasureConsumer {

    private final UserConsentCommandService userConsentCommandService;
    private final ObjectMapper objectMapper;

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(value = "${keepguard.events.user-erasure-queue:ms.user-consents.user-erasure}", durable = "true"),
            exchange = @Exchange(value = "${keepguard.events.exchange:keepguard-events-exchange}", type = "topic"),
            key = "${keepguard.events.erasure-routing-key:user.erasure.requested}"
        )
    )
    public void handleUserErasure(String message) {
        try {
            log.info("Recebida solicitação de eliminação de titular (Art. 18 LGPD) no ms-user-consents: {}", message);
            JsonNode json = objectMapper.readTree(message);
            String userIdStr = json.path("userId").asText();
            if (userIdStr != null && !userIdStr.isBlank()) {
                UUID userId = UUID.fromString(userIdStr);
                userConsentCommandService.deleteAllByUserId(userId);
                log.info("Consentimentos do titular {} expurgados com sucesso sob Art. 18 LGPD", userId);
            }
        } catch (Exception e) {
            log.error("Erro ao processar eliminação de titular no ms-user-consents: {}", e.getMessage(), e);
        }
    }
}
