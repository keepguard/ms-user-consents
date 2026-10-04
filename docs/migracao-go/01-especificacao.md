# ms-user-consents — Especificação do comportamento atual (Java) para migração Go

Data: 2026-10-04. Fonte: `keepguard-core/backend/ms/ms-user-consents` (Spring Boot 3.5.3, `pom.xml:8`; artefato `1.0.9`, `pom.xml:13`; Java 25, `pom.xml:18`; MinIO SDK 8.5.7, `pom.xml:87-89`; Resilience4j 2.2.0, `pom.xml:123-137`; lib-common `1.0.37-SNAPSHOT`, `pom.xml:113-115`). Imagem ao vivo `ms-user-consents:f84fae9` (`kubectl get deploy`, 2026-10-04).

Abreviações (relativas a `src/main/java/com/keepguard/ms_user_consents/`): `ctrl/` = `adapters/in/rest/`, `app/` = `application/`, `svc/` = `application/service/`, `dom/` = `domain/`, `infra/` = `infrastructure/`, `res/` = `src/main/resources/`, `lib/` = `../lib-common/src/main/java/com/keepguard/lib_common/`.

Legenda: **[código]** lido diretamente; **[framework]** comportamento padrão de Spring/Jackson/Hibernate/Spring Data inferido — validar com chamada real antes de congelar o contrato Go.

Documentos irmãos: consumidores e infra em `02-consumidores-e-infra.md`; libs em `03-libs.md`; resiliência em `04-resiliencia-integracoes.md`.

---

## 0. Resumo executivo (maiores riscos para a reescrita)

1. **24 handlers HTTP** em 4 controllers: ConsentDocument 9, UserConsent 10, Compliance 4, Health 1 (seção 1). **Nenhuma autenticação**: não há `spring-boot-starter-security` no `pom.xml:23-156`; `JWT_SECRET` é injetado (`helm/templates/deployment.yaml:32-36`) mas nenhum código o lê. `X-Company-Id` é obrigatório em todos os handlers de negócio, mas só é usado de fato em 4 rotas de user-consent (seção 1.0).
2. **Erasure LGPD provavelmente não apaga nada**: `deleteAllByUserId` é um `@Query("DELETE ...")` **sem `@Modifying`** (`infra/persistence/spring/UserConsentSpringRepository.java:28-29`) → Hibernate 6 recusa DML em query de seleção [framework — validar]. No HTTP vira 500; no consumer a exceção é engolida (`infra/messaging/UserErasureConsumer.java:41-43`) e a mensagem é confirmada (ack) → perda silenciosa.
3. **`findLatestByUserIdAndConsentDocumentId` retorna `Optional` de uma query sem limite** (`UserConsentSpringRepository.java:20-24`). Assim que existir mais de uma linha para (user, doc) — o que o `accept-batch` cria ao reaceitar depois de revogar (`svc/UserConsentCommandService.java:232-259`) — a chamada lança `IncorrectResultSizeDataAccessException` → 500 em `/latest`, `/revoke` e `/accept-batch` [framework — validar].
4. **Contrato de erro não é ProblemDetail**: `Map` simples na raiz com `timestamp,status,error,message` (+ `errors` na validação), sem `errorCode`, sem `properties` (`infra/rest/GlobalExceptionHandler.java:86-97`). Quase todo "não encontrado" é `RuntimeException` → **500**, não 404 (seção 2). O 403 de isolamento de tenant (`ResponseStatusException`) é capturado pelo handler genérico → **500** [framework — validar].
5. **Divergência `mandatory`**: o manifesto público marca `mandatory` só para `ConsentCategory.ESSENTIAL` (`svc/TermsManifestPublisherService.java:44`), enquanto revoke/compliance usam `ConsentType.isMandatory()` = ESSENTIAL **e** FUNCTIONAL (`dom/enums/ConsentType.java:37-39`, `dom/enums/ConsentCategory.java:7-8`). `DATA_PROCESSING` e `ESSENTIAL_COOKIES` aparecem como opcionais no manifesto, mas não podem ser revogados e contam para compliance.
6. **Manifesto sempre global**: as duas chamadas passam `companyId = null` (`svc/ConsentDocumentCommandService.java:240,269`) → sempre grava `public-legal/global/terms-manifest.json` (`svc/TermsManifestPublisherService.java:85-87`). O backoffice tenta primeiro `public-legal/{tenantId}/...` e cai no global em 404 (`frontend/backoffice/src/services/termsSyncService.ts:83-95`). A `url` de cada documento usa o endpoint interno (`http://minio:9000` em prod) (`TermsManifestPublisherService.java:45`).
7. **Documentos são globais, não por tenant**: `consent_documents.company_id/tenant_id` existem (`infra/persistence/entity/ConsentDocumentJpaEntity.java:36-40`), mas o mapper nunca os preenche (`infra/persistence/mapper/ConsentDocumentJpaMapper.java:29-45`). `UserConsent.tenantId` também é sempre `null`: o controller não preenche (`ctrl/userConsent/UserConsentController.java:65-75`).
8. **MinIO fora da transação**: create/publish/delete mexem no MinIO antes do commit; falha no banco deixa o objeto movido/órfão (seção 7). Publicar um documento já PUBLISHED arquiva o próprio documento antes de falhar (seção 4.2).
9. **Schema via Hibernate `ddl-auto: update`** (`res/application.yml:43`), nenhum profile sobrescreve; sem Flyway. Duas tabelas em `ms_user_consents` (seção 6). Sem unique constraint de aceite por versão: a unicidade é só um `exists` antes do insert (race).
10. **Resilience4j "aplicado" mas inócuo**: o YAML é lido (starter `resilience4j-spring-boot3` + AOP, `pom.xml:58-61,123-127`), mas todo método de cache faz `try/catch` interno e devolve `null`/engole (ex.: `infra/redis/ConsentDocumentCacheService.java:40-50`), então o circuit breaker e o retry nunca veem exceção (seção 8).

---

## 1. Inventário de endpoints

### 1.0 Regras gerais

- **Porta**: `server.port: 8086` (`res/application.yml:2`, `res/application-prod.yml:2`, `res/application-dev.yml:2`); `8586` em `res/application-local.yml:2`. O Deployment ao vivo injeta `SERVER_PORT=8086` (kubectl), assim como o Helm (`helm/values.yaml:17`, `helm/templates/deployment.yaml:30-31`). Sem context-path.
- **Contagem**: ConsentDocument 9 (`ctrl/consentDocument/ConsentDocumentController.java:36,87,118,147,172,198,224,249,274`), UserConsent 10 (`ctrl/userConsent/UserConsentController.java:40,86,131,185,221,248,276,306,335,359`), Compliance 4 (`ctrl/compliance/ComplianceController.java:31,57,81,103`), Health 1 (`ctrl/health/HealthController.java:21`) = **24**.
- **Segurança**: nenhuma. Sem Spring Security no classpath (`pom.xml:23-156`), sem filtro de JWT; o único filtro próprio é `CorrelationIdFilter` (`infra/filter/CorrelationIdFilter.java:18-50`). Swagger habilitado em todos os profiles (`res/application.yml:146-150`).
- **`X-Company-Id`**: `@RequestHeader("X-Company-Id") UUID` obrigatório em todos os 23 handlers de negócio (ex.: `ConsentDocumentController.java:55`). Uso real:
  - ConsentDocument e Compliance: só log (comentários "Valida o X-Company-Id" sem código, ex. `ConsentDocumentController.java:59,108`; `ComplianceController.java:49`).
  - UserConsent: grava `companyId` no aceite (`UserConsentController.java:66,113,166`) e filtra/isola leituras (`:242,268-271,298-301,329`). `hasAccepted` e `DELETE /user/{userId}` ignoram o tenant (`:354,376`).
  - Ausente → 400 com mensagem `Header obrigatório 'X-Company-Id' não foi fornecido.` (`infra/rest/GlobalExceptionHandler.java:66-78`). Não-UUID → `MethodArgumentTypeMismatchException` → 500 genérico [framework].
