# Sync upstream 2026-09 — `self-hosted-v1.4.0`

> **Stato: MERGE ESEGUITO E TESTATO**, immagini `-rc1` pubblicate, deploy live da fare.
> Branch di lavoro: `sync-upstream` (ricreato da `self-hosted` il 2026-09-17).
> Segue la procedura di [upstream-sync-plan.md](upstream-sync-plan.md), §5.

## 1. Cosa è stato integrato

**88 commit** di `Grashjs/cmms` (base comune `4e307282`, 31 ago → `869d648f`, 16 set).
Temi principali: Sentry (17 commit), test nuovi (13), CI (8), Part/PartService (7),
Microsoft Clarity (4), validazione invito per super admin (3), fix upload file.

Dopo il merge il branch è **0 commit indietro** da upstream.

## 2. Conflitti (2, entrambi risolti)

| File | Natura | Risoluzione |
|---|---|---|
| `controller/RequestController.java` | upstream ha spostato la logica di `create` nel service (`@CurrentUser`, controller sottile) | Adottato il refactor upstream; il nostro check di **asset scoping v1.3.0** spostato in `RequestService.create(RequestPostDTO, User)`, dove `assetService` era già iniettato. Comportamento invariato (403 su asset fuori scope), e ora protegge anche gli altri chiamanti. |
| `service/FileService.java` | import e campi adiacenti | Unione. Rimossi gli import `Autowired`/`Lazy` rimasti inutilizzati. |

## 3. Deviazioni deliberate da upstream

1. **MinIO non aggiornato.** Upstream passa a `alpine/minio:RELEASE.2025-10-15` + `user: "0:0"`.
   Non adottata: immagine diversa e release avanti di 6 mesi su un bind-mount che contiene
   tutti gli allegati, senza downgrade garantito del formato dati → romperebbe il rollback.
   **Inoltre il repo Docker Hub `minio/minio` non esiste più (404)**: l'immagine vive solo
   nella cache del server e su `quay.io/minio/minio`. Conseguenza operativa: `docker compose pull`
   **senza argomenti fallisce** → pullare solo `api frontend`.
2. **`sentry.send-default-pii`** parametrizzato a `${SENTRY_SEND_PII:false}`. Upstream lo
   hardcoda a `true`: con un DSN configurato spedirebbe dati utente fuori sede.
3. Sentry e Clarity restano **disattivati** (DSN/ID vuoti), coerente con un'installazione
   self-hosted. Se un giorno si abilitano, va aggiornata la CSP in `nginx.conf` (upstream ci
   aggiunge `sentry.io` in `connect-src`) — il file è montato dall'host, non cambia con lo swap.

## 4. Impatto sul database

Una sola migrazione Liquibase, `2026_09_09_1788956680_user_settings_language.xml`:

```
addColumn  user_settings.language (int, nullable)
dropColumn user_settings.language        <- la stessa, appena creata
addColumn  own_user.language (int, nullable)
```

**Effetto netto: una colonna `int` nullable in più su `own_user`.** Nessun dato preesistente
toccato. Verificato sul dump reale di produzione (2026-09-17): 813 → **815** changeset,
`own_user.language` creata, `ddl-auto: validate` passato.

**Rollback verificato sul campo:** il backend `self-hosted-v1.3.0` è stato avviato sul DB già
migrato ed è partito regolarmente (Hibernate `validate` ignora le colonne extra).

## 5. Test

### 5.1 Funzionali, sui dati reali di produzione

Stack completo ricostruito con la topologia di produzione (Postgres 16 + MinIO + api +
frontend + nginx), restore del dump e degli allegati:

| Verifica | Esito |
|---|---|
| Login | ✅ |
| Asset / WO / richieste / parti / location | ✅ 152 / 6 / 3 / 3 / 30, identici a produzione |
| Allegati via nginx `/storage` (URL firmati MinIO) | ✅ 4/4, byte identici al backup |
| **Asset visibility scoping v1.3.0** | ✅ A/B sullo stesso utente: **152** asset con ruolo `Administrator`, **1** con `Rola Bratex` (view ASSETS senza viewOther) |
| Migrazione Liquibase + `validate` | ✅ |
| Rollback a v1.3.0 su DB migrato | ✅ |

### 5.2 Suite Maven — confronto a tre

| Branch | Tests | Failures | Errors | Build |
|---|---|---|---|---|
| `upstream/main` puro | 307 | 0 | 0 | ✅ |
| `self-hosted` **pre-merge** | 304 | **20** | 9 | ❌ |
| `self-hosted` + merge, prima dei fix | 307 | **20** | 21 | ❌ |
| `self-hosted` + merge, **dopo i fix** | 307 | 0 | 0 | ✅ |
| `self-hosted` + merge, **suite completa** | **1872** | **0** | **0** | ✅ |

**Il merge non ha introdotto regressioni**: le 20 failures erano identiche prima e dopo. Erano
test upstream ereditati in un sync precedente senza adattarli al comportamento self-hosted, e
la v1.3.0 è andata in produzione con quelli rossi. Adattati ora:

- `WorkOrderServiceTest` — mancava il `@Mock` di `SuperAccountRelationRepository`, dipendenza
  **nostra** di `WorkOrderService` (fix Bug 3, lettura session-safe delle relazioni super-account
  via query invece di navigare la collection LAZY). Aggiunto il mock + stub nei 2 test che
  richiedono relazioni non vuote. → 21 errors risolti.
- `MainLayoutConsumerTemplatesTest` (16) e `MainLayoutTemplateTest` (3) — asserivano il logo
  come URL remoto `api.host/images/logo.png`; noi usiamo `cid:logo` (immagini inline, così i
  client di posta non le bloccano). Asserzioni portate sul CID.
- `AuthControllerTest.logout` — verificava `invalidateSessions`; il nostro `logout` chiama
  `invalidateSessionsById` (fix Bug 1). Verify aggiornato.

**Suite completa rieseguita col socket Docker montato** (`-v /var/run/docker.sock:/var/run/docker.sock`,
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`): **1872 run / 0 failures / 0 errors**, inclusi
gli 8 `*IntegrationTest` che nei giri precedenti non partivano per assenza del socket. Nessun test
rosso resta sul branch.

## 6. Deploy

Vedi `docker-compose.prod.yml` (ora tracciato). **Attenzione: non basta cambiare i tag** —
il frontend v1.4.0 richiede 4 variabili nuove, altrimenti il container esce con
`Error getting 'CLARITY_ID' from process.env`. Sono già nel compose con default, quindi
il `.env` non va toccato (verificato sul `.env` reale di produzione).

```bash
docker exec atlas_db pg_dump -U atlas atlas > atlas_$(date +%F).sql   # backup obbligatorio
# compose: api+frontend -> :self-hosted-v1.4.0-rc1  (o ATLAS_VERSION nel .env)
docker compose pull api frontend && docker compose up -d && docker compose restart nginx
# rollback: rimetti :self-hosted-v1.3.0 e ripeti pull/up/restart
```

## 7. Cadenza

Confermata la lezione di [upstream-sync-plan.md](upstream-sync-plan.md) §7: 32 commit → 4
conflitti, 88 commit → 2 conflitti ma molto più lavoro di **adattamento dei test** upstream.
Sincronizzare spesso e in piccoli batch, e adattare i test nello stesso sync invece di
ereditarli rossi.
