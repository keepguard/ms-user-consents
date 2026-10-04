# ms-user-consents — consumidores e infra de produção (levantamento para troca Java → Go)

Data: 2026-10-04. Caminhos relativos a `keepguard-core/` (`KC`). Dados ao vivo: `kubectl get/top` e `rabbitmqctl list_*` read-only, namespace `keepguard` (só nomes de env/secret).

`BC` = `KC/backend/bff/bff-core/`, `MUC` = `KC/backend/ms/ms-user-consents/src/main/java/com/keepguard/ms_user_consents/`, `FE` = `KC/frontend/backoffice/src/`.

## 1. Quem chama o ms-user-consents

**Único consumidor HTTP: bff-core.** Nenhuma referência em bff-auth, srv/*, outros ms, investbot ou achadinhos (busca por `user-consents|consent-documents|8086`). ms-knowledge só cita o nome no catálogo de health (`KC/backend/ms/ms-knowledge/.../ask/ServiceCatalog.java:28,49`).

### 1.1 bff-core — `BC/internal/adapters/outbound/http/client/user_consent_client.go`
- resty, timeout 30 s, **2 retries** de 500 ms (:27-30). Vale para POSTs (accept-all/accept-batch não são idempotentes por si — o Java ignora já aceitos no accept-all, `MUC/adapters/in/rest/userConsent/UserConsentController.java:90`).
- Headers enviados em todas: `X-Correlation-ID`, `X-Company-Id`, `Content-Type`; `Authorization: Bearer` em accept/find*/check, opcional em accept-batch (:287-289), **ausente** em accept-all e delete (:249-255, :319-323). ms-user-consents não tem lib-security (pom.xml só `lib-common`) → hoje nenhuma rota exige JWT, embora `JWT_SECRET` esteja montado.
- accept-batch envia ainda `X-Forwarded-For`, `X-Public-IP` e `User-Agent` do cliente final (:290-296) — o Java grava IP/UA a partir deles (`UserConsentController.java:152`). O Go precisa ler os mesmos headers.

| Rota no ms | Linha | Status aceito | Usada de fato? |
|---|---|---|---|
| `POST /api/v1/user-consents/accept-all` | :247 | **só 201** (:261) | sim — saga de cadastro, passo 6 (`BC/internal/application/register/register_confirm_usecase.go:252-268`, timeout 30 s, comentário: costuma levar ~4 s) |
| `DELETE /api/v1/user-consents/user/{userId}` | :317 | **só 204** (:330) | sim — compensação da saga (:261-265) |
| `POST /api/v1/user-consents/accept-batch` | :279 | 200 ou 201 (:303) | sim — `POST /api/v1/user-consents/accept-batch` do bff-core (`BC/internal/adapters/inbound/http/server.go:131`, JWT) |
| `POST /accept`, `GET /{id}`, `GET /user/{id}`, `/user/{id}/document/{doc}[/latest]`, `/version/{v}/check` | :54-243 | 200/201 | **não** — métodos existem no client/decorator mas nenhum use case chama |

### 1.2 bff-core — `BC/internal/adapters/outbound/http/client/consent_document_client.go`
- Mesmo resty (timeout 30 s, 2 retries, :27-30), token vazio (`BC/internal/application/consentdocument/port.go:27,31`).
- `GET /api/v1/consent-documents/published` (:73) → `[]ConsentDocument`, status 200 só. Exposto como `GET /api/v1/consents/published` público (`server.go:124`).
- `GET /api/v1/consent-documents/type/{type}/latest-published` (:41) → 200 só. Exposto como `GET /api/v1/consents/type/:type/latest` público (`server.go:125`).

### 1.3 Campos lidos (`BC/internal/application/dto/wire.go`)
- ConsentDocument (:296-311): `id, title, description, type, version(int), status, s3Url, contentHash, fileSizeBytes(int64), mimeType, createdBy, createdAt, updatedBy, publishedAt` (datas como string).
- UserConsent (:322-333): `id, userId, email, consentDocumentId, version, acceptedAt, ipAddress, userAgent, geolocation, createdAt`.
- accept-all/batch response (:355-371): `{acceptedConsents:[{id,userId,email,consentDocumentId,version,acceptedAt,createdAt,ipAddress,userAgent,geolocation}], totalAccepted}`; datas via `CustomTime`.
- Request accept-all (:335-353): `{userId,email,acceptedAt:"2006-01-02T15:04:05.000Z",geolocation?}`. accept-batch (:373-419): idem + `consents:[{documentId,version,accepted,contentHash?}]`.
- O `s3Url` é montado no ms como `{STORAGE_MINIO_ENDPOINT}/{bucket}/{s3Key}` (`MUC/adapters/in/rest/consentDocument/mapper/ConsentDocumentAdapterMapper.java:18-20`) → em prod sai `http://minio:9000/keepguard-consents/...`. **O Go deve repetir esse formato** (o front faz replace literal de `http://minio:9000`, ver 2).