- **Serialização**: não há `ObjectMapper` próprio no serviço nem na lib (só `RedisConfig`, `MinIOConfig`, `SwaggerConfig` em `infra/config/`; lib sem `@Bean ObjectMapper`) → mapper do Spring Boot com `spring.jackson.*` aplicado [framework]:
  - `INDENT_OUTPUT: true` → respostas **pretty-printed** (`res/application.yml:49-51`).
  - `FAIL_ON_UNKNOWN_PROPERTIES: false`, `ADJUST_DATES_TO_CONTEXT_TIME_ZONE: false`, `time-zone: UTC` (`res/application.yml:52-55`).
  - `LocalDateTime` de resposta → ISO sem zona, fração variável (ex. `"2026-10-04T13:05:07.123456"`) [framework: Boot desliga `WRITE_DATES_AS_TIMESTAMPS`]. Nenhum DTO de resposta tem `@JsonFormat` (`ctrl/userConsent/dto/response/UserConsentResponseDTO.java:15-31`, `ctrl/consentDocument/dto/response/ConsentDocumentResponseDTO.java:17-32`).
  - Sem `NON_NULL`: `null` é serializado [framework]. Nomes JSON = nomes Java camelCase (Lombok `@Data`). Booleans `compliant/accepted/mandatory` saem com esses nomes (getter `isX`) [framework].
  - Enums por `name()` (ex. `"TERMS_OF_USE"`) [framework].
- **Datas de entrada**: `acceptedAt` com `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")` em `LocalDateTime` (`ctrl/userConsent/dto/request/UserConsentAcceptRequestDTO.java:31-33`, `UserConsentAcceptAllRequestDTO.java:25-27`, `UserConsentAcceptBatchRequestDTO.java:28-30`). Exige exatamente 3 casas de milissegundo e `Z` literal; outro formato → `HttpMessageNotReadableException` → 400 "Dados de entrada inválidos" [framework]. O `Z` é descartado: grava o valor como hora local.
- **Paginação**: nenhuma. Todas as listas são `List<...>` completas.
- **IP do cliente** (`UserConsentController.java:408-432`): primeiro header não vazio e diferente de `unknown` (case-insensitive) na ordem `X-Forwarded-For, X-Real-IP, Proxy-Client-IP, WL-Proxy-Client-IP, HTTP_X_FORWARDED_FOR, HTTP_X_FORWARDED, HTTP_X_CLUSTER_CLIENT_IP, HTTP_CLIENT_IP, HTTP_FORWARDED_FOR, HTTP_FORWARDED, HTTP_VIA, REMOTE_ADDR`; pega o primeiro item antes de `,` com `trim`. Senão `request.getRemoteAddr()`. Sem validação de formato/tamanho (coluna `ip_address` tem 45 chars, seção 6).
- **User-Agent**: `httpRequest.getHeader("User-Agent")` cru (`UserConsentController.java:63,110,154`); coluna 512 chars.

### 1.1 ConsentDocumentController — base `/api/v1/consent-documents` (`ctrl/consentDocument/ConsentDocumentController.java:28`)

| # | Método/Path | Entrada | Sucesso | Linhas |
|---|---|---|---|---|
| 1 | POST `` (multipart/form-data) | partes `title`, `description`, `type` (enum), `createdBy`, `file`; todos `@RequestParam` obrigatórios | **201** `ConsentDocumentResponseDTO` | `:36-85` |
| 2 | POST `/{consentDocumentId}/publish` | query `updatedBy` obrigatório | 200 `ConsentDocumentResponseDTO` | `:87-116` |
| 3 | POST `/{consentDocumentId}/archive` | query `updatedBy` obrigatório | 200 `ConsentDocumentResponseDTO` | `:118-145` |
| 4 | GET `/{id}` | — | 200 `ConsentDocumentResponseDTO` | `:147-170` |
| 5 | GET `/status/{status}` | `status` ∈ `DRAFT, PUBLISHED, ARCHIVED` | 200 `List<ConsentDocumentResponseDTO>` | `:172-196` |
| 6 | GET `/type/{type}` | `type` ∈ `ConsentType` | 200 lista | `:198-222` |
| 7 | GET `/type/{type}/latest-published` | — | 200 objeto | `:224-247` |
| 8 | GET `/published` | — | 200 lista | `:249-272` |
| 9 | DELETE `/{id}` | — | **204** sem corpo | `:274-298` |

Observações:
- **Create não valida nada**: o `ConsentDocumentCreateRequestDTO` com `@NotBlank`/`@Size` (`ctrl/consentDocument/dto/request/ConsentDocumentCreateRequestDTO.java:18-29`) **não é usado** pelo controller, que recebe `@RequestParam` soltos (`ConsentDocumentController.java:49-55`). Mensagens declaradas e mortas: "Title is required", "Title must not exceed 255 characters", "Description must not exceed 500 characters", "Type is required", "CreatedBy is required".
  - `title`/`description` vazios são aceitos; acima de 255/500 chars falham no banco (`ConsentDocumentJpaEntity.java:42-46`) → 500.
  - **Qualquer exceção** dentro do `try` vira `RuntimeException("Erro ao criar documento: ...")` → 500 (`ConsentDocumentController.java:81-84`). Parte ausente (`MissingServletRequestPartException`/`MissingServletRequestParameterException`), `type` inválido e arquivo > 10MB (`res/application.yml:56-60`) ocorrem antes do método → também 500 pelo handler genérico [framework].
  - Sem validação de MIME/extensão; `mimeType` = `file.getContentType()` do cliente (`:69`).
- `updatedBy` ausente em publish/archive → `MissingServletRequestParameterException` → 500 [framework].
- Path enum inválido (`/status/FOO`, `/type/FOO`) → `MethodArgumentTypeMismatchException` → 500 [framework].

`ConsentDocumentResponseDTO` (`ctrl/consentDocument/dto/response/ConsentDocumentResponseDTO.java:17-32`): `id, title, description, version (int), status, type, createdAt, publishedAt (null em DRAFT), createdBy, updatedBy, s3Url, contentHash (sha256 hex), fileSizeBytes (long), mimeType`.
- `s3Url = "{storage.minio.endpoint}/{bucket}/{s3Key}"` ou `null` se `s3Key` nulo (`ctrl/consentDocument/mapper/ConsentDocumentAdapterMapper.java:18-20`). Em prod o endpoint é `http://minio:9000` (env `STORAGE_MINIO_ENDPOINT`, kubectl) → URL interna do cluster. `s3Key` em si não é exposto.

### 1.2 UserConsentController — base `/api/v1/user-consents` (`ctrl/userConsent/UserConsentController.java:32`)

| # | Método/Path | Body/params | Sucesso | Linhas |
|---|---|---|---|---|
| 1 | POST `/accept` | `UserConsentAcceptRequestDTO` | **201** `UserConsentResponseDTO` | `:40-84` |
| 2 | POST `/accept-all` | `UserConsentAcceptAllRequestDTO` | **201** `UserConsentAcceptAllResponseDTO` | `:86-129` |
| 3 | POST `/accept-batch` | `UserConsentAcceptBatchRequestDTO` | **201** `UserConsentAcceptAllResponseDTO` | `:131-183` |
| 4 | POST `/revoke` | `UserConsentRevokeRequestDTO` | **200** `UserConsentResponseDTO` | `:185-219` |
| 5 | GET `/{id}` | — | 200 `UserConsentResponseDTO`; isolamento → 403 (na prática 500, seção 2) | `:221-246` |
| 6 | GET `/user/{userId}` | — | 200 lista filtrada por tenant | `:248-274` |
| 7 | GET `/user/{userId}/document/{consentDocumentId}` | — | 200 lista filtrada | `:276-304` |
| 8 | GET `/user/{userId}/document/{consentDocumentId}/latest` | — | 200 objeto; isolamento 403 | `:306-333` |
| 9 | GET `/user/{userId}/document/{consentDocumentId}/version/{version}/check` | `version` Integer | 200 corpo `true`/`false` (JSON booleano cru) | `:335-357` |
| 10 | DELETE `/user/{userId}` | — | **204** | `:359-381` |

Isolamento de tenant:
- Filtro de lista: mantém `companyId == null` (legado) **ou** igual ao header (`:268-271,298-301`).
- `enforceTenantIsolation`: retorna se o consent ou o `companyId` dele for nulo; se diferente → `ResponseStatusException(FORBIDDEN, "Acesso negado: consentimento pertence a outro tenant")` (`:395-406`).

