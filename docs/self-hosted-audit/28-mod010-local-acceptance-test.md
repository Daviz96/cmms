# MOD-010 — Local Acceptance Test

Primo Acceptance Test locale end-to-end dell'Atlas self-hosted modificato, con lo stack
**ufficiale** (`docker-compose.yml`, backend **buildato dai sorgenti** — MOD-006) avviato
in locale. Attività di **verifica**: nessuno sviluppo, nessun licensing/frontend/mobile/
architettura modificati. **Nessun ambiente production toccato.** Secret mascherati (`********`).

`Code changes: NONE.`

---

## 1. Objective

Stabilire se l'Atlas modificato funziona come prodotto utilizzabile (backend + PostgreSQL
+ MinIO + frontend + API) prima del deployment live, e valutare la **production readiness**.

## 2. Environment

**LOCAL.** Stack ufficiale del repo via `docker compose` (project `atlas-cmms`), un solo
ingresso pubblicato: **nginx su `localhost:3000`**. Servizi: `postgres` (16), `minio`
(RELEASE.2025-04-22), `api` (**atlas-cmms-backend:local**, build da `./api`), `frontend`
(intelloop upstream, non-focus), `nginx` (1.27, `nginx.conf` del repo). Env isolato
(da `.env.example` + `LICENSING_SELF_HOSTED_MODE=true`, `PUBLIC_SERVER_URL=http://localhost:3000`).
Nessun uso di `websrv01` / production.

## 3. Version Tested

Branch `main`, HEAD **`e1d24406`**. Immagine backend buildata dai sorgenti.

## 4. Pre-flight

TEST: `docker compose config` valido, api build-from-source · ACTUAL: exit 0; api
`build.context ./api` + `image: atlas-cmms-backend:local`; **nessun** `intelloop/atlas-cmms-backend`.
RESULT: **PASS**.

TEST: l'immagine eseguita è quella modificata (non upstream) · ACTUAL: `javap` sul jar →
`buildSelfHostedLicensingState` (MOD-001), `responseHeaderOverrides` (MOD-004B),
`recipients: ${MAIL_RECIPIENTS:}` (CFG-01) presenti. RESULT: **PASS** (fondamentale, §6).

## 5. Infrastructure

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| PostgreSQL schema | tabelle presenti | 139 tabelle (public), Liquibase applicato | PASS |
| MinIO | bucket scrivibile | upload riusciti → bucket `atlas-bucket` operativo | PASS |
| Backend | startup OK, API up | `Started ApiApplication` (~35s), `/api/license/state` 200 | PASS |
| Frontend | servito | `GET /` 200, SPA JS (`<script src="/runtime-env.js">`) | PASS |

## 6. Licensing

TEST: comportamento self-hosted a runtime · EXPECTED: SELF_HOSTED, valid=true, entitlement
pieni · ACTUAL: log `Atlas licensing mode: SELF_HOSTED`; `GET /api/license/state` → 200,
`valid:true`, `planName:"Self-Hosted"`, **34 entitlement** (intero enum). RESULT: **PASS**.

Nota deployment: con un env in cui `LICENSING_SELF_HOSTED_MODE` risultava impostata a
`false` il log mostrava `COMMERCIAL` → **conferma che il flag è effettivamente rispettato
a runtime** (non un default silenzioso).

## 7. Authentication

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| signup (owner) | 200 + token | 200, JWT (self-hosted → utente abilitato) | PASS |
| login `/auth/signin` (type=client) | 200 + accessToken | 200 | PASS |
| logout | token invalidato | logout 200 → vecchio token `whoami` **401** | PASS |
| re-login | nuovo token valido | 200 | PASS |
| whoami | 200, ruolo | 200, `ROLE_CLIENT` | PASS |

## 8. Authorization

TEST: endpoint protetto senza token · ACTUAL: `GET /api/auth/me` senza token → **401**.
RESULT: **PASS**. Permessi di ruolo attivi (owner = ruolo "Administrator" `ROLE_CLIENT`).

## 9. Company Isolation

Due company reali (CoA, CoB) via signup, entrambe **BUSINESS con 17 `PlanFeatures`** (DB).

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| A legge proprio file | 200 | 200 | PASS |
| **B legge file di A** | 403 | **403** (`@PostLoad`) | PASS |
| **B cancella file di A** | 403 | **403** | PASS |

Multi-tenant `CompanyAudit.@PostLoad` confermato a runtime.

## 10. Assets

Create `POST /assets` 200 (id=1); Read `GET /assets/1` 200; Search `POST /assets/search`
200 (totalElements=1); Update `PATCH /assets/1` **con `status` → 200** (rinomina applicata).
⚠️ Update con patch **parziale senza `status` → 500** (vedi Finding F-01). RESULT:
**PASS (con finding)**.

