# ms-user-consents — integrações de saída e resiliência (levantamento para migração Go)

Data: 2026-10-04. Só leitura, nenhum código alterado. Paths relativos a
`keepguard-core/backend/ms/`. `J/` = `ms-user-consents/src/main/java/com/keepguard/ms_user_consents/`,
`R/` = `ms-user-consents/src/main/resources/`.

Saídas: Postgres, Redis (cluster em prod), MinIO, RabbitMQ (consumer de eliminação + auditoria via
lib-common). **Não há cliente HTTP de saída** (sem Feign/RestTemplate no `J/`).

---

## 0. Resilience4j: o YAML é aplicado, mas não protege nada

- Dependências: `resilience4j-spring-boot3 2.2.0` + `spring-boot-starter-aop` (`ms-user-consents/pom.xml:58-61`, `:123-137`).
- **Não há** `@Bean` de registry manual (`CircuitBreakerRegistry.ofDefaults()` etc.) em `J/` — diferente do
  ms-auth. A auto-config do starter vale, então `resilience4j.*` de `R/application.yml:117-144` É carregado:
  - CB `redisCache`: janela 10, 50% falha, 60 s aberto, 3 em half-open, slow call 5 s/50% (`:118-130`).
  - Retry `redisCache`: 2 tentativas, 1 s, `exponentialBackoffMultiplier: 2` (`:132-144`). Obs.: o
    multiplicador só vale com `enableExponentialBackoff: true`, que não está setado → espera fixa de 1 s.
- **Porém todos os métodos anotados têm `try/catch (Exception)` interno que engole o erro e devolve `null`**
  (ex.: `J/infrastructure/redis/UserConsentCacheService.java:38-48`, `:54-60`, `:79-89`, `:119-129`). Como a
  exceção nunca sai do método:
  - o circuit breaker nunca registra falha (nunca abre);
  - o `@Retry` nunca retenta;
  - os `fallbackMethod` (`:172-185`) são código morto na prática.
- Mesmo padrão em `J/infrastructure/redis/ConsentDocumentCacheService.java:37-242` e
  `J/infrastructure/redis/ComplianceCacheService.java:34-116`.
- `management.health.circuitbreakers.enabled: true` (`R/application.yml:113-115`) expõe o CB no health,
  sempre CLOSED.

**Comportamento real hoje = Redis fail-open por try/catch, sem breaker e sem retry.** Cada chamada com Redis
fora paga o timeout do Lettuce (3 s, `R/application.yml:34`, `R/application-prod.yml:11`).

---

## 1. Postgres

- Hikari: connect 30 s, pool 10, min-idle 5, idle 10 min, max-lifetime 30 min, `SELECT 1`
  (`R/application.yml:15-26`). Schema `ms_user_consents`, `ddl-auto: update` (`:41-48`).
- Sem Resilience4j nos adapters de persistência (`J/infrastructure/persistence/*RepositoryAdapter.java`),
  sem retry. Credenciais em texto no YAML (`:16-18`) — no Go, só env/secret.
- Transações nos `*UseCaseService` (`J/application/service/ConsentDocumentUseCaseService.java:27-51`,
  `J/application/service/UserConsentUseCaseService.java:27-89`) e em `UserConsentCommandService.deleteAllByUserId`
  (`J/application/service/UserConsentCommandService.java:356`).
- **Postgres fora:** toda rota responde 500 após até 30 s esperando conexão (connection-timeout). O consumer
  de eliminação perde a mensagem (§4).

**Go:** `pgxpool` + `TxManager` do molde `ms-company-go/internal/adapters/out/postgres/pool.go` e
`tx_manager.go`. `ConnectTimeout` ~5 s, timeout por query via ctx; sem retry em escrita.

---

## 2. Redis

- Template String/String (`J/infrastructure/config/RedisConfig.java:12-22`); Lettuce, timeout 3 s, pool 8,
  `max-wait: -1ms` (espera infinita por conexão do pool) (`R/application.yml:32-40`). Prod: cluster de 6 nós,
  `max-redirects: 3` (`R/application-prod.yml:5-17`).
- TTLs e prefixos: `consent_doc_cache` 24 h, `user_consent_cache` 1 h, `compliance_cache` 1 h
  (`R/application.yml:76-86`). Chaves do user consent: `:latest:{user}:{doc}`, `:accepted:{user}:{doc}:{ver}`,
  `:user:{user}` (`UserConsentCacheService.java:194-204`).