DTOs de request (validação → mensagem exata; `@Valid` no body em `:53,100,144,198`):
- `UserConsentAcceptRequestDTO` (`ctrl/userConsent/dto/request/UserConsentAcceptRequestDTO.java`):
  - `userId` UUID `@NotNull` "UserId is required" (`:19-20`).
  - `email` String `@NotNull` "Email is required" (`:22-23`) — sem `@Email`, aceita vazio.
  - `consentDocumentId` UUID `@NotNull` "ConsentDocumentId is required" (`:25-26`).
  - `version` Integer `@NotNull` "Version is required" (`:28-29`).
  - `acceptedAt` `@NotNull` "AcceptedAt is required" + formato da seção 1.0 (`:31-33`).
  - `geolocation` opcional (`:35`).
- `UserConsentAcceptAllRequestDTO`: `userId`, `email`, `acceptedAt` com as mesmas mensagens; `geolocation` opcional (`UserConsentAcceptAllRequestDTO.java:19-29`).
- `UserConsentAcceptBatchRequestDTO` (`UserConsentAcceptBatchRequestDTO.java`):
  - `userId`, `email`, `acceptedAt`, `geolocation` como acima (`:22-32`).
  - `consents` `@NotEmpty` "Consents list cannot be empty" + `@Valid` (`:34-36`).
  - Item `ConsentItemRequestDTO`: `documentId` `@NotNull` "DocumentId is required", `version` `@NotNull` "Version is required", `accepted` boolean primitivo (ausente = `false`), `contentHash` opcional (`:42-52`).
  - Erro em item aparece com chave `consents[0].documentId` no mapa `errors` [framework].
- `UserConsentRevokeRequestDTO`: `userId` "UserId is required", `consentDocumentId` "ConsentDocumentId is required", `reason` opcional (`UserConsentRevokeRequestDTO.java:17-23`).

DTOs de response:
- `UserConsentResponseDTO` (`ctrl/userConsent/dto/response/UserConsentResponseDTO.java:16-30`): `id, companyId, tenantId (sempre null), userId, email, consentDocumentId, version, status (ACCEPTED|REVOKED), acceptedAt, revokedAt, revocationReason, createdAt, ipAddress, userAgent, geolocation`.
- `UserConsentAcceptAllResponseDTO` (`UserConsentAcceptAllResponseDTO.java:18-35`): `acceptedConsents[]` + `totalAccepted` (= tamanho da lista, `ctrl/userConsent/mapper/UserConsentAdapterMapper.java:37-40`). Cada item: `id, userId, email, consentDocumentId, version, acceptedAt, createdAt, ipAddress, userAgent, geolocation` — **sem** `companyId`, `tenantId`, `status`, `revokedAt` (`UserConsentAdapterMapper.java:43-56`). Itens só dos aceites gravados; ignorados/revogados não aparecem.
- `contentHash` recebido no batch **não é persistido** (`UserConsent` não tem o campo; `svc/UserConsentCommandService.java:246-257`), apesar do system-design dizer o contrário.

### 1.3 ComplianceController — base `/api/v1/compliance` (`ctrl/compliance/ComplianceController.java:23`)

| # | Método/Path | Sucesso | Linhas |
|---|---|---|---|
| 1 | GET `/user/{userId}` | 200 `ComplianceStatusResponseDTO` | `:31-55` |
| 2 | GET `/user/{userId}/mandatory` | 200 booleano cru | `:57-79` |
| 3 | GET `/consent-types` | 200 `["TERMS_OF_USE", ...]` (12 nomes, ordem do enum) | `:81-101`; `svc/ComplianceQueryService.java:136-138` |
| 4 | GET `/consent-types/mandatory` | 200 5 nomes: `TERMS_OF_USE, PRIVACY_POLICY, LGPD_COMPLIANCE, DATA_PROCESSING, ESSENTIAL_COOKIES` | `:103-123`; `ComplianceQueryService.java:140-144` |

- Enums na resposta saem só pelo nome; `displayName`/`description` não são serializados [framework: enum Jackson padrão].
- `ComplianceStatusResponseDTO` (`ctrl/compliance/dto/response/ComplianceStatusResponseDTO.java:16-19`): `userId, compliant, consents[], missingMandatory[]` (nomes de `ConsentType`).
- `ConsentStatusDetailDTO` (`ConsentStatusDetailDTO.java:17-23`): `documentId, type, typeDisplayName` (ex. "Termos de Uso", `ctrl/compliance/mapper/ComplianceAdapterMapper.java:31`), `version, accepted, acceptedAt, mandatory`.
- Nenhum 404 real: usuário inexistente devolve 200 com `accepted=false` (Swagger diz 404, `ComplianceController.java:39`).

### 1.4 HealthController — GET `/api/v1/health` (`ctrl/health/HealthController.java:17-31`)

- 200 `{"status":"UP","service":"ms-user-consents","timestamp":<LocalDateTime.now()>}` (`:24-27`); `HashMap` → ordem das chaves não garantida [framework]. Sem header obrigatório.
- Actuator: `health, info, prometheus` (`res/application.yml:99-115`), probes ligadas (`:105-107`), `show-details: always` (`:108`). K8s usa `/actuator/health/liveness` e `/readiness` (`helm/templates/deployment.yaml:77-92`).

---

## 2. Contrato de erro (`infra/rest/GlobalExceptionHandler.java`)

Formato único: `HashMap<String,Object>` na **raiz** (não é `ProblemDetail`, não há `properties` aninhado, não há `errorCode`, `title`, `type` nem `instance`) (`:90-97`):

```json
{ "timestamp": "<OffsetDateTime.now()>", "status": 404, "error": "Not Found", "message": "..." }
```

- `timestamp`: `OffsetDateTime` serializado ISO-8601 em UTC pelo mapper do Boot (`spring.jackson.time-zone: UTC`) [framework]. Ordem das chaves não garantida (HashMap) e saída indentada.

| Exceção | Status | `error` | `message` | Linhas |
|---|---|---|---|---|
| `NotFoundException` | 404 | `Not Found` | `ex.getMessage()` | `:22-26` |
| `AlreadyExistsException` | 409 | `Conflict` | `ex.getMessage()` | `:28-32` |
| `IllegalArgumentException` | 400 | `Bad Request` | `ex.getMessage()` | `:34-38` |
| `MethodArgumentNotValidException` | 400 | `Bad Request` | `Dados de entrada inválidos` + `errors: {campo: mensagem}` (todos os campos; colisão no mesmo campo → último vence) | `:40-52` |
| `HttpMessageNotReadableException` | 400 | `Bad Request` | `Dados de entrada inválidos` | `:54-58` |
| `IllegalStateException` | **409** | `Conflict` | `ex.getMessage()` | `:60-64` |
| `MissingRequestHeaderException` | 400 | `Bad Request` | `Header obrigatório '<nome>' não foi fornecido.` (texto especial só para `X-Tenant-Id`, que nenhum endpoint usa) | `:66-78` |
| `Exception` (resto) | 500 | `Internal Server Error` | `Erro interno do servidor` | `:80-84` |

Mapeamento real por cenário (o que o cliente vê hoje):
- **Não encontrado → 500**, porque é `RuntimeException`: documento por id (`svc/ConsentDocumentQueryService.java:44-49`), latest-published (`:126-131`), publish/archive/delete de id inexistente (`svc/ConsentDocumentCommandService.java:138-139,259-260,288-289`), user-consent por id (`svc/UserConsentQueryService.java:30-31`), `/latest` sem registro (`:76-83`).
- **404 real** só no revoke: documento inexistente "Documento de consentimento não encontrado: {id}" e sem consentimento "Nenhum consentimento encontrado para o usuário e documento informados" (`svc/UserConsentCommandService.java:288-299`).
- **409** "Usuário já aceitou esta versão do documento" no `/accept` duplicado (`UserConsentCommandService.java:59-65`).
- **400** "Documentos obrigatórios não podem ser revogados individualmente sem o encerramento da conta" (`UserConsentCommandService.java:291-294`).
- **409** em transição inválida de documento: "Only DRAFT documents can be published" / "Only PUBLISHED documents can be archived" (`dom/entity/ConsentDocument.java:64-66,112-114`). No publish de não-DRAFT o fluxo quebra antes (seção 4.2).
- **403 de tenant → 500** [framework — validar]: `ResponseStatusException` é subclasse de `Exception`; o `@ExceptionHandler(Exception.class)` local tem precedência sobre o `ResponseStatusExceptionResolver` (`UserConsentController.java:403-404`; `GlobalExceptionHandler.java:80`).
- Tipo de path/header inválido, parâmetro/parte ausente, upload > 10MB → 500 [framework].

