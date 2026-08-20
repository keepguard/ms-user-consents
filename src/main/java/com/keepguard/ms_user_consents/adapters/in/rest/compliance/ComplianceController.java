package com.keepguard.ms_user_consents.adapters.in.rest.compliance;

import com.keepguard.ms_user_consents.adapters.in.rest.compliance.dto.response.ComplianceStatusResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.compliance.mapper.ComplianceAdapterMapper;
import com.keepguard.ms_user_consents.application.port.in.CompliancePort;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import com.keepguard.lib_common.utils.ValidationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "API para validação de compliance de consentimentos")
public class ComplianceController {

    private final CompliancePort compliancePort;
    private final ComplianceAdapterMapper mapper;

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Verifica compliance completo do usuário",
        description = "Verifica o status de compliance de um usuário, retornando informações detalhadas " +
                    "sobre quais documentos de consentimento foram aceitos e quais ainda estão pendentes."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status de compliance verificado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ComplianceStatusResponseDTO> checkUserCompliance(
            @PathVariable UUID userId,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/compliance/user/{} - Application: {}", userId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var status = compliancePort.checkUserCompliance(userId);
        var response = mapper.toResponseDTO(status);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/mandatory")
    @Operation(
        summary = "Verifica se usuário tem todos os consentimentos obrigatórios",
        description = "Verifica se um usuário possui todos os consentimentos obrigatórios aceitos."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status de consentimentos obrigatórios verificado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Boolean> hasMandatoryConsents(
            @PathVariable UUID userId,
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/compliance/user/{}/mandatory - Application: {}", userId, tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        boolean hasMandatory = compliancePort.hasMandatoryConsents(userId);

        return ResponseEntity.ok(hasMandatory);
    }

    @GetMapping("/consent-types")
    @Operation(
        summary = "Lista todos os tipos de consentimento",
        description = "Retorna todos os tipos de consentimento disponíveis no sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de tipos de consentimento retornada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<ConsentType>> listAllConsentTypes(
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/compliance/consent-types - Application: {}", tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var types = compliancePort.listAllConsentTypes();

        return ResponseEntity.ok(types);
    }

    @GetMapping("/consent-types/mandatory")
    @Operation(
        summary = "Lista apenas os tipos de consentimento obrigatórios",
        description = "Retorna apenas os tipos de consentimento que são obrigatórios para os usuários."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de tipos obrigatórios retornada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<ConsentType>> listMandatoryConsentTypes(
            @Parameter(description = "UUID da aplicação", required = true)
            @RequestHeader("X-Tenant-Id") String tenantIdHeader) {
        log.info("GET /api/v1/compliance/consent-types/mandatory - Application: {}", tenantIdHeader);

        // Valida o X-Tenant-Id
        UUID tenantId = ValidationUtils.validateTenantId(tenantIdHeader);

        var types = compliancePort.listMandatoryConsentTypes();

        return ResponseEntity.ok(types);
    }
}