- `clearAll` usa `KEYS prefix:*` (`UserConsentCacheService.java:158-169`) — em cluster, `KEYS` pelo
  template só varre um nó; limpeza incompleta. No Go: `SCAN` por master (`ForEachMaster`) ou evitar.
- **Redis fora:** leituras viram miss (vai ao banco), escritas/invalidações são ignoradas com WARN; cada
  operação custa até 3 s. Invalidação perdida → dado velho até o TTL (até 24 h para documentos).
  Risco relevante: `hasAccepted` em cache antigo após revogação durante falha do Redis.

**Go:** padrão fail-open do `ms-company-go/internal/adapters/out/redis/json_store.go` e
`ms-auth-go/internal/adapters/out/redis/session/redis_json_store.go`: erro → log + miss, nunca erro para o
caso de uso. `go-redis` `ClusterClient`, `ReadTimeout/WriteTimeout` ~300 ms, `PoolTimeout` finito. Sem CB
(não há comportamento real a reproduzir); se quiser, um breaker leve para não pagar timeout em série.

---

## 3. MinIO

- Cliente `io.minio:minio 8.5.7` (`ms-user-consents/pom.xml:86-90`), criado só com endpoint e credenciais,
  **sem timeouts explícitos** (padrão OkHttp do SDK: 5 min connect/read/write) (`J/infrastructure/config/MinIOConfig.java:22-30`).
- Endpoint prod `http://minio:9000`, credenciais no YAML (`R/application-prod.yml:21-25`); bucket
  `keepguard-consents` (`R/application.yml:63-74`).
- Operações (`J/infrastructure/storage/MinIOStorageAdapter.java`):
  - `uploadFile`: `bucketExists`+`makeBucket` a cada upload (`:31`, `:124-145`), `putObject` com
    part size -1 (`:34-41`); devolve URL `endpoint/bucket/key` (`:44`).
  - `fileExists`: qualquer erro → `false` (`:52-66`) — MinIO fora vira "arquivo não encontrado".
  - `downloadFile`, `deleteFile`, `generatePresignedUrl`: erro → `RuntimeException` (`:68-122`).
- Sem retry nem breaker.
- **MinIO fora:**
  - `create`: falha antes do INSERT → 500, nada persistido (`J/application/service/ConsentDocumentCommandService.java:69-75`, `:91`).
  - `publish`: `fileExists` false → "Arquivo não encontrado no storage" (`:142-144`). Se cair no meio da
    movimentação drafts→published / published→archived (download+upload+delete, `:161-182`, `:209-230`), o
    rollback do banco não desfaz os objetos já copiados/deletados → **storage e banco divergem**.
  - `delete`: `deleteFile` antes do `deleteById` (`:292-297`); se o banco falhar depois, o arquivo já sumiu.
  - Manifesto: erro engolido (§5).

**Go:** `github.com/minio/minio-go/v7` (`minio.New(endpoint, &minio.Options{Creds: credentials.NewStaticV4(...), Secure: false, Transport: <http.Transport com timeouts>})`).
Timeout por operação via ctx (~10 s upload pequeno, 5 s stat/delete). Usar `CopyObject` server-side em vez
de download+upload para mover drafts/published/archived. Criar bucket só no boot (não a cada upload).
`StatObject`: distinguir `NoSuchKey` (false) de erro de rede (erro → 503), ao contrário do Java.

---

## 4. RabbitMQ — consumer `user.erasure.requested` (LGPD Art. 18)

Topologia (`J/infrastructure/messaging/UserErasureConsumer.java:24-30`), **precisa ser idêntica no Go**:
- Fila `ms.user-consents.user-erasure`, `durable=true`, sem `exclusive`/`autoDelete`, **sem argumentos**
  (nenhum `x-dead-letter-*`, `x-message-ttl`, `x-queue-type`) (`:26`).
- Exchange `keepguard-events-exchange`, `topic`, durável (padrão da anotação) (`:27`).
- Routing key `user.erasure.requested` (`:28`).
- Props sobrescrevíveis: `keepguard.events.user-erasure-queue|exchange|erasure-routing-key` (não definidas no YAML).
- Conexão: `RABBITMQ_HOST/PORT/USER/PASSWORD` (`R/application.yml:27-31`); sem prefetch/concurrency
  configurados (padrão Spring: prefetch 250, 1 consumer).