---

## 3. Domínio

### 3.1 Enums
- `ConsentDocumentStatus`: `DRAFT, PUBLISHED, ARCHIVED` (`dom/enums/ConsentDocumentStatus.java:3-7`).
- `UserConsentStatus`: `ACCEPTED, REVOKED` (`dom/enums/UserConsentStatus.java:3-6`).
- `ConsentCategory(displayName, mandatory, defaultExpirationDays, canBeRevoked)` (`dom/enums/ConsentCategory.java:7-10`):
  - `ESSENTIAL("Essencial", true, 0, true)`, `FUNCTIONAL("Funcional", true, 0, false)`, `ANALYTICS("Analytics", false, 365, true)`, `MARKETING("Marketing", false, 180, true)`.
  - `canBeRevoked` e `defaultExpirationDays` **não são usados** por nenhum fluxo (só getters, `ConsentType.java:41-47`). Não existe expiração de consentimento implementada.
- `ConsentType(displayName, description, category)` (`dom/enums/ConsentType.java:8-25`), na ordem:
  - ESSENTIAL: `TERMS_OF_USE` "Termos de Uso", `PRIVACY_POLICY` "Política de Privacidade", `LGPD_COMPLIANCE` "Conformidade LGPD".
  - FUNCTIONAL: `DATA_PROCESSING` "Processamento de Dados", `ESSENTIAL_COOKIES` "Cookies Essenciais".
  - ANALYTICS: `ANALYTICS` "Analytics", `PERFORMANCE_COOKIES` "Cookies de Performance".
  - MARKETING: `MARKETING_EMAIL` "Email Marketing", `MARKETING_SMS` "SMS Marketing", `MARKETING_PUSH` "Push Notifications", `THIRD_PARTY_SHARING` "Compartilhamento com Terceiros", `PROFILING` "Perfilamento".
  - `isMandatory()` = `category.isMandatory()` (`:37-39`).

### 3.2 `ConsentDocument` (imutável, `dom/entity/ConsentDocument.java`)
- Campos: `id, title, description, version, status, type, createdAt, publishedAt, createdBy, updatedBy, s3Key, contentHash, fileSizeBytes, mimeType` (`:15-30`).
- `create(...)`: novo UUID, `DRAFT`, `createdAt = LocalDateTime.now()`, `publishedAt = null`, `updatedBy = createdBy` (`:33-60`).
- `publish(updatedBy[, newS3Key])`: exige `DRAFT`, senão `IllegalStateException`; seta `PUBLISHED`, `publishedAt = now` (`:63-108`).
- `archive(updatedBy)`: exige `PUBLISHED`; mantém `publishedAt` (`:111-132`).
- O auto-arquivamento no publish **não** usa `archive()`: reconstrói via `fromJpa` com `ARCHIVED` (`svc/ConsentDocumentCommandService.java:185-201`).
- Transições: DRAFT→PUBLISHED, PUBLISHED→ARCHIVED. ARCHIVED é final. DELETE físico em qualquer status.

### 3.3 `UserConsent` (imutável, `dom/entity/UserConsent.java`)
- Campos: `id, companyId, tenantId, userId, email, consentDocumentId, version, status, acceptedAt, revokedAt, revocationReason, createdAt, ipAddress, userAgent, geolocation` (`:14-28`).
- `accept(...)`: novo UUID, `ACCEPTED`, `createdAt = LocalDateTime.now()` (`:31-60`).
- `revoke(reason)`: mesma linha (mesmo `id`), `REVOKED`, `revokedAt = now`, `revocationReason = reason`; **sem** checar status atual (`:77-95`). É um UPDATE, não uma nova linha.
- `fromJpa` com `status == null` → `ACCEPTED` (`:123`).
- Invariantes que existem só no serviço: aceite único por (user, doc, version) via `exists` (seção 4.4); revogação proibida para tipo mandatory (seção 4.7). Não há checagem de que o documento existe, está PUBLISHED ou de que `version` bate com a do documento no `/accept`.

---

## 4. Use cases passo a passo

Transações: os `*UseCaseService` são `@Transactional` (escrita) ou `@Transactional(readOnly = true)` (leitura) (`svc/ConsentDocumentUseCaseService.java:26-95`, `svc/UserConsentUseCaseService.java:26-90`, `svc/ComplianceUseCaseService.java:21-31`). `listAllConsentTypes`/`listMandatoryConsentTypes` sem transação (`ComplianceUseCaseService.java:33-41`). Os `@LogOperation` ficam nos `*CommandService` e são chamados de outro bean → **AOP intercepta, não há auto-invocação** nos fluxos HTTP (`ConsentDocumentUseCaseService.java:29`, `UserConsentUseCaseService.java:29`).

### 4.1 Criar documento (`svc/ConsentDocumentCommandService.java:39-109`)
`@LogOperation(operation="CREATE_CONSENT_DOCUMENT", description="Criando novo documento de consentimento: {command.title}", audit=true, auditAction="CREATE", auditEntityType="CONSENT_DOCUMENT")` (`:39-45`).
1. `nextVersion` = maior `version` entre **todos** os documentos do tipo (qualquer status) + 1, ou 1 (`:111-126`). Lê o banco direto (sem cache). Sem lock → duas criações concorrentes geram a mesma versão.
2. Lê o arquivo inteiro em memória; `contentHash = sha256Hex(bytes)` (`:55-58`).
3. `s3Key = "drafts/{type.lower}_v{version}_{uuidAleatório}.{ext}"`; `ext` = trecho após o último `.` do nome original, ou `bin` (`:61-66,346-351`).
4. Upload no bucket `keepguard-consents` com o `fileSize` e `contentType` do cliente (`:69-75`).
5. Cria `DRAFT` e salva (`:78-91`).
6. Invalida cache: `type:{type}`, `status:draft`, `all_published` (`:308-313`).
7. Métrica `consent_document_created_total{entity_id,type}` (`:97-98`).
8. Qualquer exceção → `RuntimeException("Falha ao criar ConsentDocument: ...")` (`:102-108`).

### 4.2 Publicar (`ConsentDocumentCommandService.java:128-247`)
`@LogOperation(operation="PUBLISH_CONSENT_DOCUMENT", description="Publicando documento de consentimento: {consentDocumentId}", audit=true, auditAction="PUBLISH", auditEntityType="CONSENT_DOCUMENT")` (`:128-134`).
1. Busca o documento; inexistente → `RuntimeException` 500 (`:138-139`).
2. `statObject` no MinIO; ausente → `RuntimeException("Arquivo não encontrado no storage: ...")` 500 (`:142-144`).
3. Para cada documento PUBLISHED do mesmo tipo (`:147-150`): download de `published/...`, upload em `s3Key.replace("published/","archived/")`, delete do original; falha → `RuntimeException` (`:156-182`); salva como `ARCHIVED` com `updatedBy` do publicador e novo `s3Key` (`:185-201`).
4. Move o próprio arquivo `drafts/` → `published/` (download + upload + delete) (`:206-230`).
5. `document.publish(updatedBy, newS3Key)` e salva (`:233-234`). Só aqui a regra "só DRAFT" é checada.
6. Invalida cache: `id:{id}`, `type:{type}`, `status:published`, `all_published`, `latest_published:{type}` (`:315-324`). **Não** invalida `id:` nem `status:archived` dos documentos auto-arquivados, nem `status:draft`, nem compliance de usuários (comentário `:322-323`).
7. `publishManifest(null)` (seção 7.3) (`:240`).
8. Métrica `consent_document_published_total{entity_id,type}` (`:243-244`).
- **Publicar documento já PUBLISHED**: o passo 3 o inclui na própria lista, move o arquivo para `archived/` e o salva `ARCHIVED`; o passo 4 tenta baixar `published/...` (já apagado) → 500. O banco faz rollback, mas o MinIO fica alterado.
- **Publicar ARCHIVED (auto-arquivado)**: o `s3Key` já aponta para `archived/`, o stat passa, o passo 4 copia para a mesma chave (o `replace` não muda nada) e depois apaga essa chave → o passo 5 lança `IllegalStateException` → 409, com o arquivo **apagado** do MinIO. ARCHIVED manual (arquivo ainda em `published/`) tem o mesmo efeito: o passo 3 não o inclui (status não é PUBLISHED), mas arquiva os outros publicados do tipo, e o passo 4 copia e apaga a própria chave → 409 com arquivo apagado [inferido do código, validar].

