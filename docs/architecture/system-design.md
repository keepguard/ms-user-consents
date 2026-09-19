# ms-user-consents - System Design

## 1. Propósito e Domínio
- **Responsabilidade Principal:** Gerenciar o ciclo de vida de documentos legais de consentimento (criação DRAFT → publicação → arquivamento) e o registro forense de aceites/revogações de usuários (IP, User-Agent, geolocalização, contentHash), além de checagens de compliance LGPD e expurgo sob solicitação de eliminação de titular.
- **Domínio/Subdomínio:** Compliance / Consent Management (LGPD) — subdomínio de IAM/Users da plataforma KeepGuard.

## 2. Tech Stack Local
- **Linguagem & Framework:** Java 25 / Spring Boot 3.5.3 (Spring Cloud BOM 2025.0.3); Web MVC, Data JPA, Validation, AOP, Actuator; virtual threads habilitadas; Lombok 1.18.40; Springdoc OpenAPI 2.3.0; Resilience4j 2.2.0 (circuit breaker + retry no cache Redis); lib-common `1.0.37-SNAPSHOT` (auditoria/`@LogOperation`, ValidationUtils).
- **Persistência e Cache:** PostgreSQL (`keepguard_api_db`, schema JPA `ms_user_consents`, tabelas `user_consents` e documentos de consentimento; `ddl-auto: update`); Redis (standalone em local; cluster 6 nós em dev/prod) com TTLs configuráveis — documentos 24h, user-consent 1h, compliance 1h; MinIO/S3 para PDFs/HTML dos termos (buckets `keepguard-consents` / paths `drafts|published|archived`) e manifesto `terms-manifest.json`.
- **Mensageria:** RabbitMQ (AMQP) — **consome** `user.erasure.requested` (exchange topic `keepguard-events-exchange`, fila `ms.user-consents.user-erasure`); **publica** eventos de auditoria via `keepguard.audit` (exchange `srv-audit-exchange-{local|dev|prod}`, routing-key `audit.event`, source-service `ms-user-consents`).

## 3. Arquitetura Interna
- **Padrão Utilizado:** Hexagonal (Ports & Adapters) + DDD leve — domínio rico imutável (`domain/entity`, `domain/enums`), application com ports in/out e serviços CQRS-like (Command/Query/UseCase), adapters REST de entrada e infrastructure (JPA, Redis, MinIO, RabbitMQ, Micrometer) de saída.
- **Módulos Principais:**
  - **Agregados de domínio:** `ConsentDocument` (ciclo DRAFT → PUBLISHED → ARCHIVED; SHA-256 `contentHash`; `s3Key`) e `UserConsent` (ACCEPTED/REVOKED; `companyId`/`tenantId`; evidências forenses).
  - **Enums de negócio:** `ConsentType` (TERMS_OF_USE, PRIVACY_POLICY, LGPD_COMPLIANCE, cookies, marketing, etc.), `ConsentCategory` (ESSENTIAL/FUNCTIONAL obrigatórios; ANALYTICS/MARKETING opcionais com expiração), `ConsentDocumentStatus`, `UserConsentStatus`.
  - **Ports in:** `ConsentDocumentPort`, `UserConsentPort`, `CompliancePort`.
  - **Ports out:** `ConsentDocumentRepositoryPort`, `UserConsentRepositoryPort`, caches (`ConsentDocument`/`UserConsent`/`Compliance`), `StoragePort`, `MetricsPort`.
  - **Application services:** `ConsentDocumentCommand/QueryService`, `UserConsentCommand/Query/UseCaseService`, `ComplianceQueryService`, `TermsManifestPublisherService`.
  - **Adapters in REST:** `ConsentDocumentController`, `UserConsentController`, `ComplianceController`, `HealthController` (`/api/v1/health`).
  - **Infrastructure:** JPA adapters + Spring Data repos, Redis cache services (CB `redisCache`), `MinIOStorageAdapter`, `UserErasureConsumer`, `MetricsAdapter`, `CorrelationIdFilter`, `GlobalExceptionHandler`.

## 4. Superfície de Contato (I/O)
- **Endpoints Expostos Principais:**
  - `POST/GET/DELETE /api/v1/consent-documents` — CRUD + `/{id}/publish`, `/{id}/archive`, filtros por status/type, `type/{type}/latest-published`, `/published` (multipart até 10MB na criação).
  - `POST /api/v1/user-consents/accept|accept-all|accept-batch|revoke`; `GET .../{id}`, `.../user/{userId}`, `.../document/...`, `.../latest`, `.../version/{v}/check`; `DELETE .../user/{userId}`.
  - `GET /api/v1/compliance/user/{userId}`, `.../mandatory`, `/consent-types`, `/consent-types/mandatory`.
  - Actuator: `/actuator/health`, `/actuator/prometheus`; Swagger UI habilitado.
  - Header obrigatório em rotas de negócio: `X-Company-Id` (multi-tenant).
- **Dependências Externas:** PostgreSQL; Redis; MinIO; RabbitMQ (erasure inbound + audit outbound para `srv-audit`); biblioteca compartilhada `lib-common`. Sem clients HTTP diretos a outros microsserviços neste repositório — integração cross-service via mensageria e contratos REST consumidos por BFFs/outros MS.

## 5. Invariantes Locais e Observações
- **Multi-tenancy:** isolamento por `X-Company-Id` / `companyId` nas leituras de user-consent (403 cross-tenant); registros legados com `companyId == null` permitidos por compatibilidade; manifesto MinIO particionado em `public-legal/{companyId}/terms-manifest.json`.
- **Ciclo do documento:** só DRAFT publica; só PUBLISHED arquiva; publicação move objeto no MinIO (`drafts/` → `published/`) e dispara republicação do `terms-manifest.json` (grace period 15 dias).
- **Aceite único por versão:** `existsByUserIdAndConsentDocumentIdAndVersion` → `AlreadyExistsException`; `accept-all` ignora silenciosamente já aceitos; `accept-batch` grava seletivo com `contentHash` e trata não-obrigatórios.
- **Revogação:** tipos com `ConsentType.isMandatory()` (categorias ESSENTIAL + FUNCTIONAL) **não** podem ser revogados individualmente — exigem encerramento de conta / erasure.
- **Compliance:** usuário compliant se não faltam documentos publicados obrigatórios com status ACCEPTED e sem `revokedAt`.
- **LGPD Art. 18:** consumer RabbitMQ expurga todos os consentimentos do titular em `user.erasure.requested` (idempotente via `deleteAllByUserId`).
- **Auditoria forense:** IP (cadeia X-Forwarded-For etc.), User-Agent, geolocation, e-mail; operações anotadas `@LogOperation(audit=true)` → exchange de audit.
- **Resiliência:** circuit breaker/retry em Redis; falha de publish do manifesto não aborta a transação principal; schema dedicado `ms_user_consents`; portas 8086 (dev/prod) / 8586 (local).
- **Peculiaridade:** no manifesto MinIO, `mandatory` é derivado só de `ConsentCategory.ESSENTIAL`, enquanto `ConsentType.isMandatory()` inclui também FUNCTIONAL — possível divergência entre manifesto público e regras de revoke/compliance.