### 1.4 Tratamento de erro no bff-core
- accept-batch e delete usam `MapHTTPError` (`BC/.../client/error_mapper.go:24-44`): lê `{error, message, details, correlationId, traceId, errors{campo:msg}}`, traduz `message`/`error` e concatena `errors`. Os demais só repassam status + body cru (`user_consent_client.go:69-75` etc.).
- Formato do Java (`MUC/infrastructure/rest/GlobalExceptionHandler.java:22-92`): `{timestamp, status, error:"Not Found|Conflict|Bad Request|Internal Server Error", message, errors?}`. NotFound→404, AlreadyExists/IllegalState→409, IllegalArgument/validação/JSON inválido/header ausente→400, resto→500 com "Erro interno do servidor". **O Go deve manter os mesmos campos `error`/`message`/`errors`.**
- Decorators de métrica/log: `BC/cmd/bff-core/main.go:243-244` (label `ms-user-consents`); auditoria do bff-core reconhece `/user-consents/accept` (`.../middleware/audit_middleware.go:110,135`).
- Health: `ms-user-consents:8086/actuator/health/liveness` (`BC/application-prod.yml:158`, `BC/internal/application/connections/catalog.go:39`) → **o Go precisa servir `/actuator/health/liveness`**.
- Config: `BC/application-prod.yml:26`, `BC/helm/values.yaml:23`; ao vivo `BFF_CORE_SERVICES_USER_CONSENTS_BASE_URL=http://ms-user-consents:8086` (único Deployment com URL do ms).

### 1.5 Rotas sem consumidor
`/api/v1/compliance/**` inteiro (`MUC/adapters/in/rest/compliance/ComplianceController.java:23-103`); em consent-documents: `POST` multipart (criar), `/{id}/publish`, `/{id}/archive`, `GET /{id}`, `/status/{s}`, `/type/{t}`, `DELETE /{id}` (`ConsentDocumentController.java:36-274`); `POST /user-consents/revoke`; `/api/v1/health`. Publicar/arquivar documento hoje só acontece chamando o ms direto (túnel `KC/scripts/tunnel-prod-services-all.sh:30`, porta 18086) — **são as rotas que geram o manifesto**, não podem sumir.

## 2. MinIO lido direto pelo front (sem passar pelo ms)

- `FE/services/consentService.ts:4-8`: `MINIO_PUBLIC_URL = VITE_MINIO_PUBLIC_URL || (host keepguard.com.br → https://minio.keepguard.com.br)`. `formatDocumentUrl` troca `http://minio:9000` por essa URL (:26-30); usado para os PDFs no cadastro (`FE/components/register/RegisterForm.tsx:59-63`, a partir do `s3Url`) e no modal (`FE/components/common/TermsConsentModal.tsx:188,276`, a partir do `url` do manifesto).
- Manifesto (`FE/services/termsSyncService.ts:60-107`, chamado em `FE/navigation/AppLayout.tsx:64`, no máx. 1x a cada 7 dias por tenant via localStorage):
  1. `GET {MINIO}/keepguard-consents/public-legal/{tenantId}/terms-manifest.json`;
  2. se 404 → `.../public-legal/global/terms-manifest.json`;
  3. qualquer outro erro → silencioso, sem pendência.
  `tenantId` default `f7fc7350-...` (`FE/services/api.ts:31`). Pendente = doc sem aceite local ou com `version` maior (:118-121). Aceite vai por `POST /api/v1/user-consents/accept-batch` no bff-core (`TermsConsentModal.tsx:77-90`).
- Formato esperado (`FE/types/consent.ts:26-43`): `{tenantId?, version:string, publishedAt, effectiveAt, gracePeriodDays, documents:[{id, type, category: ESSENTIAL|FUNCTIONAL|ANALYTICS|MARKETING, title, version:int, mandatory, contentHash, url}]}`.
- Quem grava: `MUC/application/service/TermsManifestPublisherService.java:37-104`, chamado após publish e archive (`ConsentDocumentCommandService.java:240,269`) **sempre com `companyId=null`** → só existe o manifesto `global` (o caminho por tenant sempre dá 404). Detalhes a replicar no Go:
  - campo `companyId` (não `tenantId`, :72), `version = "{maxVersion}.0"` (:63-68), `gracePeriodDays=15`, `publishedAt/effectiveAt = LocalDateTime.now()` (sem fuso, :69);
  - `mandatory = categoria ESSENTIAL` (:44), ordem: obrigatórios primeiro, depois por título (:58-59);
  - `url = {endpoint}/{bucket}/{s3Key}` (:45) → `http://minio:9000/...`;
  - JSON pretty-print, `application/json` (:81-94); falha de upload só loga (:98-101).
