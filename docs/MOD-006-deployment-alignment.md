# MOD-006 — Allineamento del deployment self-hosted al codice modificato

## Decisione tecnica

MOD-005 è stato verificato come **PASS WITH FINDINGS**.

La verifica ha dimostrato che il backend costruito dai sorgenti contiene e applica correttamente le modifiche dei MOD precedenti. Tuttavia ha evidenziato un problema operativo importante:

> il `compose` ufficiale del repository continua a utilizzare l'immagine upstream `intelloop/atlas-cmms-backend`, mentre le modifiche dei MOD sono presenti nel codice sorgente e nella build locale `atlas-mod005-backend:local`.

Di conseguenza il deployment self-hosted ufficiale non è ancora automaticamente allineato al codice modificato.

Sono inoltre presenti:
- `CFG-01`: `MAIL_RECIPIENTS` senza default;
- `CFG-02`: frontend/configurazione nginx con dipendenza dal container frontend.

Questa attività deve risolvere **solo i problemi di packaging/deployment identificati da MOD-005**, senza riaprire licensing, security degli allegati o architettura applicativa.

---

# 1. Obiettivo

Rendere il deployment self-hosted del repository riproducibile e coerente con il codice sorgente modificato.

Al termine deve essere possibile:

```text
git checkout del repository
        ↓
docker compose build
        ↓
immagini locali coerenti con i sorgenti
        ↓
docker compose up
        ↓
Atlas self-hosted
```

senza dipendere accidentalmente dall'immagine backend upstream contenente una versione diversa del codice.

Inoltre valutare e, se appropriato, correggere i due finding:

```text
CFG-01 — MAIL_RECIPIENTS
CFG-02 — frontend/nginx startup dependency
```

---

# 2. Fonti obbligatorie

Prima di modificare qualsiasi cosa leggere:

1. `CLAUDE.md`
2. `docs/self-hosted-audit/22-audit-consolidation.md`
3. `docs/self-hosted-audit/23-mod005-runtime-integration-verification.md`
4. documentazione MOD-001
5. documentazione MOD-004B/004C
6. `docker-compose.yml`
7. `api/Dockerfile`
8. `nginx.conf`
9. `application.yml`
10. eventuali README/deployment docs.

Non rileggere inutilmente la documentazione dei MOD se non serve.

---

# 3. Scope

## IN SCOPE

- backend Docker image;
- Docker Compose;
- Dockerfile backend;
- configurazione necessaria per build locali;
- configurazione env relativa ai finding CFG-01/CFG-02;
- documentazione deployment self-hosted;
- verifica build;
- verifica runtime.

## OUT OF SCOPE

NON modificare:

- licensing logic;
- PlanFeatures;
- LDAP;
- CompanyAudit;
- authorization;
- FileService;
- MinioService;
- storage security;
- database schema;
- API contract;
- frontend application code, salvo assoluta necessità documentata;
- architettura applicativa.

Non trasformare MOD-006 in un nuovo audit funzionale.

---

# 4. Fase 1 — Analisi

Prima di modificare il repository determinare:

### Backend

- quale immagine usa attualmente Compose;
- perché viene usata l'immagine upstream;
- se `api/Dockerfile` è sufficiente per una build ufficiale;
- se il Dockerfile deve essere modificato;
- quale tag locale/repository deve essere utilizzato.

### Compose

Verificare se è preferibile:

### Opzione A
```yaml
build:
  context: ./api
```

oppure:

### Opzione B
```yaml
image: atlas-cmms-backend:...
```

con build separato.

Preferire la soluzione più semplice, riproducibile e coerente con il repository attuale.

NON introdurre un registry esterno se non necessario.

---

# 5. Requisito fondamentale

Il deployment documentato non deve permettere accidentalmente questa situazione:

```text
sorgente modificato
        ↓
docker compose up
        ↓
intelloop/atlas-cmms-backend upstream
        ↓
MOD-001/004B assenti
```

La configurazione finale deve rendere evidente quale immagine viene eseguita e da quale sorgente viene costruita.

Se Compose utilizza `build:`, verificare che:

```text
docker compose build
```

produca il backend dai sorgenti correnti.

Se viene mantenuto `image:`, documentare chiaramente come viene costruita/taggata l'immagine e come si garantisce la corrispondenza con il commit.

---

# 6. Provenienza immagine

Dopo l'implementazione verificare:

```text
source commit
Dockerfile
build context
image tag
container image ID
```

e dimostrare che il container contiene almeno:

```text
MOD-001 self-hosted licensing
MOD-004B attachment security
```

Non basta verificare che il build termini con successo.

Ripetere la verifica bytecode/metodi caratteristici usata in MOD-005, in forma sintetica.

---

# 7. CFG-01 — MAIL_RECIPIENTS

Analizzare:

```text
application.yml
emailService2
MAIL_RECIPIENTS
```

Obiettivo:

Un deployment senza email notifications configurate non deve fallire il bootstrap se le email sono una funzionalità opzionale.

Prima di modificare:

- verificare come viene utilizzata la variabile;
- verificare se `ENABLE_EMAIL_NOTIFICATIONS=false` dovrebbe rendere inutile `MAIL_RECIPIENTS`;
- verificare se esiste già una convenzione per valori opzionali;
- verificare i test esistenti.

Se è sicuro e coerente con il comportamento esistente, applicare la modifica minima necessaria.

Non cambiare il comportamento delle notifiche quando sono abilitate.

Aggiungere/regolare test solo se necessario.

---

# 8. CFG-02 — Frontend / nginx

Analizzare il problema descritto da MOD-005:

```text
frontend prebuilt
        ↓
nginx upstream frontend:3000
        ↓
frontend down
        ↓
nginx startup failure
```

Determinare se il problema è:

- una caratteristica normale di nginx;
- un problema del compose;
- una configurazione migliorabile;
- una dipendenza necessaria per il deployment.

NON implementare automaticamente una soluzione `resolver` dinamica o simili.

Prima determinare la soluzione minima e più robusta.

Obiettivo:

Un errore di configurazione/startup del frontend non dovrebbe impedire inutilmente al sistema di essere diagnosticabile o, se tecnicamente possibile senza modifiche architetturali, impedire il funzionamento dei servizi indipendenti.

Se la soluzione richiede modifiche nginx sostanziali, **fermarsi e documentare**, invece di introdurle autonomamente.

---

# 9. Non modificare production

Il deployment corrente potrebbe essere utilizzato da utenti.

Non eseguire:

```text
docker compose down
docker volume rm
docker system prune
docker network rm
```

sullo stack production.

Per la verifica creare uno stack isolato, come in MOD-005:

```text
atlas-cmms-mod006
```

con:

- PostgreSQL dedicato;
- MinIO dedicato;
- network dedicata;
- volumi dedicati;
- porte locali dedicate.

---

# 10. Test di build

Dopo le modifiche:

```bash
docker compose config
docker compose build
```

Verificare:

- nessuna variabile critica mancante;
- build backend PASS;
- frontend PASS;
- nginx PASS.

Registrare eventuali warning.

---

# 11. Test runtime

Avviare lo stack isolato.

Verificare:

```text
postgres      UP
minio         UP
api           UP
frontend      UP
nginx         UP
```

Poi verificare:

```text
GET /
GET /api/auth/me
```

e almeno una verifica API autenticata.

---

# 12. Regressione funzionale minima

NON ripetere tutto MOD-005.

È sufficiente verificare che il deployment ufficiale ora utilizzi il backend corretto e che le funzionalità già validate non siano regredite.

Eseguire:

### Build
```text
docker compose build
```

### Test backend
```text
mvnw test
```

Atteso:

```text
1445 tests
0 failures
0 errors
0 skipped
```

### Runtime smoke test

Verificare almeno:

1. startup;
2. self-hosted licensing;
3. PlanFeatures.FILE;
4. upload allegato;
5. download;
6. delete.

Se questi test falliscono, classificare la regressione e fermarsi.

---

# 13. Security

Non modificare i controlli di sicurezza già verificati.

In particolare devono rimanere invariati:

- self-hosted licensing;
- authorization;
- tenant isolation;
- `CompanyAudit`;
- attachment security;
- `Content-Disposition`;
- `nosniff`;
- `X-Frame-Options`;
- MinIO lifecycle.

Se una modifica di deployment li altera, il MOD deve essere considerato FAIL.

---

# 14. Documentazione

Creare:

```text
docs/self-hosted-audit/24-mod006-deployment-alignment.md
```

Struttura:

```text
# MOD-006 — Deployment Alignment

## 1. Objective
## 2. Initial State
## 3. Root Cause
## 4. Decision
## 5. Changes Implemented
## 6. Docker Build
## 7. Image Provenance
## 8. CFG-01 Evaluation
## 9. CFG-02 Evaluation
## 10. Isolated Runtime Verification
## 11. Regression Tests
## 12. Security Regression
## 13. Findings
## 14. Final Verdict
## 15. Remaining Work
```

Per ogni verifica:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

---

# 15. Git

Consentito:

```bash
git status
git diff
git log
git show
```

NON eseguire:

```bash
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

---

# 16. Secret handling

Non inserire nei documenti:

- password;
- JWT secret;
- DB password;
- MinIO secret;
- SMTP password;
- OAuth secret;
- token.

Usare:

```text
********
```

---

# 17. Context management

Mantieni il contesto limitato a:

```text
Compose
Dockerfile
nginx
application configuration
CFG-01
CFG-02
deployment documentation
```

Non analizzare nuovamente tutto il backend.

Non riaprire MOD già verificati.

---

# 18. Anti-hallucination

NON inventare:

- comportamento Docker;
- variabili;
- dipendenze;
- configurazioni;
- requisiti;
- API.

Se non è possibile stabilire qualcosa:

```text
UNKNOWN / DA VERIFICARE
```

---

# 19. Definition of Done

MOD-006 è completato quando:

- il deployment self-hosted usa esplicitamente il backend costruito dai sorgenti;
- il build è riproducibile;
- la provenienza dell'immagine è verificata;
- CFG-01 è risolto oppure motivatamente lasciato aperto;
- CFG-02 è risolto oppure motivatamente lasciato aperto;
- lo stack isolato parte;
- smoke test PASS;
- `mvnw test` resta 1445/1445;
- nessuna regressione di sicurezza;
- `24-mod006-deployment-alignment.md` è prodotto;
- nessun ambiente production è stato modificato.

---

# 20. STOP CONDITION

Al termine:

**STOP.**

Non iniziare MOD-007.

Non modificare licensing.

Non modificare le feature applicative.

Non aggiungere funzionalità.

Non modificare l'architettura.

Non aggiornare automaticamente `CLAUDE.md` o gli audit precedenti.

Restituire:

1. verdict MOD-006;
2. modifiche effettuate;
3. build result;
4. runtime result;
5. test result;
6. stato CFG-01;
7. stato CFG-02;
8. eventuali finding;
9. percorso del report;
10. raccomandazione per il prossimo intervento.

La decisione successiva verrà presa dal responsabile tecnico dopo la revisione del report.