### 4.3 Arquivar / Deletar documento
- Arquivar (`:249-276`), `@LogOperation(operation="ARCHIVE_CONSENT_DOCUMENT", description="Arquivando documento de consentimento: {consentDocumentId}", auditAction="ARCHIVE", auditEntityType="CONSENT_DOCUMENT")`:
  1. Busca (500 se não existe); `archive()` (409 se não PUBLISHED); salva. **Não move** o arquivo no MinIO (continua em `published/`).
  2. Invalida `id`, `type`, `status:archived`, `all_published`, `latest_published:{type}` (`:326-333`) — `status:published` **não** é invalidado.
  3. `publishManifest(null)`; métrica `consent_document_archived_total{entity_id,type}` (`:269-273`).
- Deletar (`:278-305`), `@LogOperation(operation="DELETE_CONSENT_DOCUMENT", description="Deletando documento de consentimento: {consentDocumentId}", auditAction="DELETE", auditEntityType="CONSENT_DOCUMENT")`:
  1. Busca (500 se não existe); apaga o objeto no MinIO se `s3Key != null`; `deleteById` (`:288-297`).
  2. Se houver `user_consents` apontando para o documento, a FK criada pelo `@ManyToOne` (seção 6) faz o commit falhar → 500, **com o arquivo já apagado** [framework — validar].
  3. Invalida `id`, `type`, `status:{status}`, `all_published`, `latest_published` se PUBLISHED (`:335-344`). Não republica o manifesto.
  4. Métrica `consent_document_deleted_total{entity_id,type}`.

### 4.4 Aceitar (`svc/UserConsentCommandService.java:41-91`)
`@LogOperation(operation="ACCEPT_USER_CONSENT", description="Registrando aceite de consentimento para usuário: {command.userId}", audit=true, auditAction="ACCEPT", auditEntityType="USER_CONSENT")`.
1. `existsByUserIdAndConsentDocumentIdAndVersion` (ignora status e tenant) → se sim: métrica `user_consent_business_errors_total{error_code=ALREADY_ACCEPTED,operation=accept}` e 409 (`:53-65`). Revogado da mesma versão também bloqueia.
2. `UserConsent.accept(companyId=header, tenantId=null, ...)`, salva (`:68-81`).
3. Invalida `user:{userId}`, `latest:{userId}:{docId}`, `accepted:{userId}:{docId}:{version}`, `compliance_cache:user:{userId}` (`:94-112`).
4. Métrica `user_consent_accepted_total{status=SUCCESS}`.

### 4.5 Aceitar todos (`:114-198`)
`@LogOperation(operation="ACCEPT_ALL_USER_CONSENTS", description="Registrando aceite de todos os documentos publicados para usuário: {command.userId}", auditAction="ACCEPT_ALL", auditEntityType="USER_CONSENT")`.
1. `findAllPublished()` do banco (sem cache). Vazio → métrica `user_consent_accept_all_total{status=NO_DOCUMENTS}` e lista vazia (`:125-134`).
2. Para cada documento: se já existe aceite da (doc, version) → ignora; senão grava aceite com a versão do documento (`:140-174`).
3. Invalida caches (erros engolidos) (`:322-347`); métricas `user_consent_accept_all_total`, `user_consent_accepted_batch_total` e, se houve ignorados, `user_consent_ignored_batch_total{status=IGNORED}` (`:180-188`).

### 4.6 Aceite seletivo em lote (`:200-276`)
`@LogOperation(operation="ACCEPT_BATCH_USER_CONSENTS", description="Registrando aceite seletivo em lote de consentimentos para usuário: {command.userId}", auditAction="ACCEPT_BATCH", auditEntityType="USER_CONSENT")`.
Para cada item:
- `accepted=false` (`:215-230`): busca o último consentimento; se existir e não for REVOKED e o documento existir e **não** for mandatory → revoga com motivo `"Revogado via atualização de preferências em lote"` e invalida cache. Mandatory desmarcado é ignorado em silêncio.
- `accepted=true` (`:232-263`): ignora só se já existe a versão **e** o último não está REVOKED; caso contrário grava **nova linha** (reaceite após revogação). Não valida se o documento existe/está publicado nem se a versão confere. `contentHash` é descartado. Métrica `user_consent_accepted_total` por item.
- Invalidação final igual à 4.5 (`:266`). Não emite métricas de lote.

### 4.7 Revogar (`:278-320`)
`@LogOperation(operation="REVOKE_USER_CONSENT", description="Revogando consentimento para usuário: {command.userId}, documento: {command.consentDocumentId}", auditAction="REVOKE", auditEntityType="USER_CONSENT")`.
1. Documento inexistente → 404; tipo mandatory → 400 (`:288-294`).
2. Último consentimento (por `createdAt DESC`) inexistente → 404 (`:296-299`).
3. Já REVOKED → devolve o atual (200 idempotente, sem métrica) (`:301-305`).
4. `reason` vazio/branco → `"Revogado pelo usuário"` (`:307-309`); revoga, salva, invalida, métrica `user_consent_revoked_total{status=SUCCESS}` (`:311-316`).
- O `companyId` do comando não é usado: revoga consentimento de qualquer tenant.

### 4.8 Apagar todos do usuário (`:349-378`)
`@LogOperation(operation="DELETE_ALL_USER_CONSENTS", description="Deletando todos os consentimentos para usuário: {userId}", auditAction="DELETE_ALL", auditEntityType="USER_CONSENT")` + `@Transactional` próprio (`:356`).
1. `findByUserId`; vazio → retorna (idempotente) (`:361-365`).
2. `deleteAllByUserId` → ver risco 2 do resumo (sem `@Modifying`).
3. Invalida `user:{userId}` e compliance; **não** apaga `latest:*`/`accepted:*` desse usuário (`:380-392`) → caches de PII sobrevivem até o TTL (1h).
4. Métrica `user_consent_deleted_all_total{status=SUCCESS}`. Ignora tenant.

### 4.9 Consultas
- Documento por id: cache `id:` → senão banco + cacheia; métricas `consent_document_queries_total{query_type=GET_BY_ID,status=CACHE_HIT|SUCCESS}`, `consent_document_not_found_total{entity_id,operation=get_by_id}` (`svc/ConsentDocumentQueryService.java:30-59`).
- Por status / por tipo: cache de lista, sem métricas (`:61-105`). Lista vazia é cacheada como `[]`.
- Latest published por tipo: cache → banco (`ORDER BY publishedAt DESC`, primeiro) (`:112-141`; `infra/persistence/ConsentDocumentRepositoryAdapter.java:61-66`).
- Todos publicados: cache `all_published` → banco `ORDER BY type, version DESC` (`:143-169`; `infra/persistence/spring/ConsentDocumentSpringRepository.java:27-28`). `type` é string → ordem alfabética do nome.
- `findByTypeAndStatus` existe na porta mas nenhum endpoint usa (`ConsentDocumentUseCaseService.java:75-81`).
- User-consent por id: banco direto, sem cache (`svc/UserConsentQueryService.java:28-32`).
- Por usuário: cache `user:{userId}` com **todos os tenants**; o filtro de tenant é no controller (`:34-55`).
- Por usuário+documento: banco direto (`:57-60`).
- Latest: cache `latest:` → banco; métricas `user_consent_queries_total{query_type=GET_LATEST_BY_USER_AND_DOCUMENT,...}`, `user_consent_not_found_total` (`:62-93`).
- `hasAccepted`: cache `accepted:` (string `"true"`/`"false"`) → `exists...` (ignora status REVOKED: revogado continua "aceito") (`:95-119`).