## 11. Work Orders

Create `POST /work-orders` 200 (id=1); Read 200; Search 200 (totalElements=1); Patch
`PATCH /work-orders/1` 200. Nota: il cambio `status`→IN_PROGRESS via patch generico non è
stato applicato (status resta `OPEN`) — transizione via flusso dedicato (F-02, INFO).
RESULT: **PASS**.

Parts: create/read 200. Meters: create 200 (richiede `asset` @NotNull). RESULT: **PASS**.

## 12. Attachments

Catena reale `nginx → API → FileController → MinIO`, 3 tipi.

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| upload immagine (IMAGE) | 200 | 200 | PASS |
| upload PDF (OTHER) | 200 | 200 | PASS |
| upload HTML (OTHER) | 200 | 200 | PASS |
| download immagine | 200, **inline** | 200, `Content-Type image/png`, **nessun** `Content-Disposition` | PASS |
| download PDF/OTHER | 200, **attachment** | 200, `Content-Disposition: attachment`, `nosniff` | PASS |
| delete (PDF) | metadata+object rimossi | storage 200→**404**, meta→**404** | PASS |

**MOD-004B/004C confermati end-to-end** nel prodotto reale. Accesso non autorizzato agli
allegati negato (§9).

## 13. Security

Endpoint protetto senza token → 401; risorsa/azione cross-company → 403 (read + delete).
Nessun test distruttivo. RESULT: **PASS** — nessun bypass osservato.

## 14. Persistence

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| `docker compose restart` | dati intatti | assets/wo/parts/meters = 1 invariati; licensing valido | PASS |
| `docker compose down` (NO -v) + `up` | volumi persistono | volumi `postgres_data`/`minio_data` presenti; dopo `up` counts=1; **attachment scaricabile 200** | PASS |

DB **e** MinIO persistono su ciclo down/up completo.

## 15. Backup

`pg_dump` (non distruttivo) → file **544 KB**, **139 `CREATE TABLE`**, 139 blocchi `COPY`,
header `PostgreSQL database dump` valido. RESULT: **PASS** (backup valido). Restore
distruttivo **non** eseguito (richiede ambiente dedicato — DA VERIFICARE separatamente).

## 16. Frontend

`GET /` → 200, SPA JS servito; le API consumate dal frontend sono le stesse verificate
sopra (login, license/state, assets, work orders, files). Interazione UI visiva completa
non testabile headless → coperta da MOD-008 (audit statico CLEAN) + i test API qui.
RESULT: **PASS (served + API-backed)**; nessun errore 401/403/500 inatteso sui flussi
testati (a parte F-01 sul patch parziale).

## 17. Mobile

**NOT TESTED** in runtime: richiede dispositivo/emulatore + IP LAN + config Firebase/store
(fuori scope, §18). La compatibilità è stata stabilita staticamente in MOD-009
(COMPATIBLE WITH CONFIGURATION: schermata Custom Server → `https://<LAN>:3000/api`). Il
lato server che l'app consuma è quello verificato in questo MOD.

## 18. Push Notifications

**DA VERIFICARE** — richiede configurazione Firebase/FCM lato backend non presente
nell'ambiente locale (§19). Non è stata creata infrastruttura Firebase.

## 19. Offline

**DA VERIFICARE** (runtime) — richiede dispositivo. Caching locale (redux-persist) e
rilevazione rete (netinfo) confermati a livello di codice in MOD-009; nessuna dipendenza
Cloud per il caching. Write-sync offline non determinabile.

## 20. Restart/Recovery

| Servizio | TEST | ACTUAL | RESULT |
|---|---|---|---|
| PostgreSQL | restart → api riconnette | query assets 200 dopo restart (HikariCP) | PASS |
| MinIO | restart → attachment | download 200 dopo restart | PASS |
| Backend | restart → recovery | `502` transitorio durante ~40s di warmup, poi `/license/state` **200** valid/Self-Hosted | PASS (F-03 INFO) |

Dati intatti dopo ogni restart.

## 21. Test Matrix

