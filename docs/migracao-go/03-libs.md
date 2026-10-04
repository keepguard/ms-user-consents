# ms-user-consents — libs Java usadas e o que vira em Go

Data: 2026-10-04. Base: `keepguard-core/backend/ms/`. Abreviações:
`UC` = `ms-user-consents/src/main/java/com/keepguard/ms_user_consents`, `LC` = `lib-common/src/main/java/com/keepguard/lib_common`,
`LGC` = `lib-go-common`. Modelo: `ms-auth/docs/migracao-go/03-libs.md` (regras do aspecto já detalhadas lá, §1.7-1.8).

---

## 0. Resumo

- **Só depende de lib-common** (`com.keepguard:lib-common:1.0.37-SNAPSHOT`, ms-user-consents/pom.xml:114-115). Não usa lib-security nem lib-validation.
- Usado de fato: `@LogOperation` (**9** usos, todos `audit=true`), `MetricsService` (via `MetricsAdapter`, só `incrementCounter`), `MetricsConfig` (UC/MsUserConsentsApplication.java:11) e o component scan de `com.keepguard.lib_common` (:9), que traz `LoggingAspect`, `LoggingService`, `MetricsService` e o publisher RabbitMQ de auditoria.
- **`@MetricsEndpoint`: 0 usos.** O ms não emite `api_requests_total`/`api_requests_latency_seconds` hoje.
- `ValidationUtils`: **só imports mortos** (0 chamadas) — UC/adapters/in/rest/compliance/ComplianceController.java:7, UC/adapters/in/rest/consentDocument/ConsentDocumentController.java:9, UC/adapters/in/rest/userConsent/UserConsentController.java:12. Não portar.
- Correlation-ID é **código local** (UC/infrastructure/filter/CorrelationIdFilter.java, UC/infrastructure/context/CorrelationContext.java), não da lib.
- Nenhuma ocorrência perde auditoria por auto-invocação (§2.1).

---

## 1. Símbolos da lib-common usados (regra exata)

### 1.1 `MetricsService` — LC/metrics/service/MetricsService.java
Adaptador UC/infrastructure/metrics/MetricsAdapter.java:13-52 expõe 7 métodos da lib pela porta `MetricsPort`, mas o código do ms **só chama `incrementCounter`** (os outros 6 — `startSample`, `recordTimer`, `setGauge`, `recordSuccess`, `recordError`, `recordMessageSend` — são mortos; não portar).
- `incrementCounter(name, tags)` = `meterRegistry.counter(name, tags...).increment()` (MetricsService.java:26-28). Sem `help`, sem pré-registro: o conjunto de labels é o do primeiro uso.
- Contadores de negócio emitidos pelo ms (ficam **no serviço** em Go):
  - UC/application/service/UserConsentCommandService.java: `user_consent_business_errors_total{error_code="ALREADY_ACCEPTED",operation="accept"}` (:62); `user_consent_accepted_total{status=SUCCESS}` (:87, :262); `user_consent_accept_all_total{status=NO_DOCUMENTS|SUCCESS}` (:129, :180); `user_consent_accepted_batch_total{status=SUCCESS}` (:182); `user_consent_ignored_batch_total{status=IGNORED}` (:186); `user_consent_revoked_total{status=SUCCESS}` (:316); `user_consent_deleted_all_total{status=SUCCESS}` (:374).
  - UC/application/service/ConsentDocumentCommandService.java: `consent_document_{created,published,archived,deleted}_total{entity_id,type}` (:97, :243, :272, :303).
  - Queries: `consent_document_queries_total`/`consent_document_not_found_total` (ConsentDocumentQueryService.java:37, 46, 55, 119, 128, 137, 150, 165); `compliance_queries_total`/`compliance_status_total` (ComplianceQueryService.java:37, 98, 100); `user_consent_queries_total`/`user_consent_not_found_total` (UserConsentQueryService.java:69, 78, 89, 103, 115).
