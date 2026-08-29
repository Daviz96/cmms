# MOD-006 — Deployment Alignment

Allineamento del deployment self-hosted ufficiale al codice sorgente modificato, più
valutazione/correzione dei finding di deployment `CFG-01` e `CFG-02` emersi in MOD-005.
Scope limitato a **packaging/deployment**: nessuna modifica a licensing, security degli
allegati, tenant isolation, API o architettura applicativa. Secret mascherati (`********`).

Ambiente: Windows 11, Docker 29.4.3, JDK 17 Temurin 17.0.20.1 (portable), Maven 3.8.6.
Fonti: [CLAUDE.md](../CLAUDE.md), [22-audit-consolidation.md](22-audit-consolidation.md),
[23-mod005-runtime-integration-verification.md](23-mod005-runtime-integration-verification.md),
`docker-compose.yml`, `api/Dockerfile`, `nginx.conf`, `application.yml`.

---

## 1. Objective

Rendere il deployment self-hosted riproducibile e coerente con i sorgenti:

```text
git checkout → docker compose build → immagine backend dai sorgenti → docker compose up → Atlas self-hosted
```

senza dipendere accidentalmente dall'immagine backend upstream (`intelloop/atlas-cmms-backend`),
che **non** contiene le modifiche dei MOD. Inoltre: valutare/correggere `CFG-01`
(`MAIL_RECIPIENTS` senza default) e `CFG-02` (dipendenza di startup frontend/nginx).

---

## 2. Initial State

- `docker-compose.yml` → servizio `api`: `image: intelloop/atlas-cmms-backend` (upstream
  prebuilt). Il `api/Dockerfile` (multi-stage Maven → `eclipse-temurin:17-jre`) esiste ed è
  sufficiente per una build ufficiale (dimostrato in MOD-005), ma **non** era usato dal compose.
- `application.yml`: `mail.recipients: ${MAIL_RECIPIENTS}` — **senza default**.
- `nginx.conf`: `upstream atlas_frontend { server frontend:3000; }` (upstream statico).

Conseguenza (MOD-005): un `docker compose up` sul repo avrebbe eseguito l'immagine
upstream priva di MOD-001/004B; inoltre l'API non parte se `MAIL_RECIPIENTS` è assente.

---

## 3. Root Cause

- **Disallineamento immagine**: il compose puntava all'immagine pubblicata upstream invece
  di costruire dai sorgenti locali → il codice modificato non veniva eseguito.
- **CFG-01**: `EmailService2` e `SendgridService` iniettano `@Value("${mail.recipients}")`
  **senza default**; con la property definita come `${MAIL_RECIPIENTS}` (senza default),
  se l'env è del tutto assente il placeholder non risolve → `emailService2` fallisce la
  creazione del bean → bootstrap fallito. (`UserService`/`WebhookController` usano invece
  `${mail.recipients:#{null}}`, già sicuri.)
- **CFG-02**: `nginx` risolve gli `upstream` statici all'avvio; se il container `frontend`
  è down, `frontend:3000` non risolve e nginx **non parte affatto** (bloccando anche
  `/api` e `/storage`). In MOD-005 il frontend era down perché il compose isolato **non**
  gli forniva le env richieste (`HOME_URL`, …).

---

## 4. Decision

- **Backend da sorgenti** con **Opzione A** (`build: context: ./api`) + tag locale esplicito
  `atlas-cmms-backend:local`, rimuovendo il riferimento upstream. Nessun registry esterno.
  È la soluzione più semplice e riproducibile, coerente col Dockerfile esistente.
- **CFG-01**: correzione minima e fondamentale nella sorgente della property —
  `mail.recipients: ${MAIL_RECIPIENTS:}` (default vuoto). Non tocca il codice Java né il
  comportamento quando le email sono configurate.
- **CFG-02**: **valutato, non "corretto" con modifiche nginx** (il brief vieta l'auto-
  implementazione di soluzioni `resolver` dinamiche). Vedi §9.

---

## 5. Changes Implemented

Due soli file modificati (diff verificato):

```diff
# docker-compose.yml (servizio api)
-    image: intelloop/atlas-cmms-backend
+    build:
+      context: ./api
+    image: atlas-cmms-backend:local
     container_name: atlas-cmms-backend

# api/src/main/resources/application.yml
-  recipients: ${MAIL_RECIPIENTS}
+  recipients: ${MAIL_RECIPIENTS:}
```

(Le altre righe presenti nel `git diff` di questi file — `licensing.self-hosted-mode`,
`LICENSING_SELF_HOSTED_MODE` — appartengono a **MOD-001** e non sono state toccate da
MOD-006.) Nessuna modifica a `nginx.conf`, `api/Dockerfile`, codice applicativo, test.

