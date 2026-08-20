package com.keepguard.ms_user_consents.adapters.in.rest.userConsent;

import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentAcceptAllRequestDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.request.UserConsentAcceptRequestDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentAcceptAllResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.dto.response.UserConsentResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.userConsent.mapper.UserConsentAdapterMapper;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentAcceptAllCommandDTO;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentCreateCommandDTO;
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
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader,
            HttpServletRequest httpRequest
    ) {
        log.info("POST /api/v1/user-consents/accept - User: {}, Document: {}, Application: {}",
                request.getUserId(), request.getConsentDocumentId(), tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        // Captura informações de auditoria
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var command = UserConsentCreateCommandDTO.builder()
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
                request.getUserId(), request.getConsentDocumentId(), tenantId);

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
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader,
            HttpServletRequest httpRequest
    ) {
        log.info("POST /api/v1/user-consents/accept-all - User: {}, Application: {}",
                request.getUserId(), tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        // Captura informações de auditoria
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        var command = UserConsentAcceptAllCommandDTO.builder()
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
                request.getUserId(), response.getTotalAccepted(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Buscar consentimento por ID",
        description = "Busca um consentimento específico pelo seu ID único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consentimento encontrado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Consentimento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> findById(
            @PathVariable UUID id,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/user-consents/{} - Application: {}", id, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var consent = userConsentPort.findById(id);
        var response = mapper.toResponseDTO(consent);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Buscar todos os consentimentos de um usuário",
        description = "Retorna todos os consentimentos registrados para um usuário específico."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de consentimentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<UserConsentResponseDTO>> findByUserId(
            @PathVariable UUID userId,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/user-consents/user/{} - Application: {}", userId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var consents = userConsentPort.findByUserId(userId);
        var responses = consents.stream()
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/document/{consentDocumentId}")
    @Operation(
        summary = "Buscar consentimentos de um usuário para um documento específico",
        description = "Retorna todos os consentimentos de um usuário para um documento de consentimento específico."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de consentimentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<UserConsentResponseDTO>> findByUserIdAndConsentDocumentId(
            @PathVariable UUID userId,
            @PathVariable UUID consentDocumentId,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{} - Application: {}", userId, consentDocumentId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var consents = userConsentPort.findByUserIdAndConsentDocumentId(userId, consentDocumentId);
        var responses = consents.stream()
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/document/{consentDocumentId}/latest")
    @Operation(
        summary = "Buscar último consentimento de um usuário para um documento",
        description = "Retorna o último consentimento registrado de um usuário para um documento específico."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Último consentimento encontrado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Nenhum consentimento encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<UserConsentResponseDTO> findLatestByUserIdAndConsentDocumentId(
            @PathVariable UUID userId,
            @PathVariable UUID consentDocumentId,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{}/latest - Application: {}", userId, consentDocumentId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var consent = userConsentPort.findLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);
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
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader
    ) {
        log.info("GET /api/v1/user-consents/user/{}/document/{}/version/{}/check - Application: {}", userId, consentDocumentId, version, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

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
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("DELETE /api/v1/user-consents/user/{} - Application: {}", userId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        userConsentPort.deleteAllByUserId(userId);

        log.info("Todos os consentimentos deletados com sucesso para usuário: {} - Application: {}", userId, tenantId);

        return ResponseEntity.noContent().build();
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

