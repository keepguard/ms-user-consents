package com.keepguard.ms_user_consents.adapters.in.rest.consentDocument;

import com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.dto.response.ConsentDocumentResponseDTO;
import com.keepguard.ms_user_consents.adapters.in.rest.consentDocument.mapper.ConsentDocumentAdapterMapper;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentCreateCommandDTO;
import com.keepguard.ms_user_consents.application.port.in.ConsentDocumentPort;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import com.keepguard.lib_common.utils.ValidationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/consent-documents")
@RequiredArgsConstructor
@Tag(name = "Consent Documents", description = "API para gerenciamento de documentos de consentimento")
public class ConsentDocumentController {

    private final ConsentDocumentPort consentDocumentPort;
    private final ConsentDocumentAdapterMapper mapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Criar novo documento de consentimento (DRAFT)",
        description = "Cria um novo documento de consentimento no status DRAFT. " +
                    "O documento será criado com o arquivo fornecido e ficará disponível para revisão antes da publicação."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Documento criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos ou arquivo inválido"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ConsentDocumentResponseDTO> create(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("type") ConsentType type,
            @RequestParam("createdBy") String createdBy,
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("POST /api/v1/consent-documents - Type: {}, Title: {}, Application: {}", type, title, companyId);

        // Valida o X-Company-Id

        try {
            var command = ConsentDocumentCreateCommandDTO.builder()
                    .title(title)
                    .description(description)
                    .type(type)
                    .createdBy(createdBy)
                    .fileContent(file.getInputStream())
                    .fileName(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();

            var created = consentDocumentPort.create(command);
            var response = mapper.toResponseDTO(created);

            log.info("Documento criado com sucesso - ID: {}, Type: {}, Application: {}", 
                    created.getId(), type, companyId);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Erro ao criar consent document - Application: {}", companyId, e);
            throw new RuntimeException("Erro ao criar documento: " + e.getMessage(), e);
        }
    }

    @PostMapping("/{consentDocumentId}/publish")
    @Operation(
        summary = "Publicar documento de consentimento",
        description = "Publica um documento de consentimento que estava em status DRAFT, " +
                    "tornando-o disponível para aceite pelos usuários."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Documento publicado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Documento não pode ser publicado"),
        @ApiResponse(responseCode = "404", description = "Documento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ConsentDocumentResponseDTO> publish(
            @PathVariable UUID consentDocumentId,
            @RequestParam String updatedBy,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("POST /api/v1/consent-documents/{}/publish - UpdatedBy: {}, Application: {}", consentDocumentId, updatedBy, companyId);

        // Valida o X-Company-Id

        var published = consentDocumentPort.publish(consentDocumentId, updatedBy);
        var response = mapper.toResponseDTO(published);

        log.info("Documento publicado com sucesso - ID: {}, Application: {}", consentDocumentId, companyId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{consentDocumentId}/archive")
    @Operation(
        summary = "Arquivar documento de consentimento",
        description = "Arquiva um documento de consentimento, removendo-o da lista de documentos ativos."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Documento arquivado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Documento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ConsentDocumentResponseDTO> archive(
            @PathVariable UUID consentDocumentId,
            @RequestParam String updatedBy,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId
    ) {
        log.info("POST /api/v1/consent-documents/{}/archive - UpdatedBy: {}, Application: {}", consentDocumentId, updatedBy, companyId);

        // Valida o X-Company-Id

        var archived = consentDocumentPort.archive(consentDocumentId, updatedBy);
        var response = mapper.toResponseDTO(archived);

        log.info("Documento arquivado com sucesso - ID: {}, Application: {}", consentDocumentId, companyId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Buscar documento por ID",
        description = "Busca um documento de consentimento específico pelo seu ID único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Documento encontrado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Documento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ConsentDocumentResponseDTO> findById(
            @PathVariable UUID id,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/consent-documents/{} - Application: {}", id, companyId);

        // Valida o X-Company-Id

        var document = consentDocumentPort.findById(id);
        var response = mapper.toResponseDTO(document);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(
        summary = "Buscar documentos por status",
        description = "Retorna todos os documentos de consentimento com o status especificado."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de documentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<ConsentDocumentResponseDTO>> findByStatus(
            @PathVariable ConsentDocumentStatus status,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/consent-documents/status/{} - Application: {}", status, companyId);

        // Valida o X-Company-Id

        var documents = consentDocumentPort.findByStatus(status);
        var responses = documents.stream()
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/type/{type}")
    @Operation(
        summary = "Buscar documentos por tipo",
        description = "Retorna todos os documentos de consentimento do tipo especificado."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de documentos encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<ConsentDocumentResponseDTO>> findByType(
            @PathVariable ConsentType type,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/consent-documents/type/{} - Application: {}", type, companyId);

        // Valida o X-Company-Id

        var documents = consentDocumentPort.findByType(type);
        var responses = documents.stream()
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/type/{type}/latest-published")
    @Operation(
        summary = "Buscar última versão publicada por tipo",
        description = "Retorna a versão mais recente publicada de um documento de consentimento do tipo especificado."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Última versão publicada encontrada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Nenhuma versão publicada encontrada"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<ConsentDocumentResponseDTO> findLatestPublishedByType(
            @PathVariable ConsentType type,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/consent-documents/type/{}/latest-published - Application: {}", type, companyId);

        // Valida o X-Company-Id

        var document = consentDocumentPort.findLatestPublishedByType(type);
        var response = mapper.toResponseDTO(document);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/published")
    @Operation(
        summary = "Buscar todos os documentos publicados",
        description = "Retorna todos os documentos de consentimento que estão publicados e disponíveis para aceite."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de documentos publicados encontrada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<List<ConsentDocumentResponseDTO>> findAllPublished(
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("GET /api/v1/consent-documents/published - Application: {}", companyId);

        // Valida o X-Company-Id

        var documents = consentDocumentPort.findAllPublished();
        var responses = documents.stream()
                .map(mapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Deletar documento de consentimento",
        description = "Remove permanentemente um documento de consentimento do sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Documento deletado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Documento não encontrado"),
        @ApiResponse(responseCode = "401", description = "Aplicação não autorizada"),
        @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @Parameter(description = "UUID da empresa", required = true)
            @RequestHeader("X-Company-Id") UUID companyId) {
        log.info("DELETE /api/v1/consent-documents/{} - Application: {}", id, companyId);

        // Valida o X-Company-Id

        consentDocumentPort.delete(id);

        log.info("Documento deletado com sucesso - ID: {}, Application: {}", id, companyId);

        return ResponseEntity.noContent().build();
    }
}