---

## 6. Docker Build

TEST: `docker compose config` valida il compose modificato · EXPECTED: exit 0, api con
`build.context ./api`, nessun `intelloop/atlas-cmms-backend` · ACTUAL:

```text
config EXIT=0 ; nessun warning (env file isolato)
api:  build.context = .../api ; dockerfile = Dockerfile ; image = atlas-cmms-backend:local
frontend = intelloop/atlas-cmms-frontend (invariato, non-focus)
```
RESULT: **PASS**.

TEST: `docker compose build api` costruisce dai sorgenti · EXPECTED: BUILD, immagine
`atlas-cmms-backend:local` · ACTUAL: `Image atlas-cmms-backend:local Built`, exit 0.
RESULT: **PASS**. EVIDENCE: `compose_build.log`.

---

## 7. Image Provenance

TEST: l'immagine costruita **dal compose** contiene il codice modificato · EXPECTED:
metodi MOD-001/004B + fix CFG-01 · ACTUAL (jar estratto, `javap` + risorsa):

```text
MinioService  : responseHeaderOverrides(com.grash.model.File)   (MOD-004B)  ✓
LicenseService: buildSelfHostedLicensingState()                  (MOD-001)  ✓
application.yml (BOOT-INF/classes): recipients: ${MAIL_RECIPIENTS:}  (CFG-01) ✓
source commit: e1d24406
```
RESULT: **PASS** — l'immagine eseguibile prodotta da `docker compose build` è quella dei
sorgenti (non upstream) e include il fix CFG-01.

---

## 8. CFG-01 Evaluation

Uso della variabile: `mail.recipients` → consumato da `EmailService2`/`SendgridService`
(`@Value("${mail.recipients}")`, `String[]`) e, con default null, da `UserService`/
`WebhookController`. `ENABLE_EMAIL_NOTIFICATIONS=false` non evita l'iniezione del bean
`emailService2` (creato comunque), quindi la property deve essere risolvibile.

Fix: `${MAIL_RECIPIENTS:}` (default vuoto). Una stringa vuota converte in `String[]` di
lunghezza 0 (Spring `commaDelimitedListToStringArray("")` → array vuoto), coerente con i
check `recipients == null || recipients.length == 0` e col percorso notifiche-disabilitate.
Comportamento **invariato** quando `MAIL_RECIPIENTS` è impostata.

TEST: l'API parte **senza** `MAIL_RECIPIENTS` · EXPECTED: startup OK · ACTUAL (stack
isolato, env priva di `MAIL_RECIPIENTS`): `Started ApiApplication in 36.3s`, nessun
`Could not resolve placeholder 'MAIL_RECIPIENTS'`. RESULT: **RESOLVED** · EVIDENCE: log api mod006.

---

## 9. CFG-02 Evaluation