### 4.10 Compliance (`svc/ComplianceQueryService.java`)
- `checkUserCompliance` (`:30-104`):
  1. Cache `compliance_cache:user:{userId}`.
  2. Documentos `findByStatus(PUBLISHED)` (banco); consentimentos do usuário de **todos os tenants**.
  3. Ativo = `status == ACCEPTED && revokedAt == null`, indexado por `consentDocumentId` (`:52-58`). Não compara versão (cada versão é um documento com id próprio, então na prática vale).
  4. Detalhe por documento publicado: `accepted`, `acceptedAt` do primeiro ativo, `mandatory = type.isMandatory()` (`:61-77`).
  5. `missingMandatory` = nomes de tipo mandatory não aceitos; `compliant = missingMandatory.isEmpty()` → usuário é compliant se **não existir** documento publicado de tipo obrigatório (`:80-85`).
  6. Cacheia 1h; métricas `compliance_queries_total`, `compliance_status_total{compliant}` (`:95-101`).
  7. Publicar documento novo **não** invalida esse cache → até 1h de "compliant" desatualizado.
- `hasMandatoryConsents` (`:106-134`): para cada um dos 5 tipos mandatory, pega o latest published; sem documento → pula; sem aceite `exists(user, doc, version)` → `false`. Não usa cache; considera revogado como aceito (usa `exists`).
- Os dois métodos podem divergir: `checkUserCompliance` exclui revogados e olha todos os publicados; `hasMandatoryConsents` só o latest de cada tipo e inclui revogados.

---

## 5. Auditoria (`@LogOperation`, lib `lib/logging/aspect/LoggingAspect.java:69-116`)

| operation | auditAction | auditEntityType | Arquivo:linha |
|---|---|---|---|
| CREATE_CONSENT_DOCUMENT | CREATE | CONSENT_DOCUMENT | `svc/ConsentDocumentCommandService.java:39-45` |
| PUBLISH_CONSENT_DOCUMENT | PUBLISH | CONSENT_DOCUMENT | `:128-134` |
| ARCHIVE_CONSENT_DOCUMENT | ARCHIVE | CONSENT_DOCUMENT | `:249-255` |
| DELETE_CONSENT_DOCUMENT | DELETE | CONSENT_DOCUMENT | `:278-284` |
| ACCEPT_USER_CONSENT | ACCEPT | USER_CONSENT | `svc/UserConsentCommandService.java:41-47` |
| ACCEPT_ALL_USER_CONSENTS | ACCEPT_ALL | USER_CONSENT | `:114-120` |
| ACCEPT_BATCH_USER_CONSENTS | ACCEPT_BATCH | USER_CONSENT | `:200-206` |
| REVOKE_USER_CONSENT | REVOKE | USER_CONSENT | `:278-284` |
| DELETE_ALL_USER_CONSENTS | DELETE_ALL | USER_CONSENT | `:349-355` |

- Todos `audit=true`. Publica `SUCCESS` após retorno ou `FAILURE` em exceção (`LoggingAspect.java:88-114`).
- **Placeholders `{command.xxx}` não são resolvidos**: o contexto só tem chaves por nome de parâmetro simples (UUID/String/Number/Boolean) e campos de DTO `username, deviceId, codeUser, companyId, userId, challengeSessionId` (`LoggingAspect.java:195-226,267-274`). Logo `reason` sai literal, ex. "Registrando aceite de consentimento para usuário: {command.userId}". `{consentDocumentId}` e `{userId}` (parâmetros diretos) são substituídos.
- `resource.id` (`LoggingAspect.java:231-265`): publish/archive → `getId()` do documento retornado; create → `getId()` do retorno; accept/revoke → id do `UserConsent`; delete documento, acceptAll, acceptBatch, deleteAll → `null` (retorno void ou DTO sem `getId`; `consentDocumentId`/`userId` não contam como `entity_id`, `:295-301`).
- Evento (`lib/audit/AuditContextCollector.java:15-84`): `tenantId`/`companyId` vêm de `X-Company-Id`; `actor.codeUser` de MDC `codeUser`/header `X-User-ID`; `clientIp` do primeiro `X-Forwarded-For` ou remoteAddr; `reason` = descrição truncada em 512.
- Publicação assíncrona (pool 2-4, fila 500, descarta quando cheia) no exchange topic `KEEPGUARD_AUDIT_EXCHANGE=srv-audit-exchange-prod`, routing `audit.event`, source `ms-user-consents` (`lib/audit/RabbitAuditConfiguration.java:22-44`; `res/application.yml:152-157`; env ao vivo).
- Erasure via consumer: chama `UserConsentCommandService.deleteAllByUserId` pelo proxy (`infra/messaging/UserErasureConsumer.java:38`) → evento publicado, mas sem request HTTP → `companyId`/`tenantId` nulos, `actor.type = ANONYMOUS` (`AuditContextCollector.java:53`).
- Operações de leitura e revogação automática dentro do `accept-batch` não geram evento próprio.

---

## 6. Persistência (schema `ms_user_consents`, `res/application.yml:48`)

- DDL: `ddl-auto: update` (`res/application.yml:43`), sem override em local/dev/prod (`res/application-local.yml`, `application-dev.yml`, `application-prod.yml`); testes `create-drop` em H2 (`src/test/resources/application-test.yml:9-12`). Sem Flyway/Liquibase (`pom.xml`). Pool Hikari 10/min 5 (`res/application.yml:20-26`).
- Banco `keepguard_api_db`, compartilhado com outros serviços (`helm/templates/deployment.yaml:37-38`).

### 6.1 `consent_documents` (`infra/persistence/entity/ConsentDocumentJpaEntity.java:16-83`)
| Coluna | Tipo/limite | Null | Linha |
|---|---|---|---|
| id | uuid PK | não | `:24-26` |
| company_id, tenant_id | uuid | sim (sempre null) | `:36-40` |
| title | varchar(255) | não | `:42-43` |
| description | varchar(500) | sim | `:45-46` |
| version | int | não | `:48-49` |
| status | varchar(16) enum string | não | `:51-53` |
| type | varchar(50) enum string | não | `:55-57` |
| created_at | timestamp | não | `:59-60` |
| published_at | timestamp | sim | `:62-63` |
| created_by, updated_by | varchar(255) | sim | `:65-69` |
| s3_key | varchar(512) | sim | `:72-73` |
| content_hash | varchar(64) | sim | `:75-76` |
| file_size_bytes | bigint | sim | `:78-79` |
| mime_type | varchar(100) | sim | `:81-82` |

- Sem unique em (type, version) e sem índice declarado.

### 6.2 `user_consents` (`infra/persistence/entity/UserConsentJpaEntity.java:15-88`)
| Coluna | Tipo/limite | Null | Linha |
|---|---|---|---|
| id | uuid PK | não | `:29-31` |
| company_id, tenant_id | uuid | sim | `:41-45` |
| user_id | uuid | não | `:47-48` |
| email | varchar(255) | não | `:50-51` |
| consent_document_id | uuid | não; FK implícita para `consent_documents.id` via `@ManyToOne` (`:85-87`) [framework] | `:53-54` |
| version | int | não | `:56-57` |
| status | varchar(20), default Java `ACCEPTED` | não | `:59-62` |
| accepted_at | timestamp | não | `:64-65` |
| revoked_at | timestamp | sim | `:67-68` |
| revocation_reason | varchar(255) | sim | `:70-71` |
| created_at | timestamp | não | `:73-74` |
| ip_address | varchar(45) | sim | `:76-77` |
| user_agent | varchar(512) | sim | `:79-80` |
| geolocation | varchar(100) | sim | `:82-83` |

- Índice `idx_user_consents_company_user_doc (company_id, user_id, consent_document_id)` (`:19-21`) — com `update` ele só é criado se a tabela/índice não existir [framework]. Nenhuma unique.
- `Persistable.isNew()` = `isNew || createdAt == null` (`:36-39`), mas o `@Builder` ignora o inicializador `isNew = true` (sem `@Builder.Default`) → `isNew=false` → todo `save` vira `merge` (SELECT + INSERT/UPDATE) [framework]. Mesmo padrão em `ConsentDocumentJpaEntity.java:28-34`.
- Timestamps `LocalDateTime` (sem zona) gerados por `LocalDateTime.now()` no fuso da JVM.

