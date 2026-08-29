# Roadmap — Chiusura Atlas Self-Hosted → APK Android → Deploy Server

## Stato di partenza

Il MOD-018 ha ricostruito lo stato generale del progetto.

Il progetto ha raggiunto la maggior parte degli obiettivi principali:

- licensing self-hosted sbloccato e verificato;
- backend verificato con 1446/1446 test;
- multi-tenancy e autorizzazioni preservate;
- storage/MinIO e sicurezza allegati verificati;
- frontend web auditato e risultato CLEAN;
- Android buildato, installato e testato sull'AVD;
- bug M-BUG-1 risolto;
- traduzioni polacche corrette e verificate a runtime;
- i18n polacco verificato;
- ambiente Android per il coding agent disponibile;
- iOS connettività verificata manualmente, ma test completo non disponibile all'agent.

Il progetto NON è ancora stato committato: le modifiche dei MOD sono ancora nel working tree.

Fonte: `docs/self-hosted-audit/38-mod018-project-state-recap.md`.

---

# Obiettivo da questo momento

Portare il progetto da:

```text
WORKING TREE MODIFICATO
        ↓
FINAL CODE AUDIT
        ↓
EVENTUALI FIX NECESSARI
        ↓
CODE FREEZE
        ↓
COMMIT
        ↓
PUSH
        ↓
RELEASE APK ANDROID
        ↓
SERVER PREPARATION
        ↓
DEPLOY
        ↓
LIVE VERIFICATION
```

Prima di chiudere questa fase dobbiamo stabilire con evidenza se esistono ancora modifiche al codice indispensabili.

Se non esistono:

```text
CODE FREEZE
```

e si passa alla chiusura/versionamento.

---

# Roadmap

## MOD-019 — Final Code Audit & Release Readiness

Analizzare repository, documentazione e stato dei test per stabilire se il codice attuale è pronto al freeze.

Possibili risultati:

```text
READY FOR CODE FREEZE
```

oppure:

```text
FIXES REQUIRED BEFORE CODE FREEZE
```

Se servono fix, devono essere minimi, motivati e verificati.

---

## MOD-020 — Release Commit, Tag & Push

Da eseguire solo dopo MOD-019.

Obiettivi:

- verificare working tree;
- verificare `.gitignore`;
- assicurarsi che nessun secret venga committato;
- creare commit coerenti con il lavoro effettuato;
- creare un tag/versione release se approvato;
- push verso il fork remoto.

Il diff finale deve rappresentare la baseline riproducibile del software che verrà deployato.

---

## MOD-021 — Android Release APK

Da eseguire dopo il code freeze.

Obiettivi:

- generare APK release dai sorgenti congelati;
- verificare build;
- installare APK;
- eseguire smoke/regression;
- verificare lingua polacca;
- verificare connessione al backend;
- conservare l'APK come artefatto della release.

Percorso documentato:

```text
mobile/android/app/build/outputs/apk/release/app-release.apk
```

L'APK deve essere generata dal codice della release committata/taggata.

---

## MOD-022 — Server Deployment Preparation

Preparare il deployment sul server senza eseguire ancora il go-live definitivo.

Verificare:

- Docker/Compose;
- sorgenti;
- `.env` production;
- secret reali;
- PostgreSQL;
- MinIO;
- volumi persistenti;
- backup;
- reverse proxy;
- Caddy;
- dominio;
- DNS;
- TLS;
- firewall/network exposure;
- image tagging;
- rollback.

Definire chiaramente:

```text
DOMAIN
DNS
TLS
REVERSE PROXY
SERVER PATHS
SECRETS
BACKUP
VERSION
ROLLBACK
```

---

## MOD-023 — Production Deployment

Deploy della versione congelata sul server.

Sequenza:

```text
BACKUP
↓
PREPARE ENV
↓
PULL RELEASE
↓
BUILD/PULL REQUIRED IMAGES
↓
START STACK
↓
CHECK CONTAINERS
↓
CHECK DATABASE
↓
CHECK MINIO
↓
CHECK API
↓
CHECK FRONTEND
↓
CHECK HTTPS
↓
SMOKE TEST
↓
FUNCTIONAL TEST
```

Non considerare il deployment completato solo perché i container risultano `Up`.

---

## MOD-024 — Production Acceptance

Verifica finale sul server reale.

Web:

- login/logout;
- dashboard;
- assets;
- work orders;
- parts;
- meters;
- PM;
- attachments;
- settings;
- ruoli/permessi;
- tenant isolation.

Licensing:

- verificare che le funzionalità precedentemente bloccate continuino a essere disponibili.

Storage:

- upload;
- download;
- delete;
- persistenza.

Security:

- HTTPS;
- autenticazione;
- autorizzazione;
- isolamento tenant;
- secret non esposti.

Android:

- configurazione verso il server reale;
- connessione;
- login;
- funzioni principali;
- polacco.

---

# Mobile iOS

L'iOS resta un'attività separata.

Stato:

```text
iOS connectivity: VERIFIED MANUALLY
iOS full runtime audit: NOT VERIFIED
iOS agent testing: NOT AVAILABLE ON CURRENT WINDOWS HOST
```

Dovrà essere gestito quando sarà disponibile un ambiente macOS/Xcode o altra modalità affidabile di test.

Non inventare un metodo di test iOS.

Il test iOS non deve bloccare automaticamente il deployment del core backend/web/Android, salvo che emerga una dipendenza funzionale reale.

---

# Principio di chiusura

Da questo momento non modificare il codice semplicemente perché esiste una possibile miglioria.

Una modifica è ammessa prima del freeze solo se:

1. risolve un bug reale;
2. corregge una regressione;
3. è necessaria per il deployment;
4. è necessaria per la sicurezza;
5. è necessaria per la build/release;
6. è necessaria per mantenere una decisione architetturale già approvata.

Migliorie estetiche, refactoring e feature nuove devono essere rimandate.

---

# MOD-019 — Final Code Audit & Release Readiness

## 1. Obiettivo

Stabilire se il repository attuale è pronto per essere congelato.

Il MOD deve analizzare:

```text
DOCUMENTAZIONE
+
CODICE MODIFICATO
+
TEST
+
BUILD
+
GIT STATUS
+
CONFIGURAZIONE
```

e produrre:

```text
READY FOR CODE FREEZE
```

oppure:

```text
FIXES REQUIRED BEFORE CODE FREEZE
```

---

## 2. Fonti

