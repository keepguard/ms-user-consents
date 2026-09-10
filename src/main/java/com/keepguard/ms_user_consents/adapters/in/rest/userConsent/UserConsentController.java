package com.keepguard.ms_user_consents.adapters.in.rest.userConsent;

import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentAcceptAllRequestDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentAcceptRequestDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentAcceptAllResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.mapper.UserConsentAdapterMapper;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
import com.keepguard.ms_user_consents.application.port.in.UserConsentPort;
import com.keepguard.lib_common.utils.ValidationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-consents")
@RequiredArgsConstructor
@Tag(name = "User Consents", description = "API para gerenciamento de consentimentos de usuários")
public class UserConsentController {

    private final UserConsentPort userConsentPort;
    private final UserConsentAdapterMapper mapper;

    @PostMapping("/accept")
    @Operation(
        summary = "Registrar aceite de consentimento",
        description = "Registra o aceite de um documento de consentimento por parte do usuário. " +
                    "Este endpoint captura informações de auditoria como IP, User-Agent e geolocalização."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Consentimento aceito com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos ou usuário já aceitou esta versão"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> accept(
            @Valid @RequestBody UserConsentAcceptRequestDTO request,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId,
            HttpServletRequest httpRequest
    ) {
        log.info("POST /api/v1/user-consents/accept - User: {}, Document: {}, Application: {}",
                request.getUserId(), request.getConsentDocumentId(), companyId);

        // Captura informações de auditoria
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var command = UserConsentCreateCommandDTO.builder()
                .companyId(companyId)
                .userId(request.getUserId())
                .email(request.getEmail())
                .consentDocumentId(request.getConsentDocumentId())
                .version(request.getVersion())
                .acceptedAt(request.getAcceptedAt())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .geolocation(request.getGeolocation())
                .build();

        var accepted = userConsentPort.accept(command);
        var response = mapper.toResponseDTO(accepted);

        log.info("Consentimento aceito com sucesso - User: {}, Document: {}, Application: {}",
                request.getUserId(), request.getConsentDocumentId(), companyId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/accept-all")
    @Operation(
        summary = "Aceitar todos os documentos publicados",
        description = "Registra o aceite de todos os documentos de consentimento publicados por parte do usuário. " +
                    "Ignora silenciosamente documentos já aceitos. Este endpoint captura informações de auditoria " +
                    "como IP, User-Agent e geolocalização para cada aceite."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Aceites registrados com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentAcceptAllResponseDTO> acceptAll(
            @Valid @RequestBody UserConsentAcceptAllRequestDTO request,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId,
            HttpServletRequest httpRequest
    ) {
        log.info("POST /api/v1/user-consents/accept-all - User: {}, Application: {}",
                request.getUserId(), companyId);

        // Captura informações de auditoria
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var command = UserConsentAcceptAllCommandDTO.builder()
                .companyId(companyId)
                .userId(request.getUserId())
                .email(request.getEmail())
                .acceptedAt(request.getAcceptedAt())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .geolocation(request.getGeolocation())
                .build();

        var result = userConsentPort.acceptAll(command);
        var response = mapper.toAcceptAllResponseDTO(result);

        log.info("Aceite em lote concluído - User: {}, Total aceitos: {}, Application: {}",
                request.getUserId(), response.getTotalAccepted(), companyId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/accept-batch")
    @Operation(
        summary = "Registrar aceite seletivo em lote de consentimentos",
        description = "Registra o aceite granular de documentos de consentimento por parte do usuário. " +
                    "Captura informações de auditoria forense (IP, User-Agent, UTC)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Aceites em lote registrados com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentAcceptAllResponseDTO> acceptBatch(
            @Valid @RequestBody com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentAcceptBatchRequestDTO request,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId,
            HttpServletRequest httpRequest
    ) {
        log.info("POST /api/v1/user-consents/accept-batch - User: {}, Total itens: {}, Application: {}",
                request.getUserId(), request.getConsents().size(), companyId);

        // Captura informações de auditoria
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var items = request.getConsents().stream()
                .map(i -> com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptBatchCommandDTO.ConsentItemCommandDTO.builder()
                        .documentId(i.getDocumentId())
                        .version(i.getVersion())
                        .accepted(i.isAccepted())
                        .contentHash(i.getContentHash())
                        .build())
                .toList();

        var command = com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptBatchCommandDTO.builder()
                .companyId(companyId)
                .userId(request.getUserId())
                .email(request.getEmail())
                .acceptedAt(request.getAcceptedAt())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .geolocation(request.getGeolocation())
                .consents(items)
                .build();

        var result = userConsentPort.acceptBatch(command);
        var response = mapper.toAcceptAllResponseDTO(result);

        log.info("Aceite seletivo em lote concluído - User: {}, Total gravados: {}, Application: {}",
                request.getUserId(), response.getTotalAccepted(), companyId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/revoke")
    @Operation(
        summary = "Revogar consentimento do usuário",
        description = "Revoga um consentimento previamente concedido. Documentos obrigatórios não podem ser revogados individualmente sem o encerramento da conta."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consentimento revogado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Tentativa de revogar documento obrigatório ou dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Consentimento ou documento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> revoke(
            @Valid @RequestBody com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentRevokeRequestDTO request,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("POST /api/v1/user-consents/revoke - User: {}, Document: {}, Application: {}",
                request.getUserId(), request.getConsentDocumentId(), companyId);

        var command = com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentRevokeCommandDTO.builder()
                .companyId(companyId)
                .userId(request.getUserId())
                .consentDocumentId(request.getConsentDocumentId())
                .reason(request.getReason())
                .build();

        var revoked = userConsentPort.revoke(command);
        var response = mapper.toResponseDTO(revoked);

        log.info("Consentimento revogado com sucesso - User: {}, Document: {}, Application: {}",
                request.getUserId(), request.getConsentDocumentId(), companyId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Buscar consentimento por ID",
        description = "Busca um consentimento específico pelo seu ID único. Aplica isolamento multi-tenant via X-Company-Id."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consentimento encontrado com sucesso"),
        @ApiResponse(responseCode = "403", description = "Consentimento pertence a outro tenant"),
        @ApiResponse(responseCode = "404", description = "Consentimento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> findById(
            @PathVariable UUID id,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/user-consents/{} - Application: {}", id, companyId);

        var consent = userConsentPort.findById(id);

        // Isolamento multi-tenant: rejeita acesso cross-tenant (LGPD Gap 4)
        enforceTenantIsolation(consent, companyId);

        var response = mapper.toResponseDTO(consent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Buscar todos os consentimentos de um usuário",
        description = "Retorna todos os consentimentos registrados para um usuário específico, filtrados pelo tenant do solicitante."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de consentimentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<UserConsentResponseDTO>> findByUserId(
            @PathVariable UUID userId,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/user-consents/user/{} - Application: {}", userId, companyId);

        var consents = userConsentPort.findByUserId(userId);

        // Isolamento multi-tenant: filtra apenas consentimentos do tenant solicitante (LGPD Gap 4)
        // Consents legados (companyId == null) são incluídos por compatibilidade retroativa
        var responses = consents.stream()
                .filter(c -> c.getCompanyId() == null || companyId.equals(c.getCompanyId()))
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/document/{consentDocumentId}")
    @Operation(
        summary = "Buscar consentimentos de um usuário para um documento específico",
        description = "Retorna todos os consentimentos de um usuário para um documento de consentimento específico, filtrados pelo tenant."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de consentimentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<UserConsentResponseDTO>> findByUserIdAndConsentDocumentId(
            @PathVariable UUID userId,
            @PathVariable UUID consentDocumentId,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{} - Application: {}", userId, consentDocumentId, companyId);

        var consents = userConsentPort.findByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Isolamento multi-tenant: filtra apenas consentimentos do tenant solicitante (LGPD Gap 4)
        // Consents legados (companyId == null) são incluídos por compatibilidade retroativa
        var responses = consents.stream()
                .filter(c -> c.getCompanyId() == null || companyId.equals(c.getCompanyId()))
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/document/{consentDocumentId}/latest")
    @Operation(
        summary = "Buscar último consentimento de um usuário para um documento",
        description = "Retorna o último consentimento registrado de um usuário para um documento específico. Aplica isolamento multi-tenant."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Último consentimento encontrado com sucesso"),
        @ApiResponse(responseCode = "403", description = "Consentimento pertence a outro tenant"),
        @ApiResponse(responseCode = "404", description = "Nenhum consentimento encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> findLatestByUserIdAndConsentDocumentId(
            @PathVariable UUID userId,
            @PathVariable UUID consentDocumentId,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{}/latest - Application: {}", userId, consentDocumentId, companyId);

        var consent = userConsentPort.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Isolamento multi-tenant: rejeita acesso cross-tenant (LGPD Gap 4)
        enforceTenantIsolation(consent, companyId);

        var response = mapper.toResponseDTO(consent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/document/{consentDocumentId}/version/{version}/check")
    @Operation(
        summary = "Verificar se usuário aceitou uma versão específica",
        description = "Verifica se um usuário já aceitou uma versão específica de um documento de consentimento."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status de aceite verificado com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Boolean> hasAccepted(
            @PathVariable UUID userId,
            @PathVariable UUID consentDocumentId,
            @PathVariable Integer version,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{}/version/{}/check - Application: {}", userId, consentDocumentId, version, companyId);

        boolean hasAccepted = userConsentPort.hasAccepted(userId, consentDocumentId, version);

        return ResponseEntity.ok(hasAccepted);
    }

    @DeleteMapping("/user/{userId}")
    @Operation(
        summary = "Deletar todos os consentimentos de um usuário",
        description = "Remove todos os consentimentos registrados para um usuário específico. " +
                    "Esta operação é idempotente e deve ser usada apenas para compensação de transações."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Consentimentos deletados com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Void> deleteAllByUserId(
            @PathVariable UUID userId,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("DELETE /api/v1/user-consents/user/{} - Application: {}", userId, companyId);

        userConsentPort.deleteAllByUserId(userId);

        log.info("Todos os consentimentos deletados com sucesso para usuário: {} - Application: {}", userId, companyId);

        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Multi-Tenant Isolation — LGPD Gap 4: Isolamento por company_id
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Verifica se o consentimento pertence ao tenant da requisição (X-Company-Id).
     * Retorna 403 Forbidden se o companyId do consentimento não corresponder
     * ao tenant do solicitante, impedindo vazamento inter-tenant (LGPD Art. 46).
     *
     * <p>Consentimentos legados (sem companyId preenchido) são permitidos por
     * compatibilidade retroativa, evitando quebra de dados migrados.</p>
     */
    private void enforceTenantIsolation(UserConsentViewDTO consent, UUID requestCompanyId) {
        if (consent == null || consent.getCompanyId() == null) {
            // Dados legados sem companyId: permite acesso por compatibilidade
            return;
        }
        if (!requestCompanyId.equals(consent.getCompanyId())) {
            log.warn("Tentativa de acesso cross-tenant bloqueada: consent.companyId={}, request.companyId={}",
                    consent.getCompanyId(), requestCompanyId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acesso negado: consentimento pertence a outro tenant");
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}