### 6.3 Queries (`infra/persistence/spring/*`)
- Derivadas: `findByStatus`, `findByType`, `findByTypeAndStatus` (`ConsentDocumentSpringRepository.java:18-22`); `findByUserId`, `findByUserIdAndConsentDocumentId`, `existsByUserIdAndConsentDocumentIdAndVersion` (`UserConsentSpringRepository.java:16-26`). Sem ORDER BY nas derivadas → ordem indefinida.
- JPQL: latest published (`ConsentDocumentSpringRepository.java:24-25`), all published (`:27-28`), latest por user+doc (`UserConsentSpringRepository.java:20-24`, risco 3), delete por usuário (`:28-29`, risco 2).
- Nenhuma paginação, nenhum filtro por `company_id` no SQL.

---

## 7. MinIO (`infra/storage/MinIOStorageAdapter.java`, `infra/config/MinIOConfig.java`)

### 7.1 Configuração
- Endpoint/credenciais por `storage.minio.*` (`res/application.yml:63-74`); prod usa env `STORAGE_MINIO_ENDPOINT=http://minio:9000` (kubectl) e credenciais do YAML (`res/application-prod.yml:21-25`) — **credenciais fixas no repositório**.
- Bucket `keepguard-consents` (`res/application.yml:69`); `keepguard-avatars` declarado e não usado (`:70`). As propriedades `storage.minio.paths.*` (`:71-74`) **não são lidas**: os prefixos `drafts/`, `published/`, `archived/` estão fixos no código (`svc/ConsentDocumentCommandService.java:61,158,206`).
- Todo upload chama `bucketExists` e cria o bucket se faltar, sem política (`MinIOStorageAdapter.java:124-145`). Nenhum código define política pública; o acesso anônimo ao manifesto depende de configuração externa do MinIO [verificar no cluster].
- `generatePresignedUrl` existe e não é usado (`:104-122`).
- Erros do MinIO viram `RuntimeException` (`:46-49,79-82,98-101`); `fileExists` devolve `false` em qualquer erro (`:62-65`).

### 7.2 Layout de objetos
- `drafts/{type_lower}_v{N}_{uuid}.{ext}` na criação (`ConsentDocumentCommandService.java:61-66`).
- `published/...` ao publicar (`:206`); `archived/...` só quando outro documento do tipo é publicado (`:158`). O arquivamento manual não move (seção 4.3).
- Mover = download + upload + delete, sem cópia server-side e sem rollback.

### 7.3 `terms-manifest.json` (`svc/TermsManifestPublisherService.java:37-104`)
- Disparado após publish e archive (`ConsentDocumentCommandService.java:240,269`); não após delete. Sempre com `companyId=null` → chave `public-legal/global/terms-manifest.json` (`:85-87`). Content-type `application/json` (`:89-95`). Sobrescreve sem versionamento de objeto.
- Falha ao gerar/enviar é logada e engolida (`:98-101`); como roda dentro da transação, o publish continua.
- Fonte: `findAllPublished()` do banco (`:40`).
- Formato (pretty printer, `:81`; DTO `app/dto/manifest/TermsManifestDTO.java:19-46`), ordem dos campos = ordem de declaração [framework]:

```json
{
  "companyId" : null,
  "version" : "3.0",
  "publishedAt" : "2026-10-04T13:05:07.123Z",
  "effectiveAt" : "2026-10-04T13:05:07.123Z",
  "gracePeriodDays" : 15,
  "documents" : [ {
    "id" : "uuid", "type" : "TERMS_OF_USE", "category" : "ESSENTIAL", "title" : "...",
    "version" : 3, "mandatory" : true, "contentHash" : "sha256hex",
    "url" : "http://minio:9000/keepguard-consents/published/terms_of_use_v3_<uuid>.pdf"
  } ]
}
```
  - `version` = `"{maior versão entre os publicados}.0"`, ou `"1.0"` sem publicados (`:63-68`).
  - `publishedAt = effectiveAt = LocalDateTime.now()` formatado com `'Z'` literal (`:24-28,69-75`): a hora é a da JVM, não necessariamente UTC.
  - `gracePeriodDays` fixo 15 (`:76`), não aplicado em nenhuma regra do backend.
  - `mandatory` = categoria ESSENTIAL (`:44`) — divergência do resumo, item 5.
  - Ordenação: mandatory primeiro, depois `title` (`:58-59`).
- Leitor conhecido: backoffice, a cada 7 dias, compara `doc.version` com o mapa em localStorage (`frontend/backoffice/src/services/termsSyncService.ts:60-129`).

---

## 8. Redis (`infra/redis/*`, `infra/config/RedisConfig.java`)

- `StringRedisTemplate` com serializer string em chave e valor (`RedisConfig.java:12-22`); valores são JSON do `ObjectMapper` do Boot (datas ISO, `LocalDateTime` sem zona) [framework].
- Prod: profile ativo vem do ConfigMap `keepguard-config` (kubectl: `SPRING_PROFILES_ACTIVE` via configMapKeyRef) — no Helm do repo é `local` (`helm/values.yaml:16`) [verificar o valor do ConfigMap]. Com `local`: Redis standalone `SPRING_DATA_REDIS_HOST=redis:6379` (env ao vivo). Com `prod`/`dev`: cluster de 6 nós (`res/application-prod.yml:6-9`). Timeout 3s (`res/application.yml:34`).
- Prefixos e TTL (`res/application.yml:77-86`); prefixo sem `:` final (`replaceAll(":+$","")`, ex. `ConsentDocumentCacheService.java:282-287`):

| Chave | Valor | TTL | Linha |
|---|---|---|---|
| `consent_doc_cache:id:{uuid}` | `ConsentDocumentViewDTO` JSON (inclui `s3Key`) | 86400 s | `infra/redis/ConsentDocumentCacheService.java:289-291` |
| `consent_doc_cache:latest_published:{type_lower}` | ViewDTO | 86400 | `:293-295` |
| `consent_doc_cache:all_published` | lista | 86400 | `:297-299` |
| `consent_doc_cache:status:{status_lower}` | lista | 86400 | `:301-303` |
| `consent_doc_cache:type:{type_lower}` | lista | 86400 | `:305-307` |
| `user_consent_cache:latest:{userId}:{docId}` | `UserConsentViewDTO` (PII: email, IP, UA) | 3600 | `infra/redis/UserConsentCacheService.java:194-196` |
| `user_consent_cache:accepted:{userId}:{docId}:{version}` | `"true"`/`"false"` | 3600 | `:198-200` |
| `user_consent_cache:user:{userId}` | lista de ViewDTO | 3600 | `:202-204` |
| `compliance_cache:user:{userId}` | `ComplianceStatusViewDTO` | 3600 | `infra/redis/ComplianceCacheService.java:148-150` |
| `compliance_cache:user:{userId}:type:{type}` | — (métodos existem, ninguém chama) | 3600 | `:152-154` |

- Leitura com valor ausente/branco → `null` (cache miss). Erro de desserialização → `null` (ex. `ConsentDocumentCacheService.java:43-50`).
- `clearAll` usa `KEYS prefix:*` (`:241-254`) e não é chamado por ninguém.
- Invalidação: seções 4.1-4.8. Lacunas: auto-arquivados no publish, `status:published` no archive, `status:draft` no publish, compliance em publish/archive, `latest`/`accepted` no delete-all.
- Chaves não são compartilhadas com outros serviços [verificar com grep nos BFFs se algum lê `user_consent_cache`].

### 8.1 Resilience4j
- Anotações `@CircuitBreaker(name="redisCache")` em todos os métodos e `@Retry(name="redisCache")` nos `get*` (ex. `ConsentDocumentCacheService.java:37-38,54,66`). Starter Spring Boot 3 + AOP presentes (`pom.xml:58-61,123-127`) → instâncias do YAML **são aplicadas** (ao contrário do ms-auth): CB `slidingWindowSize 10, failureRateThreshold 50, waitDurationInOpenState 60s, permittedNumberOfCallsInHalfOpenState 3, slowCall 5000ms/50%`; retry `maxAttempts 2, waitDuration 1s, exponentialBackoffMultiplier 2` (sem `enableExponentialBackoff`, então o multiplicador é ignorado [framework]) (`res/application.yml:117-144`).
- Porém cada método captura `Exception` internamente (ex. `:47-50,60-62`) → o aspecto nunca vê falha: CB nunca abre, retry nunca repete, fallbacks (`:257-280`) nunca rodam. Só chamadas lentas (> 5s, impossível com timeout de 3s) contariam. Efeito real: com Redis fora, cada operação de cache espera até 3s e segue.
- Indicador de saúde de CB ligado (`res/application.yml:113-115,130`) — sempre CLOSED.