Prima leggere:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/38-mod018-project-state-recap.md`;
3. documenti implementation/verification direttamente collegati ai file modificati;
4. documentazione MOD-001→017 solo quando necessaria.

NON rileggere indiscriminatamente tutti i documenti se MOD-018 contiene già l'informazione necessaria.

---

## 3. Git baseline

Eseguire:

```powershell
git status
git diff --stat
git diff --check
git diff
```

Analizzare:

- file modificati;
- file untracked;
- test nuovi;
- documentazione;
- configurazione;
- mobile;
- home;
- backend.

Determinare quali file appartengono effettivamente al progetto e quali sono artefatti locali.

NON cancellare nulla automaticamente.

---

## 4. Secret Audit

Verificare che nel working tree non siano presenti secret reali destinati al commit.

Controllare in particolare:

```text
.env
google-services.json
JWT secrets
password
API keys
Firebase credentials
tokens
private keys
production credentials
```

Verificare `.gitignore`.

Non stampare valori segreti nel report.

Se viene trovato un secret potenzialmente committabile:

```text
STOP
SECURITY FINDING
```

---

## 5. Modifiche applicative

Per ogni gruppo di modifiche determinare:

```text
Why changed?
Related MOD?
Verified?
Still required?
Risk if reverted?
```

Gruppi attesi secondo MOD-018:

```text
Backend licensing
Backend security/storage
Backend F-01
LDAP hardening
Mobile config
Mobile bug fix
Polish i18n
Home PL
Tests
Configuration
Documentation
```

Non assumere che tutti siano presenti: verificare il diff reale.

---

## 6. Licensing Regression Check

Verificare che le modifiche di licensing siano ancora coerenti con:

```text
self-hosted mode centralizzato
no Keygen per self-hosted
no bypass sparsi
authorization invariata
tenant isolation invariata
cloud licensing non alterato
```

Non modificare il licensing durante questo MOD salvo bug reale dimostrato.

---

## 7. Backend Regression

Eseguire:

```text
mvnw test
```

Target documentato:

```text
1446/1446
```

Se il numero cambia perché sono stati aggiunti test durante questo MOD, documentare il nuovo totale.

Se un test fallisce:

```text
FIX REQUIRED
```

Non ignorare failure.

---

## 8. Frontend Regression

Non effettuare un nuovo audit licensing.

MOD-008 è già:

```text
PASS — CLEAN
```

Verificare soltanto che il working tree non contenga modifiche frontend inattese.

Se vengono trovate modifiche frontend:

```text
identificare origine
→ verificare documentazione
→ determinare se necessarie
```

Non introdurre nuove modifiche frontend senza necessità.

---

## 9. Mobile Regression

Verificare che le modifiche mobile documentate siano presenti e coerenti.

Eseguire almeno:

```text
build
install
launch
login
dashboard
work orders
assets
settings
logout
```

con l'ambiente Android già disponibile.

Verificare anche il polacco.

Non trasformare MOD-019 in un nuovo bug discovery completo.

I bug generici segnalati dal responsabile ma non documentati restano una futura attività, salvo che vengano scoperti durante questa verifica e siano chiaramente bloccanti.

---

## 10. iOS

NON tentare procedure non documentate per simulare macOS/Xcode su Windows.

Verificare solo lo stato documentale:

```text
manual connectivity: verified
full iOS testing: not verified
agent iOS testing: unavailable
```

Non modificare codice iOS solo per compensare l'assenza dell'ambiente di test.

---

## 11. Build Release

Verificare che sia possibile generare:

```text
Android release APK
```

Il percorso atteso:

```text
mobile/android/app/build/outputs/apk/release/app-release.apk
```

Se la build è riproducibile:

```text
RELEASE BUILD READY
```

Se fallisce:

```text
FIX REQUIRED
```

---

## 12. Docker/Deployment Readiness

Verificare il repository rispetto al deployment documentato.

Controllare:

```text
docker-compose.yml
.env.example
Dockerfiles
nginx.conf
backend build
frontend image
volumes
health/startup assumptions
```

Non modificare ancora DNS/Caddy/server.

Determinare se esiste qualche modifica al codice/config necessaria prima del deployment.

---

## 13. Database

Non introdurre migration.

Verificare che le modifiche MOD non richiedano schema changes.

Se viene rilevata una necessità di migration:

```text
STOP
REPORT REQUIRED
```

---

## 14. Documentation Consistency

Confrontare il codice attuale con:

```text
CLAUDE.md
MOD-018
MOD-001…017 relevant verification docs
```

Cercare:

```text
implemented but undocumented
documented but reverted
documented as verified but no longer true
obsolete configuration
obsolete paths
wrong test counts
wrong build information
```

Se esistono discrepanze, correggere la documentazione solo se necessario e documentare la differenza.

---

## 15. Decisione sui fix

Per ogni problema:

| Finding | Severity | Required before freeze? | Action |
|---|---|---|---|

Usare:

```text
BLOCKER
REQUIRED
OPTIONAL
DEFERRED
```

Solo `BLOCKER` e `REQUIRED` devono essere risolti prima del freeze.

`OPTIONAL` e `DEFERRED` non devono bloccare la chiusura.

---

## 16. Cosa NON fare

NON fare:

- refactoring;
- cleanup cosmetico;
- upgrade dipendenze;
- nuove feature;
- nuove traduzioni;
- nuovo licensing audit;
- nuovo frontend audit;
- nuova architettura;
- modifiche iOS speculative.

Questo MOD serve a decidere se fermare lo sviluppo.

---

## 17. Se non servono modifiche

Se:

```text
READY FOR CODE FREEZE
```

NON modificare il codice.

Produrre:

```text
CODE FREEZE APPROVED
```

e indicare che il prossimo step è il versionamento/commit del repository.

---

## 18. Se servono modifiche

Se:

```text
FIXES REQUIRED BEFORE CODE FREEZE
```

effettuare solo i fix classificati `REQUIRED`.

Dopo i fix:

```text
tests
→ build
→ regression
→ git diff
→ documentation
```

Poi rivalutare.

Non iniziare il deployment.

---

## 19. Documentation

Produrre:

```text
docs/self-hosted-audit/39-mod019-final-code-audit.md
```

Struttura:

```text
# MOD-019 — Final Code Audit & Release Readiness