- **Achado**: `consent_document_*_total` usa `entity_id` (UUID do documento) como label — cardinalidade ilimitada. Decidir no Go se mantém (compatível com dashboard) ou remove o label.

### 1.2 `MetricsConfig` — importado em UC/MsUserConsentsApplication.java:11
Config Micrometer/Prometheus. Em Go = `/actuator/prometheus` (ou `/metrics`) com `promhttp`, já padrão no ms-auth-go/ms-company-go.

### 1.3 `@LogOperation` + `LoggingAspect` + `LoggingService` + `AuditContextCollector`
Comportamento que vira código explícito (regras completas em ms-auth 03 §1.8):
1. `operations_total{operation=<lower(operation)>,status=SUCCESS|ERROR}` sempre (LC/logging/service/LoggingService.java:54-56, 72-74); no erro também `business_errors_total{error_type="UNKNOWN_ERROR"}` (:76-77, errorCode fixo em LoggingAspect.java:101).
2. Logs de início/sucesso/erro: **desligados** (`LoggingService`, `MetricsAspect`, `StructuredLogger` = OFF em ms-user-consents/src/main/resources/logback-spring.xml:49-51, 72-74, 96-98). Não portar.
3. Auditoria (`audit=true`): `action = auditAction` (LoggingAspect.java:91/107); outcome `SUCCESS`/`FAILURE` (:92/:108); `changes` = nada (o `AuditChangeExtractor` só trata ações de USER do ms-auth).
4. `entityId` (LoggingAspect.java:231-265): para `USER_CONSENT`/`CONSENT_DOCUMENT` cai no caminho genérico — `deviceId/codeUser/entity_id/id` do contexto (nenhum existe aqui; `consentDocumentId` e `userId` **não** contam, `isEntityIdParam` :295-301) → `result.getId()` → `null`. Ou seja: **id do retorno no SUCCESS, vazio no FAILURE**, e vazio sempre que o retorno é `void` ou DTO sem `getId`.
5. Placeholders (`formatDescription`, LoggingAspect.java:342-356): só chaves planas. `{userId}`, `{consentDocumentId}` resolvem (arg com esse nome); `{command.userId}`, `{command.title}`, `{command.consentDocumentId}` **ficam literais** no `reason`.
6. Envelope (LC/audit/AuditContextCollector.java:15-84): `correlationId` = MDC → header `X-Correlation-ID` → UUID novo; `codeUser` = MDC → `X-User-ID` → `sub` do JWT; `tenantId`/`companyId` com fallback cruzado MDC/`X-Tenant-Id`/`X-Company-Id`/JWT; `clientIp` = 1º de `X-Forwarded-For` ou remoteAddr; `actor.type` USER/ANONYMOUS; `reason` truncado em 512. `source-service: ms-user-consents`, exchange `${KEEPGUARD_AUDIT_EXCHANGE}`, routing key `audit.event` (ms-user-consents/src/main/resources/application.yml:152-157).
7. Filtro de descarte do `publishAudit` (LoggingService.java:199-204: REFRESH_TOKEN/OAUTH_TOKEN_ISSUE) **não afeta** nenhuma ação deste ms.

---

## 2. Todas as ocorrências

### 2.1 `@LogOperation` — 9 ocorrências, todas `audit = true` explícito