Classificazione del problema: **comportamento normale di nginx** con `upstream` statici
(risoluzione DNS all'avvio), **non** un difetto introdotto dai MOD.

Osservazione chiave: nel **compose ufficiale** il servizio `frontend` riceve le proprie env
con default (`HOME_URL: ${HOME_URL:-https://atlas-cmms.com}`, e le altre `${X:- }`), quindi
il frontend **parte** e nginx risolve l'upstream. Il fallimento visto in MOD-005 derivava
dal compose **isolato** che ometteva quelle env — non dal deployment ufficiale.

TEST: deployment official-style (frontend con env di default) · EXPECTED: frontend UP →
nginx UP → routing OK · ACTUAL (stack isolato mod006, env frontend completo):

```text
frontend UP ; nginx UP ; GET / -> 200 ; GET /api/auth/me -> 401
```
RESULT: **CFG-02 non si manifesta** nel deployment ufficiale.

Decisione: **lasciato motivatamente aperto** come *hardening opzionale*. Una soluzione
`resolver` dinamica (o `set`+variabile negli `upstream`) disaccoppierebbe l'avvio di nginx
da frontend/api, ma è una modifica nginx sostanziale che il brief (§8) vieta di
auto-implementare. Nessuna modifica a `nginx.conf`. Vedi §13/§15.

---

## 10. Isolated Runtime Verification

Stack isolato `atlas-cmms-mod006` (project + rete + volumi + porta 18086 dedicati; immagine
backend = `atlas-cmms-backend:local` dai sorgenti; **`MAIL_RECIPIENTS` omessa**). Nessun
impatto su ambienti esistenti.

| Servizio | Stato |
|---|---|
| postgres / minio / api / frontend / nginx | **UP** (tutti) |

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| startup API | Started | `Started ApiApplication` (36.3s) | PASS |
| self-hosted licensing | log SELF_HOSTED | `Atlas licensing mode: SELF_HOSTED` | PASS |
| `GET /` | 200 | 200 | PASS |
| `GET /api/auth/me` | 401 | 401 | PASS |
| signup → BUSINESS + token | 200 | 200, success:true | PASS |
| upload non-immagine (entitlement+`FILE`+perm) | 200 | 200, url con `response-content-disposition=attachment` | PASS |
| download header (MOD-004B) | attachment+nosniff+XFO | `Content-Disposition: attachment`, `nosniff`, `X-Frame-Options: DENY` | PASS |
| delete lifecycle | metadata+object rimossi | DELETE 200 ; GET meta 404 ; storage 404 | PASS |

EVIDENCE: log api, `s.json`/`up.json`, header curl, sequenza delete.

---

## 11. Regression Tests

TEST: `mvnw test` dopo le modifiche · EXPECTED: 1445/0/0/0 · ACTUAL:
**`Tests run: 1445, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS**. RESULT: **PASS**.
EVIDENCE: `mod006_full.log`. Il default vuoto di `MAIL_RECIPIENTS` non introduce regressioni.

---

## 12. Security Regression

Controlli di sicurezza **invariati** (nessuna modifica al codice/nginx relativa):

| Controllo | Stato runtime |
|---|---|
| self-hosted licensing | attiva (SELF_HOSTED) |
| attachment `Content-Disposition` (OTHER→attachment) | presente |
| `X-Content-Type-Options: nosniff` / `X-Frame-Options: DENY` | presenti |
| MinIO delete lifecycle (object+metadata) | funzionante (404/404) |
| authorization / tenant isolation (`CompanyAudit`) | non modificati (verificati in MOD-005) |

RESULT: **PASS** — nessuna regressione di sicurezza. (MOD-006 non tocca licensing,
authorization, `CompanyAudit`, `FileService`, `MinioService`, storage security.)

---

## 13. Findings

| ID | Classe | Severity | Stato | Descrizione | Azione |
|---|---|---|---|---|---|
| CFG-01 | D (config) | Low | **Resolved** | `MAIL_RECIPIENTS` senza default → boot failure | default vuoto in `application.yml`; validato (boot senza la env) |
| CFG-02 | D (config)/reliability | Low | **Open (accettato)** | nginx `upstream` statico: se frontend down, nginx non parte | non manifesto nel compose ufficiale (frontend env con default); hardening `resolver` **deferito** (non auto-implementato, §8) |

Nessun nuovo finding. Nessun finding di classe A/B/C/E/G. Nessun bypass di sicurezza.

---

## 14. Final Verdict

**PASS.**

- Il deployment self-hosted ora **builda esplicitamente il backend dai sorgenti**
  (`build: ./api`, tag `atlas-cmms-backend:local`), senza riferimento all'immagine upstream;
  build riproducibile via `docker compose build`; provenienza verificata (MOD-001/004B +
  CFG-01 nell'immagine).
- **CFG-01 risolto** (boot senza `MAIL_RECIPIENTS`, comportamento invariato quando impostata).
- **CFG-02 valutato**: non si manifesta nel deployment ufficiale; hardening opzionale
  lasciato aperto e documentato (nessuna modifica nginx).
- Stack isolato UP, smoke test allegati PASS, security invariata, **regressione 1445/1445**.
- Nessun ambiente production modificato; solo 2 file toccati (`docker-compose.yml`,
  `application.yml`).

⏹️ **STOP** — non avvio MOD-007, non modifico licensing/feature/architettura, non aggiungo
funzionalità.

---

## 15. Remaining Work

- **CFG-02 hardening (opzionale, da approvare)**: disaccoppiare l'avvio di nginx dal
  frontend tramite `resolver` dinamico + `proxy_pass` con variabile negli `upstream`, così
  che `/api` e `/storage` restino serviti anche se il frontend è down. Modifica nginx
  sostanziale → richiede decisione esplicita (non implementata qui).
- **Frontend build-from-source (opzionale)**: il frontend resta `intelloop/atlas-cmms-frontend`
  (nessun MOD ne ha modificato i sorgenti). Se si desidera piena riproducibilità anche del
  frontend, si potrebbe usare `build: ./frontend` (`frontend/Dockerfile` esiste). Fuori dallo
  scope backend di MOD-006.
- **Documentazione da aggiornare (non modificata, per §20 del brief)**: `CLAUDE.md`
  (aggiungere MOD-006 PASS; nota che il compose ora builda dai sorgenti) e — se desiderato —
  `.env.example`/README deployment per riflettere il flusso `docker compose build`.

`Code changes: docker-compose.yml (api build-from-source), application.yml (MAIL_RECIPIENTS default).`