- Bucket `keepguard-consents` (`MUC/../resources/application.yml:69`); prefixos `drafts/`, `published/`, `archived/` (:71-74) e `public-legal/`. O ms cria o bucket se faltar, **sem policy** (`MUC/infrastructure/storage/MinIOStorageAdapter.java:124-145`).
- Policy pública: só `mc anonymous set download .../keepguard-consents/published` + versionamento, aplicados pelo `KC/scripts/sync-apps-data-to-prod.sh:528-529` (e no compose comentado, `KC/docker/docker-compose.yml:150`). **`public-legal/` não está na policy** → o manifesto não seria lido anonimamente nem com MinIO exposto.
- **Ao vivo, o MinIO não é público:** `minio.keepguard.com.br` não tem DNS (dig vazio, curl 000); não há Ingress/IngressRoute para `minio` (ingresses: front-achadinhos, front-keepguard-core, grafana, keepguard-api-http/https). Ou seja, links de PDF e checagem de termos já falham hoje em produção (silenciosamente). A migração não piora isso, mas não deve “consertar” sem decisão.

## 3. Redis e RabbitMQ

- Redis: chaves só do próprio ms — prefixos `consent_doc_cache`, `user_consent_cache`, `compliance_cache` (`application.yml:83-86`; montagem em `MUC/infrastructure/redis/ConsentDocumentCacheService.java:289-305` e afins), TTL 24 h / 1 h / 1 h (:80-82). Invalidação usa `KEYS pattern` (`ConsentDocumentCacheService.java:246`, `ComplianceCacheService.java:120`). **Ninguém fora do ms lê essas chaves** (busca no monorepo) → o Go pode trocar o formato, desde que não leia cache Java velho (ou use prefixo novo / flush).
- RabbitMQ consome `ms.user-consents.user-erasure` (durable), exchange topic `keepguard-events-exchange`, key `user.erasure.requested` (`MUC/infrastructure/messaging/UserErasureConsumer.java:24-30`). Lê só `userId` do JSON e chama `deleteAllByUserId`; erro só loga, mensagem é ack-ada (:31-43). Publicador: ms-user (`KC/backend/ms/ms-user/.../messaging/UserErasureEventPublisher.java:27-52`). Ao vivo: fila com 1 consumer, 0 msgs, binding presente (mesmo padrão de `ms.auth.*` e `ms.billing.*`). O Go deve usar a **mesma fila** (não criar outra) e só um consumidor ativo por vez durante a troca.
- Publica auditoria via lib-common (`@Monitored(audit=true)` em `ConsentDocumentCommandService.java:42-44,131-133,252-254,281-283` e `UserConsentCommandService.java:44-46,117-119,203-205,281-283,352-354`), exchange `KEEPGUARD_AUDIT_EXCHANGE=srv-audit-exchange-prod`, key `audit.event`, source `ms-user-consents` (`application.yml:152-157`). Ações: CREATE/PUBLISH/ARCHIVE/DELETE (CONSENT_DOCUMENT), ACCEPT/ACCEPT_ALL/ACCEPT_BATCH/REVOKE/DELETE_ALL (USER_CONSENT).

## 4. Infra em produção

| Item | Ao vivo |
|---|---|
| Deployment/container | `ms-user-consents`/`ms-user-consents`, 1 réplica, selector `app=ms-user-consents`, sem Helm release |
| Estratégia | RollingUpdate **maxSurge 0 / maxUnavailable 1** → todo deploy derruba o pod antes de subir o novo |
| Imagem | `ghcr.io/keepguard/ms-user-consents:f84fae9` |
| Service | `ms-user-consents` ClusterIP 10.43.113.111, 8086/TCP, selector `app=ms-user-consents` |
| Resources | requests 384Mi / limits 1Gi, sem CPU |
| Probes | nenhuma (liveness/readiness vazias) |
| Uso (`kubectl top`) | 2m CPU, **705Mi**; pod 23d, 1 restart (23d atrás) |
| Profile | `SPRING_PROFILES_ACTIVE` do cm `keepguard-config` = **`local`** |
| Ingress | não exposto; IngressRoute `keepguard-api-https` manda `/api/v1/consents` e `/api/v1/user-consents` para o **bff-core** |