| # | Arquivo:linha (UC/application/service/…) | Método | operation | auditAction | entityType | description (reason) | resource.id |
|---|---|---|---|---|---|---|---|
| 1 | UserConsentCommandService.java:41 | accept(command) | ACCEPT_USER_CONSENT | ACCEPT | USER_CONSENT | "Registrando aceite de consentimento para usuário: {command.userId}" (literal) | id do consent / vazio na falha |
| 2 | UserConsentCommandService.java:114 | acceptAll(command) | ACCEPT_ALL_USER_CONSENTS | ACCEPT_ALL | USER_CONSENT | "Registrando aceite de todos os documentos publicados para usuário: {command.userId}" (literal) | **sempre vazio** (retorno `UserConsentAcceptAllResultDTO` sem `getId`) |
| 3 | UserConsentCommandService.java:200 | acceptBatch(command) | ACCEPT_BATCH_USER_CONSENTS | ACCEPT_BATCH | USER_CONSENT | "Registrando aceite seletivo em lote de consentimentos para usuário: {command.userId}" (literal) | **sempre vazio** |
| 4 | UserConsentCommandService.java:278 | revoke(command) | REVOKE_USER_CONSENT | REVOKE | USER_CONSENT | "Revogando consentimento para usuário: {command.userId}, documento: {command.consentDocumentId}" (literal) | id do consent (também quando já estava revogado, :302-305) |
| 5 | UserConsentCommandService.java:349 | deleteAllByUserId(UUID userId) | DELETE_ALL_USER_CONSENTS | DELETE_ALL | USER_CONSENT | "Deletando todos os consentimentos para usuário: {userId}" (**resolve**) | **sempre vazio** (`void`) |
| 6 | ConsentDocumentCommandService.java:39 | create(command) | CREATE_CONSENT_DOCUMENT | CREATE | CONSENT_DOCUMENT | "Criando novo documento de consentimento: {command.title}" (literal) | id do doc / vazio na falha |
| 7 | ConsentDocumentCommandService.java:128 | publish(UUID consentDocumentId, String updatedBy) | PUBLISH_CONSENT_DOCUMENT | PUBLISH | CONSENT_DOCUMENT | "Publicando documento de consentimento: {consentDocumentId}" (resolve) | id do doc / vazio na falha |
| 8 | ConsentDocumentCommandService.java:249 | archive(UUID consentDocumentId, String updatedBy) | ARCHIVE_CONSENT_DOCUMENT | ARCHIVE | CONSENT_DOCUMENT | "Arquivando documento de consentimento: {consentDocumentId}" (resolve) | id do doc / vazio na falha |
| 9 | ConsentDocumentCommandService.java:278 | delete(UUID consentDocumentId) | DELETE_CONSENT_DOCUMENT | DELETE | CONSENT_DOCUMENT | "Deletando documento de consentimento: {consentDocumentId}" (resolve) | **sempre vazio** (`void`) |

**Auto-invocação: nenhuma.** Nenhum método anotado é chamado via `this` dentro da própria classe; todos entram pelo proxy Spring:
- UC/application/service/UserConsentUseCaseService.java:29 (accept), :70 (acceptAll), :76 (acceptBatch), :82 (revoke), :89 (deleteAllByUserId);
- UC/application/service/ConsentDocumentUseCaseService.java:29 (create), :36 (publish), :43 (archive), :50 (delete);
- UC/infrastructure/messaging/UserErasureConsumer.java:38 (deleteAllByUserId, via RabbitMQ).

Casos que publicam com envelope pobre (reproduzir conscientemente no Go):
- `deleteAllByUserId` chamado pelo `UserErasureConsumer`: fora de request HTTP → sem headers; `actor.type=ANONYMOUS`, `clientIp` vazio, `correlationId` novo se o consumer não pôs no MDC, `resource.id` vazio. O `userId` só aparece no `reason`.
- `deleteAllByUserId` sem consents (UserConsentCommandService.java:362-365) retorna cedo e **ainda publica SUCCESS** (o aspecto não distingue).
- `create` embrulha toda exceção em `RuntimeException("Falha ao criar ConsentDocument: …")` (ConsentDocumentCommandService.java:102-108) → FAILURE publicado com `reason` = description, não a mensagem.

Também não publicam (independente da ação): publisher NoOp/RabbitMQ fora (LoggingService.java:207-211) — descarte com warn.

### 2.2 `@MetricsEndpoint` — 0 ocorrências
Nenhum controller anotado. Em Go **não** registrar `httpmetrics.Recorder` por paridade (ou registrar e aceitar métrica nova — decisão de produto, não de porte).

