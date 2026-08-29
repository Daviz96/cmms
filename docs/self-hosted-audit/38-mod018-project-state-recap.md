# MOD-018 — Comprehensive Project State Recap

Checkpoint generale del progetto **Atlas CMMS self-hosted**, ricostruito dalla documentazione
persistente in `docs/` (fonte primaria; gerarchia: verification recente > implementation recente >
decisioni approvate > audit > storico > memoria). Task di **sola documentazione**: nessuna modifica
a codice/backend/frontend/mobile/DB/Docker/Caddy/DNS/SSL; nessun commit/push. L'unico file di
progetto aggiornato oltre a questo report è `CLAUDE.md` (checkpoint). Convenzioni anti-hallucination:
`NOT DOCUMENTED`, `NOT VERIFIED`, `CONFLICT` usati esplicitamente dove pertinente.

```text
Code changes: NONE
Documentation files analyzed: 66 .md in docs/ (00–37 report + prompt MOD-001…018 + CLAUDE.md + audit prompt)
MODs identified: 17 numerati (001–017) + sotto-varianti 003A, 004B, 004C, 014A, 014B
```

---

## 1. Executive Summary

**Dove siamo.** L'obiettivo *licensing-unlock* — il cuore del progetto — è **completo e verificato a
runtime**. Il prodotto **backend + web** è **READY WITH CONDITIONS** per il go-live (MOD-013): nessun
blocker P0/P1, restano solo attività operative di provisioning. Il lavoro delle ultime sessioni si è
spostato su **mobile** (ambiente di test agent reso operativo, 1 bug corretto) e **i18n polacco** (due
audit, mobile completamente ripulito e verificato a runtime).

**Cosa funziona (verificato live).** Self-hosted licensing (`SELF_HOSTED`, 34 entitlement, no Keygen),
piano BUSINESS + 17 `PlanFeatures`, auth (signup/login/logout), autorizzazione, **isolamento
multi-tenant** (`@PostLoad` 403 cross-company), CRUD CMMS (assets/WO/parts/meters), allegati con
hardening stored-XSS + lifecycle MinIO, persistenza volumi, backup/restore. Suite backend
**1446/1446**. Frontend web audit **CLEAN** (nessuna modifica necessaria). Mobile Android: build
release + install + smoke + regression PASS su emulatore; i18n polacco verificato a runtime.

**Cosa abbiamo già sbloccato.** Tutte le feature "premium" prioritarie (erano già implementate in
AGPLv3, bloccate dal solo gate Keygen): assets/users/locations/parts/WO/PM/meters/checklists
illimitati, asset hierarchy, custom roles, WO history, downtime, time/cost tracking, WO linking,
signature, request portal, webhook/API, branding, ecc. LDAP/AD è implementato e hardened (gate `SSO`).

**Problemi principali.** (1) **Deployment production non ancora eseguito**: dominio/DNS/TLS/Caddy non
definiti nel repo; secret ancora d'esempio; **modifiche MOD non committate** (HEAD fermo a MOD-013).
(2) **Mobile**: il responsabile ha segnalato "alcuni bug" — solo M-BUG-1 è stato trattato finora; iOS
non testabile dall'agent. (3) **F-04** (P3, partial-patch, pre-esistente, mobile impact NONE).

**Quanto manca all'obiettivo.** Il *software* è sostanzialmente pronto. Manca l'**esecuzione
operativa del go-live** (provisioning infra + rotazione secret + commit dei sorgenti) e, se si vuole
"mobile production-ready", un ciclo di **bug-fix mobile** più ampio. Nessuna feature prioritaria resta
bloccata dal licensing.

**Prossimo passo consigliato.** USER DECISION tra: (A) **deployment production** (risolvere
GL-1/GL-2/GL-3 + dominio/TLS — richiede accesso server + decisioni owner); (B) **audit/fix bug
mobile** (ora che l'ambiente agent è AVAILABLE); (C) **hardening del repository** (commit/branch dei
MOD, GL-2). Vedi §24.

---

## 2. Final Project Objective

Ricostruito da `atlas-self-hosted-audit-prompt.md` (§1, §20) e `00-executive-summary.md` — **documentato
esplicitamente**.

> Produrre una build **Atlas CMMS self-hosted** per l'infrastruttura interna aziendale, che renda
> disponibili le funzionalità bloccate da entitlement/licensing in modo **pulito, prevedibile,
> testabile e aggiornabile** — **non** semplicemente facendo sparire i controlli di licenza — e
> **preservando** sicurezza, autorizzazione e multi-tenancy.

Scoperta chiave dell'audit (doc 00): quasi tutte le feature premium sono **già implementate** nel
sorgente AGPLv3 e bloccate da un **unico gate centrale** (`LicenseService.hasEntitlement()`, Keygen); il
secondo gate (`PlanFeatures`) è già aperto in self-hosted (ogni nuova azienda → piano `BUSINESS` senza
scadenza). Quindi la strategia approvata è una **modalità self-hosted centralizzata**
(`licensing.self-hosted-mode=true`) invece di bypass sparsi.

Target funzionale finale (doc §20): Atlas con assets illimitati, hierarchy, WO, PM, meters, checklists,
parts, attachments, WO history, labor, costs, downtime, custom roles, **LDAP/AD**, email, API, webhooks,
su **PostgreSQL + MinIO + Docker + Caddy/reverse proxy**. App mobile utilizzabile contro il backend
self-hosted.

Obiettivo mobile/i18n: `NOT EXPLICITLY DOCUMENTED` nel prompt originale — sono emersi come estensioni
operative (MOD-014→017) dopo che il responsabile ha verificato l'app su device reale e richiesto la
qualità del polacco.