Env (só nomes): `SPRING_PROFILES_ACTIVE`(cm), `SPRING_CLOUD_COMPATIBILITY_VERIFIER_ENABLED`, `SERVER_PORT=8086`, `JWT_SECRET`(secret `keepguard-secret`, não usado), `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/keepguard_api_db`, `SPRING_DATASOURCE_USERNAME`(cm `POSTGRES_USER`), `SPRING_DATASOURCE_PASSWORD`(secret), `SPRING_DATA_REDIS_HOST=redis`/`PORT=6379`, `STORAGE_MINIO_ENDPOINT=http://minio:9000`, `KEEPGUARD_VALIDATION_MODERATION_ENABLED`, `SPRING_RABBITMQ_HOST/PORT`, `RABBITMQ_HOST/PORT`, `SPRING_RABBITMQ_USERNAME/PASSWORD` (**literais no spec**), `KEEPGUARD_AUDIT_EXCHANGE=srv-audit-exchange-prod`.
- **Credencial do MinIO não vem do cluster:** não há env de access/secret key; vale o hardcoded `keepguard_admin` / senha em `application.yml:66-67` (e `application-prod.yml:24-25`). O Go deve receber isso por Secret (`MINIO_ROOT_USER/PASSWORD` do Deployment `minio`), não repetir o hardcode.
- MinIO no cluster: Deployment `minio` (`minio/minio:RELEASE.2024-10-02T17-50-41Z`, `server /data --console-address :9100`, env `MINIO_ROOT_USER/PASSWORD`), Service `minio` 9000/9100 ClusterIP, PVC `minio-pvc` 15Gi local-path. Sem Ingress.
- Schema `ms_user_consents` (`application.yml:48`): criado/alterado pelo Hibernate `ddl-auto: update` (`application.yml:43`, o profile `local` não sobrescreve); sem Flyway. Dados vieram de `KC/scripts/sync-apps-data-to-prod.sh:40` (pg_dump/restore) e bucket de `:48` (mc mirror). `application-prod.yml` (Redis cluster, :7) nunca é carregado. Não aparece em `k8s/backup/postgres-backup.yaml`.
- Actuator: `health,info,prometheus` + probes habilitados (`application.yml:99-115`).
- Prometheus: job `ms-user-consents`, `ms-user-consents:8086/actuator/prometheus`, labels `application=ms-user-consents`, `environment=production` (`KC/k8s/observability/prometheus-configmap.yaml:61-67`; local em `KC/docker/prometheus/prometheus.yml:54-59`). Sem regra de alerta específica (busca em `k8s/observability/alerting`).
- Dashboard: `KC/k8s/observability/generate-dashboards.py:380-381` → `dashboards/ms-user-consents.json` (métricas Spring `http_server_requests` por URI `/api/v1/user-consents.*`); cópia em `KC/docker/grafana/provisioning/dashboards/json/ms-user-consents.json`. O Go precisa expor nomes compatíveis ou o dashboard tem que ser regerado.
- Deploy: `script-deploy-github-ms-user-consents.sh:161` e `script-deploy-k8s-prod.sh:73` fazem `kubectl set image deployment/ms-user-consents ms-user-consents=...`; `KC/scripts/deploy-all-prod.sh:70`.
- docker-compose local (`KC/docker/docker-compose.yml:359-404`): imagem `f84fae9`, `JAVA_OPTS -Xmx640m`, porta `${MS_USER_CONSENTS_PORT}:8086`, `STORAGE_MINIO_ENDPOINT=http://minio:9000` mas o serviço `minio` está **comentado** (:96-150), healthcheck `curl /actuator/health`. bff-core local aponta para `${MS_USER_CONSENTS_URL:-http://ms-user-consents:8086}` (:703).

## 5. O que a troca precisa manter
1. Service `ms-user-consents:8086`, mesmos paths e status (201 em accept-all, 204 no delete) e formato de erro `{error,message,errors}`.
2. `s3Url`/`url` no formato `{endpoint}/keepguard-consents/{s3Key}` e manifesto `public-legal/global/terms-manifest.json` com os mesmos campos.
3. Ler `X-Forwarded-For`/`X-Public-IP`/`User-Agent` no accept-batch; não exigir JWT (bff-core chama accept-all/delete sem token).
4. `/actuator/health/liveness` e `/actuator/prometheus` (ou ajustar bff-core e Prometheus juntos).
5. Mesma fila `ms.user-consents.user-erasure` e auditoria em `srv-audit-exchange-prod`.
6. Schema `ms_user_consents` já existe: Go com `DB_RUN_MIGRATIONS=false` no primeiro deploy, sem `ddl-auto`.
7. Corrigir junto (com decisão do Rafael): probes, credencial MinIO via Secret, maxSurge 0. Fora de escopo: expor MinIO publicamente e a policy de `public-legal/`.