## 1. Objective
## 2. Repository State
## 3. Secret Audit
## 4. Application Changes Review
## 5. Licensing Regression
## 6. Backend Verification
## 7. Frontend Verification
## 8. Mobile Verification
## 9. iOS Status
## 10. Android Release Build
## 11. Docker/Deployment Readiness
## 12. Database Review
## 13. Documentation Consistency
## 14. Findings
## 15. Required Fixes
## 16. Code Freeze Decision
## 17. CLAUDE.md Update
## 18. Final Verdict
```

---

## 20. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

```text
MOD-019 status
Current Project State
Current Focus
Known Issues
Next Step
```

Se il code freeze è approvato:

```text
CODE FREEZE READY
```

Non inserire tutto il diff nel CLAUDE.md.

---

## 21. Anti-Hallucination

NON dichiarare `READY` solo perché i test passano.

Considerare anche:

```text
working tree
secret safety
documentation consistency
release build
deployment readiness
```

NON dichiarare un test `VERIFIED` se non è stato realmente eseguito o documentato.

NON considerare iOS verificato oltre ciò che è documentato.

NON considerare i bug mobile generici risolti se non sono stati riprodotti e verificati.

---

## 22. STOP Conditions

Fermarsi se:

- viene trovato un secret reale nel materiale destinato al commit;
- è necessaria una migration;
- è necessaria una modifica architetturale;
- licensing e authorization risultano confusi;
- una modifica precedente sembra essere stata persa;
- la build release non è riproducibile;
- il comportamento corretto non è determinabile.

Documentare e STOP.

---

## 23. Definition of Done

MOD-019 è completo quando:

- working tree analizzato;
- modifiche MOD classificate;
- secret audit completato;
- backend testato;
- frontend verificato senza ripetere MOD-008;
- Android regression eseguita;
- release build verificata;
- iOS status documentato;
- deployment readiness analizzata;
- documentazione confrontata;
- eventuali fix REQUIRED applicati e verificati;
- `CLAUDE.md` aggiornato;
- decisione finale `READY FOR CODE FREEZE` oppure `FIXES REQUIRED`.

---

## 24. Final Output

```text
CLAUDE.md updated: YES/NO

Working tree:
X tracked modified
X untracked

Secret audit:
PASS / FAIL

Backend:
PASS / FAIL

Frontend:
PASS / FAIL / NO CHANGES

Android:
PASS / FAIL / PARTIAL

Polish:
PASS / FAIL

iOS:
NOT VERIFIED / PARTIAL

Release APK build:
PASS / FAIL

Docker/deployment readiness:
PASS / FINDINGS

Database/migrations:
NONE / REQUIRED

Required fixes before freeze:
NONE / LIST

Optional/deferred findings:
NONE / LIST

Code freeze:
APPROVED / NOT APPROVED

Recommended next step:
MOD-020 COMMIT & PUSH / FIX REQUIRED / USER DECISION

Final verdict:
READY FOR CODE FREEZE / FIXES REQUIRED BEFORE CODE FREEZE / BLOCKED
```

---

# Regole per il passaggio successivo

Se MOD-019 conclude:

```text
READY FOR CODE FREEZE
```

il prossimo lavoro sarà esclusivamente:

```text
COMMIT
→ TAG
→ PUSH
→ RELEASE APK
→ SERVER DEPLOY
```

Non riaprire audit già conclusi senza una nuova evidenza.

Se MOD-019 trova un problema reale, correggere solo quello e ripetere le verifiche necessarie.

---

# Stato mobile bug discovery

I bug generici segnalati dal responsabile restano:

```text
OPEN / NOT DOCUMENTED
```

Non devono essere inventati o considerati risolti.

MOD-019 può rilevare eventuali problemi durante la regression, ma non deve trasformarsi automaticamente in un nuovo progetto di sviluppo mobile.

Un eventuale:

```text
MOD-025 — Mobile Bug Discovery & Fix
```

potrà essere creato in futuro se il responsabile decide di completare la maturità dell'app.

---

# Principio finale

Il progetto è arrivato alla fase di **stabilizzazione e chiusura della release**, non alla fase di sviluppo indefinito.

La domanda da risolvere ora è:

> **Possiamo congelare il codice che abbiamo oggi?**

Se sì:

```text
FREEZE
↓
COMMIT
↓
PUSH
↓
APK
↓
DEPLOY
```

Se no:

```text
FIX MINIMO
↓
VERIFY
↓
FREEZE
```

Al termine di MOD-019: **STOP**.