---

## 3. Documentation Inventory

66 file `.md` in `docs/`. Categorie:

| Categoria | File (prefisso/numero) |
|---|---|
| **Mandato/goal** | `atlas-self-hosted-audit-prompt.md`, `00-executive-summary.md` |
| **Audit fase-1 (feature/licensing)** | `01-license-entitlements`, `02-backend-feature-gates`, `03-frontend-feature-gates`, `04-feature-matrix`, `05-ldap-ad`, `06-storage-attachments`, `07-maintenance-pm`, `08-work-orders`, `09-asset-management`, `10-security-considerations`, `11-modification-plan`, `12-test-plan` |
| **Implementation/Verification MOD** | `13/14` (MOD-001), `15` (MOD-002), `16/17` (MOD-003/003A), `18/19/19b/20/21/21c` (MOD-004…004C), `23` (MOD-005), `24` (MOD-006), `25` (MOD-007), `26` (MOD-008), `27` (MOD-009), `28` (MOD-010), `29` (MOD-011), `30` (MOD-012), `31` (MOD-013), `32/33/34` (MOD-014/014A/014B), `35` (MOD-015), `36` (MOD-016), `37` (MOD-017) |
| **Consolidamento** | `22-audit-consolidation` (report), `22-audit-consolidation-gap-analysis` (**in realtà un PROMPT**, vedi §21) |
| **Prompt MOD** (root `docs/`) | `MOD-001…MOD-018-*.md` (istruzioni, non report) |
| **Checkpoint** | `CLAUDE.md` (sintesi di stato mantenuta ad ogni MOD) |

Nota: la cartella `docs/self-hosted-audit/` contiene report **numerati progressivamente 00→37**; i file
`MOD-xxx-*.md` nella root `docs/` sono i **prompt** dei rispettivi MOD (non vanno confusi con i report
omonimi).

---

## 4. MOD Timeline

Stati: `PASS`, `PASS WITH FINDINGS`. Nessun MOD `BLOCKED`/`FAIL`/`IN PROGRESS`. Nessun MOD
funzionalmente `SUPERSEDED` (alcuni *documenti* di audit sono superati, §21).

| MOD | Titolo | Obiettivo | Stato | Verifica | Doc |
|---|---|---|---|---|---|
| 001 | Self-hosted licensing | Modalità centralizzata: concede entitlement senza Keygen | **PASS** | unit + runtime (MOD-005) | 13/14 |
| 002 | Commercial limits | Sblocco 8 limiti `UNLIMITED_*` | **PASS** | suite 1430 | 15 |
| 003 | LDAP/AD audit | Mappare LDAP esistente | **PASS** (audit) | doc 16 | 16 |
| 003A | LDAP hardening | Logging/test/.env, no redesign | **PASS** | suite 1439 (+9 test) | 17 |
| 004 | Attachment audit | Audit allegati/storage/MinIO | **PASS** (audit) | doc 18 | 18 |
| 004B | Attachment security | Mitigazione stored-XSS + delete lifecycle | **PASS WITH FINDINGS** | suite 1445; indip. doc 20 | 19/20 |
| 004C | Storage e2e | Verifica runtime storage/proxy (MinIO+nginx reali) | **PASS** | runtime live | 21c |
| 005 | Runtime integration | Backend buildato dai sorgenti verificato live | **PASS WITH FINDINGS** | runtime (licensing/BUSINESS/attachments/tenant) | 23 |
| 006 | Deployment alignment | Compose builda backend da sorgente | **PASS** | smoke + 1445 | 24 |
| 007 | Documentation baseline | Riallineo CLAUDE.md / baseline doc | **PASS** (documental) | — | 25 |
| 008 | Frontend licensing audit | Audit web feature-gate | **PASS — CLEAN** | analisi (no change) | 26 |
| 009 | Mobile compatibility audit | App RN/Expo vs self-hosted | **PASS — COMPATIBLE** | analisi (no change) | 27 |
| 010 | Local acceptance test | E2E stack ufficiale locale | **PASS WITH FINDINGS** | 37/38 live; F-01 | 28 |
| 011 | F-01 fix | NPE `AssetService.patch` senza `status` | **PASS — RESOLVED** | +1 test; 1446; runtime | 29 |
| 012 | Mobile runtime acceptance | Contratto mobile + impatto F-04 | **PASS WITH FINDINGS** | GUI NOT TESTED (agent); F-04=NONE | 30 |
| 013 | Go-live readiness | Prontezza pre-deploy | **PASS WITH FINDINGS — READY WITH CONDITIONS** | verifica/doc | 31 |
| 014 | Mobile agent test env (assess) | Fattibilità test GUI via agent | **PASS WITH FINDINGS — NOT AVAILABLE** | assessment | 32 |
| 014A | Android test env setup | Install SDK/AVD/emulatore/ADB | **PASS WITH FINDINGS — PARTIAL** | runtime env (blocker Firebase) | 33 |
| 014B | Firebase + Android build | Build/install/smoke con Firebase reale | **PASS WITH FINDINGS — AVAILABLE** | smoke test PASS su AVD | 34 |
| 015 | Mobile bug fix | M-BUG-1 (`getApiUrl` endsWith) | **PASS — RESOLVED** | release APK + regression | 35 |
| 016 | Polish translation audit | Qualità traduzioni PL (mobile) | **PASS** | 67 fix; runtime PL; +email/home | 36 |
| 017 | Polish i18n key audit | Literal key PL (fallback) | **PASS** | 7 key +3 fix; runtime PL | 37 |