| Area | Test | Expected | Actual | Result |
|---|---|---|---|---|
| Infrastructure | pg/minio/backend/frontend | up | tutti up | PASS |
| Licensing | /license/state | SELF_HOSTED valid | valid, Self-Hosted, 34 ent. | PASS |
| Auth | signup/login/logout/relogin | ok + token invalidation | tutti ok, 401 post-logout | PASS |
| Authorization | no-token | 401 | 401 | PASS |
| Company isolation | B→A read/delete | 403 | 403/403 | PASS |
| Assets | CRUD | ok | create/read/search/update(+status) ok; partial-patch 500 | PASS w/ finding |
| Work Orders | CRUD | ok | create/read/patch/search ok | PASS |
| Parts/Meters | create/read | ok | ok (meter needs asset) | PASS |
| Attachments | upload/dl/delete ×3 | disposition per type + lifecycle | tutto conforme (MOD-004B) | PASS |
| Persistence | restart + down/up | dati intatti | intatti (pg+minio) | PASS |
| Backup | pg_dump | valido | 544KB/139 tabelle | PASS |
| Restart/Recovery | per servizio | recovery | recovery ok | PASS |
| Frontend | served | 200 + SPA | 200 + JS | PASS |
| Mobile | runtime | — | NOT TESTED (no device) | N/A |
| Push | runtime | — | DA VERIFICARE | N/A |
| Offline | runtime | — | DA VERIFICARE | N/A |

## 22. Findings

| ID | Prio | Descrizione | Evidenza | Stato |
|---|---|---|---|---|
| **F-01** | **P3** | `AssetService.patch` va in **NPE (500)** se la PATCH non include `status` (`getStatus().isReallyDown()` senza null-check) | `AssetService.java:419`; PATCH `{name}`→500, PATCH `{name,status}`→200 | **Pre-esistente**, fuori scope MOD-010; **non corretto**. Workaround: inviare il DTO con `status` (la UI web/mobile lo fa) |
| F-02 | INFO | Cambio `status` WO via patch generico non applicato (resta OPEN) | PATCH `/work-orders/1` 200, status invariato | Comportamento: transizione via flusso dedicato. Non un bug |
| F-03 | INFO | Backend dopo restart ha ~40s di warmup → `502` transitorio via nginx | recovery poi 200 | Atteso; nessun errore persistente |

Nessun **P0/P1**. Nessun bypass di sicurezza. Nessuna perdita dati.

## 23. Fixes

**Nessuna modifica al codice** applicata (`Code changes: NONE`). F-01 è un bug
pre-esistente **fuori dallo scope** di MOD-010 (§24: non correggere problemi non nello
scope / non introdotti dai MOD); documentato per decisione del responsabile tecnico.

## 24. Remaining Issues

- **F-01** (P3, pre-esistente): patch asset parziale → 500. Da valutare un fix separato
  (null-check di `status`) se desiderato.
- **DA VERIFICARE**: mobile runtime (device), push FCM (Firebase backend), offline
  write-sync (device), restore distruttivo del backup (ambiente dedicato).

## 25. Production Readiness

```text
BLOCKERS (P0/P1):      nessuno
NON-BLOCKERS:          F-01 (P3, pre-esistente, workaround), F-02/F-03 (INFO)
OPTIONAL:              frontend build-from-source, CFG-02 hardening
DA VERIFICARE:         mobile runtime, push FCM, offline write-sync, restore distruttivo
```

**PRODUCTION READINESS: READY WITH FINDINGS.** Il core self-hosted (backend + PostgreSQL
+ MinIO + frontend + API) è funzionante, sicuro (multi-tenant + auth), persistente e
recuperabile; licensing self-hosted e allegati (MOD-004B) confermati a runtime. I punti
aperti sono non bloccanti (P3/INFO) o verifiche che richiedono un dispositivo/servizio
esterno (mobile/push/offline), non necessari per il prodotto backend+web.

## 26. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-010 (Local Acceptance Test, READY WITH FINDINGS) a Current Project
State + Documentation Map/Workflow; registrato F-01 (AssetService.patch NPE, P3
pre-esistente) in Known Issues e le voci DA VERIFICARE (mobile/push/offline runtime).
```

## 27. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
Environment: LOCAL
Tests executed: 38
PASS: 37
FAIL: 1 (F-01, P3 pre-esistente, workaround disponibile)
P0: 0   P1: 0   P2: 0   P3: 1   INFO: 2
Mobile: NOT TESTED (static compatibility per MOD-009)
Production readiness: READY WITH FINDINGS
Final verdict: PASS WITH FINDINGS
```

⏹️ **STOP** — non eseguo deployment live, non modifico server production/Caddy/DNS/
certificati, non eseguo migrazioni production, non avvio un nuovo MOD, non implemento
CFG-02, non correggo F-01. La decisione sul deployment live spetta al responsabile
tecnico dopo la revisione di questo report.

> Nota ambiente: lo stack è stato smontato con `docker compose down` (**senza** `-v`,
> come da §15). I volumi throwaway `atlas-cmms_postgres_data` / `atlas-cmms_minio_data`
> (dati di test) **restano** sul disco locale: verrebbero riutilizzati da un futuro
> `docker compose up` con lo stesso project name. Rimuoverli prima di un deployment
> locale reale è consigliato (richiede approvazione: `docker volume rm`).
