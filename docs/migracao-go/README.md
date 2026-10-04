# Migração Java → Go: ms-user-consents — levantamento

Data: 2026-10-04. Status: **D1–D12 aprovadas como recomendadas. Fases 1–3 concluídas; falta produção (Fase 4).**

## Resultado (2026-10-04)

- Fase 1: `lib-go-common` **v0.2.0** com `oplog` e o coletor de ator (`audit.ActorMiddleware`,
  `audit.NewOplogRecorder`, ator `SYSTEM`).
- Fase 2: `keepguard-core/backend/ms/ms-user-consents-go` (repo `keepguard/ms-user-consents-go`),
  3 contextos, 24 rotas, consumer LGPD. 20 pacotes de teste, build isolado `-mod=vendor` ok.
- Fase 3: `ms-user-consents-go/docs/testes-locais/api_flow.sh` — 60/60 contra a infra local
  (validações, D5, D6a–e, D8, cache, Redis/MinIO fora, FK, erasure pela fila, auditoria).
- **Achado novo:** `user_consents` em prod (e no banco local) **não tem a coluna `status`** que o
  Java mapeia (o `ddl-auto update` não conseguiu criá-la NOT NULL com a tabela já populada). O Java
  provavelmente devolve 500 nas rotas de aceite hoje. O Go deriva o status de `revoked_at`.
- Fase 4: `docs/prompts/deploy-ms-user-consents-go-prod.md` + `ms-user-consents-go/docs/deploy-prod-substituir-java.md`.
Moldes: `ms-company-go` e `ms-auth-go` (em prod). Prompt: `docs/prompts/migrar-ms-user-consents-para-go.md`.

## Por que migrar

| | Memória real em prod |
|---|---|
| ms-user-consents (Java) | **705Mi** (384Mi/1Gi, sem probes, profile `local`) |
| ms-company-go / ms-auth-go | 7Mi cada |

~5,8k linhas Java, **24 endpoints** (ConsentDocument 9, UserConsent 10, Compliance 4, Health 1).

## Documentos

| Arquivo | Conteúdo |
|---|---|
| `01-especificacao.md` | Endpoints, contrato de erro, domínio, use cases, schema, Redis, MinIO e manifesto, auditoria, bugs. |
| `02-consumidores-e-infra.md` | Só o bff-core chama (5 rotas); front lê o manifesto direto do MinIO; Deployment real. |
| `03-libs.md` | Só lib-common: 9 `@LogOperation`, 1 contador. Proposta de levar `oplog` para a lib. |
| `04-resiliencia-integracoes.md` | Postgres, Redis, MinIO, consumer LGPD; padrões Go recomendados. |

## O que muda a forma do plano

1. **Erro não é ProblemDetail**: mapa `{timestamp, status, error, message, errors}` na raiz — é o que o
   `MapHTTPError` do bff-core lê. O `httperr` do Go segue ESTE formato (não o do ms-auth).
2. **A exclusão LGPD provavelmente não apaga nada**: o `DELETE` do repositório não tem `@Modifying`
   → 500 pela API; pelo consumer a exceção é engolida e a mensagem confirmada.
3. **MinIO fora da transação**: publicar algo que não é DRAFT move o arquivo antes do erro; apagar
   documento com aceites apaga o arquivo e depois falha na FK.
4. **Sem autenticação** (não há Spring Security); `X-Company-Id` obrigatório em todas as rotas.
5. Manifesto público sempre em `public-legal/global/`; o front lê direto do MinIO, mas em prod isso
   já está quebrado (sem DNS/Ingress e a policy pública só cobre `published/`).
6. Credenciais do MinIO fixas no `application.yml`; RabbitMQ literal no Deployment.

## Decisões que são suas

| # | Decisão | Recomendação |
|---|---|---|
| D1 | Paridade de rotas | **Sim, 24.** |
| D2 | Lib | **Por tag** (`lib-go-common` v0.1.0+) + `vendor/`. |
| D3 | Fase 1 na lib | **Levar `oplog` e o coletor de ator da auditoria para a `lib-go-common` (v0.2.0).** Hoje copiados em ms-company-go e ms-auth-go; ms-user, ms-communication e ms-knowledge também usam. Os dois Go em prod continuam com a cópia até o próximo deploy deles (não mexo agora). |
| D4 | Formato de erro | **Igual ao Java** (`{timestamp,status,error,message,errors}`). |
| D5 | Exclusão LGPD (DELETE e consumer) | **Corrigir: apagar de verdade**, na transação. Consumer: mesma fila e argumentos (`ms.user-consents.user-erasure`, sem args), sem DLQ; erro vira log `error` + métrica. |
| D6 | Bugs — lista fechada | (a) erro de framework → 400; (b) "não encontrado" 500 → 404 e tenant 500 → 403, mesmas mensagens; (c) `findLatest` com várias linhas → a mais recente (hoje 500 após reaceite); (d) invalidar cache de compliance ao publicar/arquivar e todas as chaves do usuário no delete-all; (e) auditoria com id do recurso e placeholders resolvidos; exclusão via fila com ator `SYSTEM`. O resto do §14 fica igual e documentado. |
| D7 | Métricas | Sem id de documento como rótulo; o Go passa a ter `api_requests_total` por endpoint. |
| D8 | Consistência MinIO × banco | **Validar antes de tocar no storage; delete: banco primeiro, arquivo depois; mover com CopyObject no servidor.** Evita perder arquivo. |
| D9 | Manifesto | **Igual ao Java** (só `global`, mesmo JSON, mesma regra de `mandatory`, gerado no publish/archive, não no delete), mas **depois do commit** e com métrica de falha. |
| D10 | Credenciais | MinIO e RabbitMQ **via configmap/secret** (nada fixo). |
| D11 | Manifesto público quebrado em prod (DNS/Ingress/policy) | **Fora de escopo**: registrar e tratar à parte. |
| D12 | Rotas sem JWT | **Paridade** (continuam públicas, chamadas só pelo bff-core). |

Corte: como só o bff-core chama e não há usuário agora, sugiro o mesmo do ms-auth (trocar o selector e
remover o Java), mas a decisão é sua.

## Pendência antes da Fase 2

Schema real de prod. Rodar da raiz do monorepo:

```bash
kubectl --kubeconfig keepguard-core/docker/keepguard-kubeconfig.yaml -n keepguard exec \
  $(kubectl --kubeconfig keepguard-core/docker/keepguard-kubeconfig.yaml -n keepguard get pod -l app=postgres -o name | head -1) -- \
  pg_dump -s -n ms_user_consents -U keepguard_api_user keepguard_api_db > keepguard-core/backend/ms/ms-user-consents/docs/migracao-go/ms_user_consents_schema_prod.sql
```