---

## 5. Current Architecture

Verificata da `docker-compose.yml`, `nginx.conf`, doc 31 §8.

```text
Internet
  │ (TLS)  [reverse proxy esterno: Caddy/Traefik/NPM/Cloudflare — NON nel repo, OPEN]
  │ HTTP
nginx (atlas_nginx, host :3000 → :80)  ← unico ingresso pubblico (single-ingress)
  ├── /         → frontend:3000  (React SPA, immagine upstream intelloop)
  ├── /api/     → api:8080        (Spring Boot, immagine atlas-cmms-backend:local, source-built)
  └── /storage/ → minio:9000      (allegati, presigned URL)
postgres (atlas_db, :5432 expose)   ─ non pubblicato
minio    (atlas_minio, :9000/:9001 expose) ─ non pubblicato
```

Stack: Java 17 / Spring Boot 3.5.16, PostgreSQL 16, MinIO, nginx 1.27, Docker Compose. Mobile: React
Native 0.79.6 / Expo SDK 53 (Hermes, dev-client). Home marketing: Next.js/next-intl. Solo nginx pubblica
una porta; DB/MinIO/API/frontend restano nella rete Docker interna.

---

## 6. Backend State

`STATUS: PASS (verificato live).`

- **Modificato (MOD-001/002/004B/011):** `LicenseService` (self-hosted mode centralizzato), MinIO/Storage/
  File security+lifecycle, `AssetService` (F-01 fix), `LdapSecurityConfig` (logging), `application.yml`
  (`mail.recipients` default vuoto), `.env.example` (set LDAP completo). `IMPLEMENTED + VERIFIED`.
- **Licensing:** `getLicensingState()` corto-circuita in self-hosted → `valid=true`, 34 entitlement,
  no Keygen. **VERIFIED** live (doc 23/28).
- **API/Auth/Authz:** signup/login/logout, 401 no-token, permessi ruolo. **VERIFIED**.
- **Multi-tenancy:** `CompanyAudit.@PostLoad` → 403 cross-company. **VERIFIED** (doc 23/28).
- **Database:** PostgreSQL 16, 139 tabelle, Liquibase; **nessuna migration/schema change** introdotta dai
  MOD. **VERIFIED** (persistenza doc 28).
- **Soft delete / Audit logging / Background jobs (Quartz, LDAP sync):** presenti, invariati. `NOT
  VERIFIED` live individualmente (fuori dallo scope dei MOD eseguiti) tranne dove toccati.
- **Test:** `mvnw test` **1446/1446** (baseline 1412→1430→1439→1445→1446).

---

## 7. Frontend Web State

`STATUS: PASS — audit CLEAN (nessuna modifica).`  (MOD-008, doc 26; confermato MOD-013)

`FRONTEND AUDIT STATUS: CONFIRMED.` L'audit frontend feature-gate/licensing **è stato eseguito** (doc 26):
il web **non** ha gate commerciali bloccanti per self-hosted — non contatta Keygen, non fa validazione
license client-side; i gate `useLicenseEntitlement`/`hasFeature` leggono lo **stato backend** (aperto in
self-hosted); `isCloudVersion` commuta solo billing/marketing cloud (nessun feature-hiding). **Nessuna
modifica frontend richiesta o effettuata.** Il frontend è servito e verificato nello stack locale (doc 28
§16). Immagine: `intelloop/atlas-cmms-frontend` **upstream** (non buildata da sorgente — OPEN/opzionale,
§20).

---

## 8. Mobile Android State

`STATUS: PARTIAL (test su emulatore PASS; non è verifica di produzione su device fisico).`

- **Ambiente:** JDK 17 (`C:\Users\dawid\Android\jdk-17.0.20.1+1`), Android SDK (`C:\Users\dawid\Android\Sdk`),
  adb 37.0.1, AVD `atlas_test` (API 35 x86_64, boot headless WHPX ~36 s), automazione ADB
  (`input`/`uiautomator`/`screencap`/`logcat`). **VERIFIED** (MOD-014A/B).
- **Firebase:** `google-services.json` reale fornito dal responsabile in `mobile/android/app/`
  (**gitignored**). **PRESENT.**
- **Build:** `npx expo run:android` (debug, MOD-014B) e `gradlew assembleRelease` (release JS bundled,
  MOD-015/017) → **BUILD SUCCESSFUL**; APK `app-release.apk` (~95 MB, gitignored).
- **Custom Server / Login / Regression:** contro `http://10.0.2.2:3000/api` — Login→Dashboard→Work
  Orders→Assets→Settings→Logout **PASS** (MOD-014B/015/017).
- **Bug:** M-BUG-1 (`getApiUrl` undefined `.endsWith`) **RESOLVED** (MOD-015). Il responsabile ha
  segnalato **"alcuni bug"** genericamente (doc 31 §4): **NOT DOCUMENTED** nel dettaglio → un audit bug
  mobile più ampio non è ancora stato fatto.
- **i18n:** polacco mobile verificato a runtime (MOD-016/017).
- **Codice mobile modificato:** `config.ts`, `slices/instanceConfig.ts` (MOD-015); `i18n/en.ts`,
  `i18n/pl.ts`, `navigation/index.tsx`, `screens/locations/EditLocationScreen.tsx`, `utils/fields.ts`
  (MOD-017).

Distinzione: **testato realmente** = build/install/smoke/regression/i18n su **emulatore**. **Non
testato** = device fisico Android da parte dell'agent; suite completa dei bug segnalati dal responsabile.

---

## 9. Mobile iOS State