Processamento:
- Lê só `userId` (`:34-35`); vazio → ignora (`:36`); `UUID.fromString` inválido → exceção.
- Chama `UserConsentCommandService.deleteAllByUserId` (`:38`), que é `@Transactional` + `@LogOperation(audit)`
  (`J/application/service/UserConsentCommandService.java:349-378`).
- **Idempotência:** natural — `findByUserId` vazio → retorna sem erro (`:361-365`).
- **Ack:** modo AUTO; o `catch (Exception)` só loga (`UserErasureConsumer.java:41-43`) → **sempre ack**,
  inclusive com Postgres fora ou userId inválido. **Eliminação LGPD pode ser perdida em silêncio.**
- **DLQ / retry:** não existem.
- Cache: invalidação depois do delete (`UserConsentCommandService.java:371`), fail-open.

**Go:** copiar `ms-auth-go/internal/adapters/in/rabbitmq/usererasure/user_erasure_consumer.go` trocando
`QueueName` para `ms.user-consents.user-erasure`; `Declare` com `args=nil` (`:170-178`) e exchange topic
durável — igual ao Java, senão o broker dá `PRECONDITION_FAILED`. Mantém ack sempre + log error + métrica
(decisão D12 do ms-auth-go), reconexão 5 s, prefetch 10. Parsear `userId` como UUID (erro → log/métrica).
DLQ só com recriação da fila em prod (fora do escopo da migração 1:1).

---

## 5. Publicação do `terms-manifest.json`

- `TermsManifestPublisherService.publishManifest(null)` (`J/application/service/TermsManifestPublisherService.java:37-104`):
  lê `findAllPublished` (`:40`), monta JSON e sobe em `public-legal/global/terms-manifest.json` (`:85-95`).
  Erro de MinIO/serialização é **engolido** (`:98-101`) → a publicação do documento segue com sucesso e o
  manifesto fica desatualizado, sem retry nem reprocessamento.
- Chamado em `publish` e `archive` (`ConsentDocumentCommandService.java:240`, `:269`), **não** em `delete`
  (`:285-305`) — deletar um PUBLISHED deixa o manifesto apontando para documento inexistente.
- **Não está fora da transação:** roda dentro do `@Transactional` do use case
  (`ConsentDocumentUseCaseService.java:34-45`), antes do commit. Se o commit falhar depois, o manifesto já
  publicado reflete um estado que não existe no banco. Também segura a conexão do banco durante o upload.
- `gracePeriodDays` fixo 15, versão = maior `version` + ".0", URL não assinada `endpoint/bucket/key` (`:45`, `:63-76`).

**Go:** publicar **depois do commit** (após `TxManager.WithTx` retornar), best-effort com log + métrica de
falha; considerar também regenerar em `delete` de PUBLISHED (mudança de comportamento — decidir).

---

## 6. Auditoria (RabbitMQ produtor)

- Via `lib-common 1.0.37-SNAPSHOT` (`ms-user-consents/pom.xml:112-116`) e `@LogOperation(audit = true)`
  (ex.: `ConsentDocumentCommandService.java:39-45`, `UserConsentCommandService.java:349-355`).
- Exchange `${KEEPGUARD_AUDIT_EXCHANGE:srv-audit-exchange-local}`, routing key `audit.event`,
  source `ms-user-consents` (`R/application.yml:152-157`); prod `srv-audit-exchange-prod` (`R/application-prod.yml:36-38`).
- Falha do publish: comportamento da lib (não lida aqui); não há outbox.

**Go:** `ms-auth-go/internal/adapters/out/messaging/audit/audit_publisher.go` (mesmo exchange/routing key/payload).

---

## 7. Resumo: quando cada dependência cai

| Dependência | Hoje | Go recomendado |
|---|---|---|
| Postgres | 500 após até 30 s; consumer perde mensagem | pgxpool, connect 5 s, ctx por query |
| Redis | fail-open por try/catch, 3 s por operação; CB/Retry inertes | fail-open explícito, timeout ~300 ms |
| MinIO | create/publish 500; `fileExists` mascara queda; banco×storage podem divergir | minio-go v7, ctx timeout, CopyObject, NoSuchKey ≠ erro |
| Manifesto | erro engolido, dentro da tx | após commit, best-effort + métrica |
| RabbitMQ consumer | sempre ack, sem DLQ | molde usererasure do ms-auth-go, args=nil |
| Auditoria | lib-common | audit_publisher do ms-auth-go |

Cliente HTTP resiliente (`ms-auth-go/internal/adapters/out/http/resilient/resilient_client.go`) não se
aplica: o serviço não faz chamadas HTTP de saída.