---

## 3. lib-go-common hoje e o que falta para o ms-user-consents

| Pacote LGC | Serve? | Observação |
|---|---|---|
| `brdoc` | Sim, só `ParseUUID` (LGC/brdoc/uuid.go:29-36) para path params — o Java faz parse por `@PathVariable UUID`, não por `ValidationUtils`; a mensagem do 400 vem do handler local, não da lib. `ValidateEmail` não é usado (o ms só armazena `email`). |
| `apperr` | Sim — ProblemDetail do GlobalExceptionHandler local. |
| `correlation` | Sim — substitui CorrelationIdFilter/CorrelationContext locais; `Transport` se houver client HTTP de saída. |
| `httpmetrics` | **Não** (0 `@MetricsEndpoint`). |
| `audit` | Sim — `Event`/`Publisher`/`NewRabbitPublisher` (LGC/audit/audit_event.go:29-74). Falta o equivalente do `AuditContextCollector` (§3.1). `MaskEmail` não é usado. |
| `auth` | Depende do 01/02: o ms não usa lib-security; se o Go passar a validar JWT, `auth.Verifier`/`Middleware`. |
| `oauthsecret`, `codegen`, `comm` | **Não** usados. |

### 3.1 O que falta e onde deve morar

1. **`oplog` (equivalente do `@LogOperation`: `operations_total` + `business_errors_total` + publicação SUCCESS/FAILURE)** → **lib (`LGC/oplog`)**.
   Hoje já está **duplicado** em ms-company-go/internal/application/oplog/operation_logger.go:15-62 e ms-auth-go/internal/application/oplog/operation_logger.go, e os próximos a migrar também usam `@LogOperation`: ms-user (26), ms-communication (15), ms-knowledge (8). ms-billing e ms-ai-guardian: 0. Ao subir para a lib, acrescentar o `business_errors_total{error_type="UNKNOWN_ERROR"}` que a versão do ms-company-go omite, e manter `ResourceID` preenchido pelo retorno (regra §1.3 item 4).
2. **Coletor de contexto do ator/tenant (porta do `AuditContextCollector`)** → **lib (`LGC/audit`, ex.: `audit.FromRequest(r *http.Request)`/middleware que põe no `context`)**. Hoje cada Go tem o seu (ms-auth-go/internal/adapters/in/http/middleware/request_actor_middleware.go:14-33, ms-auth-go/internal/adapters/out/messaging/audit/audit_publisher.go:75); a regra de fallback (X-User-ID, X-Tenant-Id/X-Company-Id cruzados, 1º IP do X-Forwarded-For) é contrato com o srv-audit e é igual para todos os ms acima.
3. **Contador de negócio genérico (`MetricsService.incrementCounter`)** → **dentro do serviço** (adapter `metrics` com `CounterVec` declarados, como ms-auth-go/internal/adapters/out/metrics/business_metrics.go). Nomes e labels são do domínio; não justificam lib. O `oplog` da lib recebe uma interface `Increment(name, labels)`.
4. **Descrições/`reason` com placeholders** → **dentro do serviço** (string montada no use case). Decidir se mantém os literais `{command.*}` (compatível com o que o srv-audit já tem) ou corrige.

---

## 4. Achados para o plano
- 9 auditorias, todas publicadas; 4 delas com `resource.id` sempre vazio (acceptAll, acceptBatch, deleteAllByUserId, delete) — no Go dá para preencher (userId / consentDocumentId) mas muda o que o srv-audit recebe.
- 5 descriptions com `{command.*}` saem literais no `reason`.
- Label `entity_id` em `consent_document_*_total` = alta cardinalidade.
- Imports mortos de `ValidationUtils` (3 controllers) e 6 métodos mortos no `MetricsAdapter` — não portar.
- Erasure via RabbitMQ publica auditoria ANONYMOUS, sem IP e com id vazio.