`STATUS: NOT VERIFIED (agent).`

- **coding-agent test:** `NOT AVAILABLE` — l'host è Windows; iOS richiede macOS + Xcode (doc 30/31/32).
- **build verification (agent):** `NOT DONE`.
- **runtime verification (agent):** `NOT DONE`.
- **manual human test:** il **responsabile ha personalmente confermato** su device iOS reale la
  **connessione dell'app al backend self-hosted** (doc 31 §4). Questo **non** implica che l'intera app iOS
  sia verificata: è solo connettività. Bug specifici iOS: `NOT DOCUMENTED`.
- **automated test:** `NOT DOCUMENTED`.

---

## 10. Polish i18n State

`STATUS: PASS (mobile).`  (MOD-016 doc 36 + MOD-017 doc 37)

- **Audit effettuati:** 2 (MOD-016 qualità traduzioni; MOD-017 integrità chiavi/literal).
- **Correzioni MOD-016:** 67 fix in `mobile/i18n/translations/pl.ts` (4 placeholder `{{…}}` rotti, 2
  chiavi mancanti, ~61 mistranslation es. `save` "Ratować"→Zapisz, `meters` "Metry"→Liczniki). Integrità
  1331=1331. **Runtime PL verificato** su release build + regression.
- **Correzioni MOD-017:** detector statico → 11 literal key `t()` assenti da `en.ts` (fallback). 7 chiavi
  nuove en+pl (`Sign out`→Wyloguj się, `Version`→Wersja, `Dev Info`, `Build ID`, `informations`→Informacje,
  `hour`→godzina, `notifications`→Powiadomienia) + 3 code fix (riuso chiavi esistenti); `NFC` non-bug.
  Integrità **1338=1338** (0 missing/extra/dup/placeholder). **Runtime PL verificato** (Settings/
  Notifications/Profile) + regression.
- **Key integrity / literal keys / fallback / runtime test:** tutti coperti (mobile).
- **Problemi residui PL:** chiavi **dinamiche** `t(variabile)` fuori dallo scope del detector statico
  (nessun problema PL noto tra le chiavi statiche).
- **Fuori scope (per decisione):** backend email locale altri idiomi (solo 1 typo PL corretto, MOD-016);
  home app PL verificata staticamente (4 fix + 17 key); **altre 14 lingue non auditate**.

---

## 11. Licensing State

`STATUS: PASS (verificato live).`

```text
Cosa era bloccato:   quasi tutte le feature premium (34 entitlement) + doppio gate su 6 feature
Dove:                LicenseService.hasEntitlement() (gate A, Keygen) ; PlanFeatures (gate B, piano)
Cosa è stato modificato:  MOD-001 → self-hosted mode centralizzato in LicenseService (valid=true, enum completo, no Keygen)
                          MOD-002 → i limiti UNLIMITED_* vanno in short-circuit con l'entitlement concesso
Cosa è stato verificato:  runtime SELF_HOSTED, 34 entitlement, BUSINESS+17 PlanFeatures (incl. FILE), upload gate (MOD-005 doc 23; MOD-010 doc 28)
Cosa resta da verificare: nulla di bloccante — le feature dipendenti da servizi esterni non sono "licensing"
```