---

## 9. RabbitMQ

- Consumer `UserErasureConsumer` (`infra/messaging/UserErasureConsumer.java:24-44`):
  - Fila `ms.user-consents.user-erasure` durável, exchange `keepguard-events-exchange` tipo topic, routing `user.erasure.requested`, todos sobrescrevíveis por `keepguard.events.*` (`:26-28`). Declarados pelo próprio listener (`@QueueBinding`), sem argumentos: **sem DLQ, sem TTL, sem x-queue-type** [framework].
  - Ack automático do container (default `AUTO`) e concorrência padrão 1 [framework]. Toda exceção é capturada e logada (`:41-43`) → mensagem sempre confirmada; falha = perda.
  - Payload: `String` do corpo; lê `userId` (`json.path("userId").asText()`); vazio → no-op silencioso; UUID inválido → exceção engolida (`:34-40`).
  - Publicador: ms-user, JSON `application/json`, persistente, header `X-Event-Type: user.erasure.requested` (`../ms-user/src/main/java/com/keepguard/ms_user/infrastructure/messaging/UserErasureEventPublisher.java:42-48`). Conversão `byte[]`→`String` feita pelo Spring AMQP [framework].
  - Efeito: `deleteAllByUserId` (seção 4.8), hoje provavelmente falhando (risco 2).
- Saída: só auditoria (seção 5). Conexão `SPRING_RABBITMQ_HOST=rabbitmq-service:5672` (env ao vivo; `res/application.yml:27-31`).

---

## 10. Infra transversal

- **Profiles**: default `local` (`res/application.yml:11-12`, `pom.xml:232-241`); Helm `local` (`helm/values.yaml:16`); ao vivo via ConfigMap `keepguard-config` [verificar valor]. Diferenças: `local` → porta 8586 se `SERVER_PORT` não vier, Redis standalone, MinIO `localhost` (sobrescrito por env); `prod` → Redis cluster, logs JSON+Logstash, exchange de audit prod (`res/application-prod.yml`). O env `SERVER_PORT=8086` e `STORAGE_MINIO_ENDPOINT` mascaram as diferenças de porta/MinIO.
- **Correlation**: `CorrelationIdFilter` (`@Order(1)`) lê `X-Correlation-ID` ou gera UUID, devolve no header de resposta e guarda num `ThreadLocal` próprio (`infra/filter/CorrelationIdFilter.java:23-49`; `infra/context/CorrelationContext.java:11-43`). **Não grava no MDC** → `%X{correlationId}` dos patterns fica vazio e a auditoria gera outro UUID (`lib/audit/AuditContextCollector.java:18-24`, que lê MDC ou o header direto — o header ainda funciona se o cliente mandar).
- **Logging** (`res/logback-spring.xml`): `local,test` → console legível (`:36-55`); `dev` → console (`:58-78`); `prod` → JSON manual + Logstash TCP (`:81-115`). Em todos, `LoggingService`, `MetricsAspect` e `StructuredLogger` da lib estão `OFF`. O JSON de prod não escapa aspas da mensagem (`:8`).
- **Métricas**: `MetricsAdapter` delega ao `MetricsService` da lib (`infra/metrics/MetricsAdapter.java:15-25`); `MetricsConfig` importado (`MsUserConsentsApplication.java:11`). Nomes de counters citados nas seções 4.x (prefixo/tag padrão da lib em `03-libs.md`). Tags `entity_id` com UUID em `consent_document_*_total` (alta cardinalidade, `svc/ConsentDocumentCommandService.java:97-98`).
- **Scan da lib**: `scanBasePackages` inclui `com.keepguard.lib_common` (`MsUserConsentsApplication.java:9`) → aspects de logging/métricas e config de auditoria da lib ativos.
- **JVM**: `-Xms512m -Xmx1024m` (`Dockerfile:29`); porta exposta 8086 (`Dockerfile:26`).
- `KEEPGUARD_VALIDATION_MODERATION_ENABLED=false` é injetado (`helm/templates/deployment.yaml:73-74`) e não é lido por nada no serviço.

---

## 11. Testes Java (spec de regressão) — `src/test/java/com/keepguard/ms_user_consents/`

Arquivos: `MsUserConsentsApplicationTest`, `ConsentDocumentControllerTest`, `UserConsentControllerTest`, `ComplianceQueryServiceTest`, `ConsentDocumentCommandServiceTest`, `ConsentDocumentQueryServiceTest`, `UserConsentCommandServiceTest`, `UserConsentQueryServiceTest`, `UserConsentUseCaseServiceTest`, `ConsentDocumentTest`, `UserConsentTest`, `ConsentDocumentCacheServiceTest`, `UserConsentCacheServiceTest`.

- Todos unitários com Mockito/MockMvc standalone (ex. `UserConsentCommandServiceTest.java:32-60`; `UserConsentControllerTest.java:35-56`). MockMvc standalone **não registra** o `GlobalExceptionHandler`, então o contrato de erro não é testado.
- Não há teste de repositório/JPA: os riscos 2 e 3 não são cobertos.
- Casos de `UserConsentCommandServiceTest` (lido integralmente): aceite OK + invalidação de 4 chaves (`:88-110,160-176`); duplicado → mensagem "Usuário já aceitou esta versão do documento" (`:112-124`); aceite sem IP/UA/geo (`:126-158`); accept-all com 2 docs, ignorando já aceitos, vazio, invalidação e métricas (`:178-374`); revogar opcional (`:376-410`); revogar obrigatório → `IllegalArgumentException` (`:412-433`). Nenhum teste de `acceptBatch` ou `deleteAllByUserId`.
- `application-test.yml` desliga RabbitAutoConfiguration e auditoria e usa chaves `minio.*` que o código não lê (`src/test/resources/application-test.yml:22-30,47-49`).
- Demais arquivos de teste: [não lidos linha a linha — portar os casos ao escrever os testes Go].

---

## 12. Comportamento atual estranho ou bugs (não corrigir na migração sem decisão)

### Segurança e LGPD
- Sem autenticação nenhuma; qualquer pod do cluster (ou quem alcançar o Service) cria/publica/apaga termos e lê PII de consentimento (seção 1.0).
- Erasure provavelmente quebrada e silenciosa (risco 2); delete-all deixa caches `latest`/`accepted` com PII por até 1h (seção 4.8).
- `revoke`, `hasAccepted` e `DELETE /user/{id}` ignoram tenant; `GET /user/{id}` cacheia todos os tenants juntos (seções 4.7, 4.9).
- Credenciais do MinIO e do Postgres em texto no YAML (`res/application.yml:16-18,66-67`).

### Erros
- Not-found como 500; 403 de tenant como 500; validação do create inexistente; tipo inválido em path/header como 500 (seção 2).

### Regras de negócio
- Divergência `mandatory` ESSENTIAL × `isMandatory()` ESSENTIAL+FUNCTIONAL (risco 5). Também `FUNCTIONAL.canBeRevoked=false` mas `ESSENTIAL.canBeRevoked=true` (`ConsentCategory.java:7-8`) — o flag não é usado; a regra real bloqueia os dois.
- `/accept` bloqueia reaceite após revogação (409), mas `/accept-batch` permite e cria segunda linha, que depois quebra `findLatest` (risco 3).
- `/accept` não valida documento, status nem versão: aceita versão inexistente.
- `hasAccepted` e `hasMandatoryConsents` tratam revogado como aceito; `checkUserCompliance` não.
- Usuário é "compliant" quando não há documento obrigatório publicado.
- Versão do documento calculada sem lock nem unique; possível duplicata (seção 4.1).
- `contentHash` do aceite descartado; `tenantId` nunca preenchido; `company_id` de documento nunca preenchido.

### Storage e consistência
- MinIO alterado antes do commit em create/publish/delete (seções 4.1-4.3); publicar documento não-DRAFT corrompe arquivos.
- Archive manual não move o arquivo; delete não republica o manifesto.
- Manifesto sempre global, com URL interna e horário com `Z` falso (seção 7.3).

### Infra
- Resilience4j sem efeito (seção 8.1); correlation id não vai para o MDC (seção 10); `-Xmx1024m` num limit de 1Gi (risco de OOMKill, `../ms-company/docs/migracao-go/02-consumidores-e-infra-prod.md:225`).