Distinzione corretta (doc 22 §6):
- **LICENSE RESTRICTION** (gate A/B): tutte sbloccate in self-hosted.
- **AUTHORIZATION / ROLE-PERMISSION**: invariate e legittime (non toccate).
- **BUSINESS RULE / EXTERNAL DEPENDENCY**: LDAP/AD, OAuth2, SMTP, MinIO restano gated dai propri **flag
  di configurazione** (concedere l'entitlement non avvia il servizio) — **non** è licensing.
- **Gate morti:** `ADVANCED_ANALYTICS`, `PARTS_COST_TRACKING` (entitlement mai applicati → già disponibili).

Nessun bypass "return true"; switch unico e centralizzato; licensing intatto per l'eventuale build cloud.

---

## 12. Infrastructure State

`STATUS: PARTIAL (locale/LAN PASS; production OPEN).`

| Componente | Stato | Livello |
|---|---|---|
| Docker Compose (stack ufficiale) | Verificato live | LOCAL |
| Backend `atlas-cmms-backend:local` | Source-built (MOD-006) | LOCAL |
| Frontend `intelloop/atlas-cmms-frontend` | Upstream (non source-built) | LOCAL |
| PostgreSQL 16 / MinIO | Verificati (persistenza volumi con nome) | LOCAL |
| nginx single-ingress | Verificato (`nginx.conf` repo) | LOCAL |
| **Dominio production** | **NOT DOCUMENTED** (nessun dominio Atlas nel repo) | OPEN |
| **DNS** | **NOT DOCUMENTED** (fuori dal repo) | OPEN |
| **TLS/SSL** | Meccanismo documentato (`dev-docs/Set up TLS.md`); certificato da provisionare | OPEN |
| **Caddy / reverse proxy** | Consigliato; **nessun `Caddyfile` nel repo** | OPEN |
| Percorsi storage host | Volumi Docker con nome (default); bind `/srv/data/...` solo nel prompt | UNKNOWN |
| `wiki.firmabratex.pl` | Citato solo nel prompt; **non nel repo** → config di `websrv01` esterna | UNKNOWN |

Backup/restore: **supportati** (`scripts/backup/atlas-backup.{sh,ps1}` + `dev-docs/Backup.md`; `pg_dump`
validato MOD-010; restore con safety copy `atlas_old`).

---

## 13. Git & Repository State

- **Repository:** fork locale di Atlas CMMS. `origin` = `git@github.com:Daviz96/cmms.git` (fork
  dell'utente); `upstream` = `https://github.com/Grashjs/cmms.git`.
- **Branch/HEAD:** `main` @ **`e1d24406`** ("refactor: update custom field value schema…", 2026-08-24) —
  **identico a quanto registrato in MOD-013**: **nessun commit è stato creato dopo MOD-013**. Tutto il
  lavoro MOD-001→017 è **non committato** nel working tree.
- **Working tree (attuale):** **21 file tracciati modificati** + **6 untracked** (5 nuovi file di test
  `*ServiceTest.java`/`SelfHostedUsageLimitsTest.java` + `docs/`):
  ```text
   M .env.example, application.yml, docker-compose.yml
   M api/.../LicenseService, AssetService, FileService, MinioService, StorageService, GCPService, FileMapper, LdapSecurityConfig
   M api/.../mailMessages_pl_PL.properties, AssetServiceTest
   M home/src/i18n/translations/pl.ts
   M mobile/config.ts, i18n/en.ts, i18n/pl.ts, navigation/index.tsx, screens/locations/EditLocationScreen.tsx, slices/instanceConfig.ts, utils/fields.ts
  ?? api/.../{FileServiceTest,LdapServiceTest,LicenseServiceTest,MinioServiceTest,SelfHostedUsageLimitsTest}.java
  ?? docs/
  ```
- **File sensibili / gitignored:** `.env` (secret), `mobile/android/app/google-services.json`
  (`mobile/.gitignore:19`), APK release (`android/app/build/...`). **Mai committati con valori reali.**
- **Commit policy:** commit/push **non** eseguiti come parte dei MOD, se non esplicitamente richiesti
  (regola standing). `docs/` è **interamente untracked** (i report non sono committati).
- **Implicazione go-live (GL-2):** la produzione builda il backend dai sorgenti → i sorgenti modificati
  devono essere resi disponibili sul build host (commit+push o trasferimento del working tree), altrimenti
  un `git pull` **non** includerebbe le modifiche MOD.

---

## 14. Testing State

| Livello | Stato | Evidenza |
|---|---|---|
| **Static** | AVAILABLE | i18n integrity/detector (MOD-016/017); `git diff --check`; tsc/prettier (MOD-015) |
| **Unit** | AVAILABLE | `mvnw test` **1446/1446** (LicenseServiceTest, LdapServiceTest ×9, AssetServiceTest, Minio/File/SelfHostedUsageLimits) |
| **Integration** | PARTIAL | via suite/Testcontainers; test integrazione allegati app-level **assenti** (gap P2, doc 22) |
| **E2E / Runtime** | AVAILABLE | stack locale (MOD-005/010), storage/proxy MinIO+nginx (MOD-004C), backend live |
| **Manual** | AVAILABLE | owner: web desktop + connessione iOS (doc 31) |
| **Mobile (agent)** | AVAILABLE (Android emulatore) | MOD-014B/015/017 (build/install/smoke/regression/i18n via ADB) |
| **Desktop/web GUI (agent)** | NOT DOCUMENTED come suite strutturata | frontend servito e verificato a livello contratto |
| **CI/CD** | NOT DOCUMENTED | nessuna pipeline descritta nei doc |

Nota: gap dichiarati con scope sufficiente **non** sono failure (es. MOD-004C copriva intenzionalmente il
solo seam storage/proxy).

---

## 15. Security State

`STATUS: PASS (con rotazione secret richiesta al go-live).`

- **Secret handling:** `.env` gitignored; `.env.example` solo placeholder; JWT/MinIO/SMTP/LDAP mai
  committati reali. **Secret di produzione ancora d'esempio** → GL-1 (rotazione richiesta).
- **Firebase:** `google-services.json` gitignored, mai stampato.
- **Authentication/Authorization:** verificati (401 no-token, permessi ruolo).
- **Tenant isolation:** `@PostLoad` 403 cross-company — **VERIFIED**.
- **Attachment security:** disposition `attachment` per non-immagini + `nosniff`/`X-Frame-Options`, delete
  lifecycle (MOD-004B/C). VF-01 (Low, orfani su errore storage reale — accettato), VF-02/O-01 (Info).
- **Docker/rete:** backend/DB/MinIO non pubblicati; solo nginx espone `3000:80`.
- **HTTPS/certificati/DNS:** meccanismo documentato; provisioning **OPEN**.
- **LDAP creds:** visibili via `docker inspect` (Info, gestione secret a deploy).

---

## 16. Completed Work

- **Licensing-unlock (obiettivo core):** MOD-001/002 — **COMPLETO + VERIFICATO live**.
- **LDAP/AD:** audit + hardening + 9 test (MOD-003/003A) — completo; live LDAP **non** esercitato (nessun
  server di test).
- **Allegati/storage:** audit + security (stored-XSS) + lifecycle + runtime (MOD-004…004C) — completo.
- **Runtime integration + deployment alignment:** MOD-005/006 — completo.
- **Frontend web audit:** MOD-008 — CLEAN.
- **Mobile compatibility + runtime acceptance:** MOD-009/012 — contratto verificato.
- **F-01 fix:** MOD-011 — RESOLVED (1446/1446).
- **Go-live readiness:** MOD-013 — READY WITH CONDITIONS.
- **Mobile agent test env + build + bug fix:** MOD-014/014A/014B/015 — AVAILABLE, M-BUG-1 RESOLVED.
- **i18n polacco:** MOD-016/017 — mobile ripulito e verificato a runtime.

---

## 17. Open Issues

| ID | Area | Problema | Severity | Stato | Evidenza | Prossima azione |
|---|---|---|---|---|---|---|
| GL-1 | Infra/Security | Secret produzione ancora d'esempio (`POSTGRES_PWD`/`MINIO_PASSWORD`/`JWT_SECRET_KEY`) | Condizione | OPEN | doc 31 §12 | Ruotare al deploy |
| GL-2 | Git/Provenance | Modifiche MOD non committate (HEAD a MOD-013) | Condizione | OPEN | §13; doc 31 §2 | Commit+push branch o trasferire sorgenti |
| GL-3 | Infra/Rollback | Immagine tag fisso `:local` sovrascritto | Condizione | OPEN | doc 31 §7 | Taggare versione prima del deploy |
| DOM | Infra | Dominio/DNS/TLS/Caddy non definiti nel repo | Open decision | OPEN | doc 31 §9/§18 | Scelta owner + provisioning server |
| F-04 | Backend | Partial PATCH che omette `@NotNull` (es. `name`) → 500 (MapStruct SET_NULL) | P3 | OPEN (pre-esistente, out-of-scope) | doc 29 §11 / 30 §16 | Decisione separata; mobile impact NONE |
| MOB-BUGS | Mobile | "Alcuni bug" segnalati dal responsabile oltre M-BUG-1 | UNKNOWN | OPEN | doc 31 §4 | Audit/fix mobile (ambiente ora AVAILABLE) |
| iOS | Mobile | GUI/runtime iOS non testabili dall'agent (host Windows) | — | OPEN | doc 30/31 | EAS/Mac/device farm o test manuale owner |
| LDAP-LIVE | Backend | Flusso LDAP mai esercitato live (nessun server di test) | — | OPEN | doc 22 §10 | Test con LDAP/AD reale se richiesto |
| LDAP-SYNC | Infra/config | Default sync divergenti `application.yml`(true) vs compose(false) | Low | OPEN | doc 22 §5 | Decisione operatore (compose prevale) |
| VF-01 | Reliability | Binario MinIO orfano su errore storage reale (best-effort) | Low | ACCEPTED | doc 21 | Opzionale metrica/job |
| I18N-OTHER | i18n | Altre 14 lingue / backend email non-PL non auditate | Low | OPEN | doc 36/37 | Solo su richiesta |
| STORAGE-FS | Backend | Storage filesystem non implementato (solo MinIO/GCP) | Medium | OPEN | doc 22 §11 | Solo se richiesto (MinIO sufficiente) |
| SPRING-PROF | Infra | `SPRING_PROFILES_ACTIVE` production da confermare | Info | OPEN | doc 31 §11 | Conferma owner |
| SYNC-DEP | Mobile | Offline write-sync / push FCM non verificati | — | DA VERIFICARE | doc 27/30 | Device reale + config backend |

Non inclusi come aperti: F-01 (RESOLVED), M-BUG-1 (RESOLVED), stored-XSS (RESOLVED), literal key PL
(RESOLVED).

---

## 18. Approved Decisions

Da `CLAUDE.md` (24 decisioni approvate). Sintesi delle principali (non riproporre come alternative senza
nuova motivazione):

1. **Self-hosted licensing centralizzato** (`self-hosted-mode=true`) — no Keygen, no bypass sparsi.
2. **Deployment builda il backend dai sorgenti** (`build: ./api`, `atlas-cmms-backend:local`) — mai
   l'immagine upstream.
3. **Tenant isolation `@PostLoad`** — non sostituire/bypassare.
4. **Attachment security MOD-004B/C** — non indebolire.
5. **`MAIL_RECIPIENTS` default vuoto** — l'app boota senza.
6. **Frontend web: nessuna modifica** (gate backend-driven).
7. **Mobile: nessuna modifica per il solo self-hosted** (custom server URL) — le modifiche mobile
   successive sono **bug-fix** (MOD-015) e **i18n** (MOD-017), non feature.
8. **LDAP**: SSO come gate; no memberOf/StartTLS/multi-company/redesign; JIT provisioning; password mai
   persistite.
9. **i18n (MOD-016/017):** usare chiavi (non literal English), `en` come riferimento tecnico, integrità
   en↔pl, non toccare altre lingue, correggere solo impatto PL effettivo.

---

## 19. Temporary Decisions

- LDAP sync disabilitato di default salvo config esplicita; LDAP non abilitato automaticamente.
- Credenziali AD reali mai committate.
- Commit/push **non** eseguiti nel workflow di audit/verifica salvo richiesta esplicita.
- Volumi throwaway locali (`atlas-cmms_postgres_data`/`minio_data`) di test rimasti dopo MOD-010 (rimuovere
  prima di un `up` reale — richiede approvazione `docker volume rm`).

---

## 20. Open Decisions

Richiedono decisione del responsabile (non risolvibili dal repo):

1. **Dominio Atlas production + reverse proxy + DNS + certificato TLS** (Caddy consigliato). Chiarire
   separazione da `wiki.firmabratex.pl` (config `websrv01` esterna).
2. **Percorsi storage host:** volumi Docker con nome (default) vs bind `/srv/data/...`.
3. **`SPRING_PROFILES_ACTIVE`** production.
4. **Versioning/tag immagini** per rollback (GL-3).
5. **Mobile:** quando/se affrontare i bug segnalati dal responsabile (ora ambiente AVAILABLE).
6. **F-04:** se/quando correggere la semantica partial-patch.
7. **Frontend build-from-source** (opzionale; oggi upstream).
8. **Servizi esterni self-hosted** (SMTP/OAuth2 in-scope o rinviati?) — `DA VERIFICARE`.
9. **Storage filesystem** (classe D) — solo se richiesto.
10. **LDAP prodotto:** memberOf→role, custom roles, StartTLS, truststore, multi-company.

---

## 21. Contradictions Found

| # | Old | New | Autoritativo | Azione |
|---|---|---|---|---|
| 1 | doc 04 feature-matrix: File Attachments storage = "MinIO/GCP/**FS**" | Audit codice (doc 18): `StorageType={GCP,MINIO}`, **no filesystem** | **doc 18** (più recente + codice) | Filesystem = classe D (non implementato); doc 04 superato |
| 2 | Baseline test **1445** (doc 20/22, era MOD-004B) | Baseline **1446** (MOD-011 +1 test) | **MOD-011/doc 29** | Usare 1446 |
| 3 | `22-audit-consolidation-gap-analysis.md` numerato come report `22` | È in realtà il **PROMPT** della consolidation (istruzioni, non risultati) | Il report reale è `22-audit-consolidation.md` | Naming ambiguo (non un conflitto di stato) — annotato |
| 4 | Doppi prefissi `19-…-security-lifecycle.md` / `19-…-implementation.md`, `21-…-decision` / `21-mod004c-e2e` | Sono documenti distinti (brief vs implementazione; decisione vs verifica) | Entrambi validi | Nessun conflitto; solo numerazione ripetuta |

Nessun conflitto tra **codice** e **ultima verifica**. Contraddizione di stato: `NONE`.

---

## 22. Goal vs Current State

```text
GOAL: build self-hosted pulita che sblocca le feature premium (no bypass), sicura, testabile,
      deployabile in produzione su infra aziendale (Postgres+MinIO+Docker+reverse proxy), con mobile utilizzabile.

CURRENT STATE:
  ✔ Licensing-unlock: COMPLETO + VERIFICATO live (MOD-001/002/005)
  ✔ Sicurezza/authz/multi-tenancy: preservate e verificate
  ✔ Allegati/storage: sicuri + runtime verificato
  ✔ Backend+web: READY WITH CONDITIONS (MOD-013), suite 1446/1446, frontend CLEAN
  ✔ Deployment aligned (source-built), backup/rollback supportati
  ✔ Mobile Android: build/install/smoke/regression/i18n PASS su emulatore
  ◑ Mobile: iOS agent non testabile; bug owner non tutti trattati
  ✗ Deployment production ESEGUITO: NO (dominio/DNS/TLS/secret/commit mancanti)
  ◑ i18n: solo polacco (mobile) completato

GAP: (1) esecuzione go-live operativa; (2) maturità mobile (bug + iOS); (3) i18n altre lingue (opz.)
```

---

## 23. Remaining Gaps

| Gap | Classe | Azione richiesta |
|---|---|---|
| Provisioning dominio/DNS/TLS/Caddy + rotazione secret (GL-1) + commit sorgenti (GL-2) + image tag (GL-3) | **MUST HAVE** | Decisioni owner + accesso server; nessun codice |
| Audit/fix bug mobile segnalati dal responsabile | **SHOULD HAVE** | Ambiente agent ora AVAILABLE → riproduzione+fix |
| Verifica iOS (oltre connettività) | SHOULD HAVE | EAS/Mac/device farm o test manuale owner |
| Test integrazione allegati app-level (Testcontainers+MinIO) | SHOULD HAVE | Suite ripetibile (gap P2 doc 22) |
| LDAP live + decisioni prodotto (memberOf/StartTLS/multi-company) | SHOULD HAVE | Server LDAP/AD di test; decisioni owner |
| Storage filesystem | NICE TO HAVE | Solo se richiesto (MinIO sufficiente) |
| i18n altre 14 lingue / backend email non-PL | NICE TO HAVE | Su richiesta |
| Frontend build-from-source | NICE TO HAVE | Opzionale |
| Offline write-sync / push FCM | UNKNOWN | Device reale + config backend |

---

## 24. Recommended Roadmap

Raccomandazione (non decisione). Candidati per MOD-019, in ordine di valore verso l'obiettivo:

**MOD-019 (opzione A — consigliata se l'obiettivo è il go-live): Repository & Deployment Readiness.**
- *Purpose:* risolvere GL-1/GL-2/GL-3 lato repo — organizzare i sorgenti MOD in commit separati e
  documentati (GL-2, coerente con la nota manutenzione del prompt §21), definire lo schema di tag immagine
  (GL-3), preparare un `.env` di produzione template (senza secret) e la checklist di rotazione (GL-1).
- *Why now:* è l'unico gap **MUST HAVE** e non richiede accesso al server; sblocca il deployment.
- *Dependencies:* decisione owner su dominio/reverse proxy (può restare parallela). *Risk:* Basso (Git +
  doc; nessun codice applicativo). *Outcome:* progetto "commit-ready" e "deploy-ready".

**MOD-019 (opzione B): Mobile Bug Audit & Fix.**
- *Purpose:* riprodurre e catalogare i "bug" segnalati dal responsabile (oltre M-BUG-1) sull'ambiente agent
  ora AVAILABLE; fix minimali + regression.
- *Why now:* l'ambiente è pronto; migliora la maturità mobile. *Dependencies:* elenco bug dal responsabile
  (attualmente NOT DOCUMENTED). *Risk:* Medio (codice mobile). *Outcome:* mobile più vicino a
  production-ready.

**MOD-019 (opzione C): LDAP Live Integration Test.**
- *Purpose:* esercitare il flusso LDAP/AD reale (auth/JIT/sync/OU-mapping) contro un server di test.
- *Why now:* chiude l'ultimo grande gap di verifica non-mobile. *Dependencies:* server LDAP/AD di test.
  *Risk:* Basso-Medio. *Outcome:* LDAP verificato live.

Alternative minori: test integrazione allegati (Testcontainers+MinIO); mini-decisione default LDAP sync;
audit i18n altra lingua prioritaria.

**Nessun MOD successivo viene avviato in questo report.**

---

## 25. Documentation Map

```text
Goal / Mandato
→ atlas-self-hosted-audit-prompt.md ; 00-executive-summary.md

Audit fase-1 (feature/licensing)
→ 01…12 (license-entitlements, backend/frontend gates, feature-matrix, ldap-ad, storage, pm, wo, assets, security, modification-plan, test-plan)

Licensing self-hosted
→ 13/14 (MOD-001) ; 15 (MOD-002) ; verificato live 23 (MOD-005)

LDAP / AD
→ 16 (MOD-003) ; 17 (MOD-003A)  [live NOT tested]

Allegati / Storage / MinIO
→ 18 (MOD-004) ; 19/20 (MOD-004B) ; 21c (MOD-004C) ; runtime 23 (MOD-005)

Deployment / Runtime
→ 23 (MOD-005) ; 24 (MOD-006) ; 28 (MOD-010) ; 31 (MOD-013 go-live)

Consolidamento
→ 22-audit-consolidation.md   (22-…-gap-analysis.md = PROMPT, non report)

Frontend Web
→ 26 (MOD-008)

Mobile
→ 27 (MOD-009) ; 30 (MOD-012) ; 32 (MOD-014) ; 33 (MOD-014A) ; 34 (MOD-014B) ; 35 (MOD-015)

Polish i18n
→ 36 (MOD-016) ; 37 (MOD-017)

Fix backend
→ 29 (MOD-011, F-01)

Checkpoint di stato (sempre aggiornato)
→ CLAUDE.md ; questo report = 38 (MOD-018)
```

---

## 26. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-018 (Project State Recap) come Current focus + riga Current Project State +
Documentation Workflow/Map (doc 38); aggiunta sezione "Project Checkpoint (MOD-018)" con lo stato
sintetico per area e il prossimo passo raccomandato (USER DECISION: deployment / mobile bug / repo
readiness). Nessuna modifica di codice. Il dettaglio resta in questo report (38).
```

---

## 27. Final Verdict

```text
CLAUDE.md updated: YES

Documentation files analyzed: 66 (docs/) — foundational letti integralmente: audit-prompt, 00, 22, 31; sintesi CLAUDE.md; report 34–37; prompt MOD-016/017/018
MODs identified: 17 (001–017) + sotto-varianti 003A/004B/004C/014A/014B
MODs completed: 17 (tutti PASS o PASS WITH FINDINGS)
MODs open: 0 (nessuno IN PROGRESS)
MODs blocked: 0
MODs superseded: 0 (documenti superati: doc 04 su storage FS)

Backend:        PASS (verificato live; 1446/1446)
Frontend:       PASS (audit CLEAN, nessuna modifica)
Android:        PARTIAL (emulatore PASS; device fisico/bug owner non completati)
iOS:            NOT VERIFIED (agent); connettività confermata dall'owner su device reale
Polish i18n:    PASS (mobile; altre lingue fuori scope)
Licensing:      PASS (verificato live; nessun gate residuo)
Infrastructure: PARTIAL (locale/LAN PASS; production dominio/DNS/TLS OPEN)
Testing:        PARTIAL (unit/e2e/runtime PASS; integrazione allegati app-level e CI/CD assenti)
Security:       PASS (rotazione secret richiesta al go-live)

Open issues: 14 (di cui 3 condizioni go-live GL-1/2/3; 1 P3 F-04; resto Low/Info/DA VERIFICARE)
Critical gaps: 1 MUST HAVE (esecuzione go-live: infra + secret + commit sorgenti)
Contradictions: 0 di stato (4 note documentali: FS storage, baseline 1445→1446, naming 22, prefissi 19/21)
Approved decisions: 24 (CLAUDE.md)
Open decisions: 10 (dominio/TLS, storage paths, SPRING_PROFILES_ACTIVE, image tag, mobile, F-04, frontend build, servizi esterni, storage FS, LDAP prodotto)

Recommended next MOD: MOD-019 — USER DECISION tra (A) Repository & Deployment Readiness [consigliata per go-live], (B) Mobile Bug Audit & Fix, (C) LDAP Live Integration Test

Final project status: licensing-unlock COMPLETO e verificato; backend+web READY WITH CONDITIONS; mobile PARTIAL; deployment production non ancora eseguito
Final verdict: PASS WITH FINDINGS
```

**Checkpoint.** Il progetto ha **raggiunto il suo obiettivo tecnico centrale** (sblocco pulito e
verificato delle funzionalità in self-hosted, con sicurezza e multi-tenancy intatte) ed è **tecnicamente
pronto** per il go-live di backend + web, a meno di **attività operative note** (dominio/DNS/TLS,
rotazione secret, commit dei sorgenti). Il fronte **mobile** è funzionante e testabile dall'agent su
Android (emulatore), con un bug corretto e il polacco ripulito, ma non è ancora "production-ready" (bug
owner + iOS). Le altre lingue e alcune decisioni di prodotto LDAP restano opzionali/aperte.

⏹️ **STOP** — MOD-018 è un checkpoint di sola documentazione. Non implemento nulla, non eseguo
deployment, non committo, non avvio MOD-019. La scelta del prossimo passo spetta al responsabile.
