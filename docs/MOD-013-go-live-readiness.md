# MOD-013 — Final Pre-Production Readiness & Device Validation

## 1. Contesto

MOD-012 è concluso con:

```text
Mobile status: PASS WITH FINDINGS
Final verdict: PASS WITH FINDINGS
Code changes: NONE
P0: 0
P1: 0
P2: 0
P3: 0
F-04 mobile impact: NONE OBSERVED
```

Il report conferma che il contratto mobile ↔ backend è compatibile a livello di codice e protocollo:

- `/auth/signin` → 200;
- Asset full-DTO PATCH → 200;
- upload allegati → 200;
- download da presigned URL → 200;
- licensing mobile → PASS;
- nessun gate commerciale mobile;
- F-04 non viene attivato dalla shape utilizzata dall'app.

La sola limitazione di MOD-012 è che non è stato possibile eseguire la GUI mobile reale perché l'ambiente di test è Windows headless senza Android SDK/ADB/emulatore/Expo e iOS richiede macOS + Xcode.

Il prossimo obiettivo è quindi preparare la **decisione finale sul deployment production**, senza modificare ancora production.

---

# 2. Obiettivo

Eseguire l'ultimo controllo tecnico prima del deployment live.

Il MOD deve:

1. verificare lo stato complessivo del progetto;
2. verificare che non esistano blocker aperti;
3. verificare backup e possibilità di rollback;
4. verificare che la configurazione production sia documentata;
5. verificare la procedura di deployment;
6. verificare le dipendenze da Caddy/DNS/certificati;
7. identificare esattamente cosa dovrà essere fatto su `websrv01`;
8. distinguere chiaramente:
   - ciò che è già verificato;
   - ciò che deve ancora essere verificato;
   - ciò che richiede approvazione;
9. se è disponibile un dispositivo Android/iOS reale, eseguire il runtime test GUI;
10. NON effettuare il deployment production.

---

# 3. Principio fondamentale

Questo MOD NON è un deployment.

NON modificare:

- `websrv01`;
- Docker production;
- Caddy production;
- DNS;
- certificati;
- database production;
- MinIO production;
- configurazioni production;
- repository remoto.

Il risultato deve essere un **Go-Live Readiness Report**.

---

# 4. Documentazione da leggere

Prima:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/30-mod012-mobile-runtime-acceptance.md`;
3. `docs/self-hosted-audit/29-mod011-f01-fix-verification.md`;
4. `docs/self-hosted-audit/28-mod010-local-acceptance-test.md`;
5. `docs/self-hosted-audit/27-mod009-mobile-compatibility-audit.md`;
6. documentazione deployment già presente nel repository;
7. documentazione backup/restore già presente.

Non leggere l'intero repository.

---

# 5. Stato dei blocker

Costruire una tabella:

| Area | Stato | Blocker? | Evidence |
|---|---|---|---|
| Backend | | | |
| PostgreSQL | | | |
| MinIO | | | |
| Licensing | | | |
| Authentication | | | |
| Authorization | | | |
| Multi-tenancy | | | |
| Assets | | | |
| Work Orders | | | |
| Attachments | | | |
| Frontend | | | |
| Mobile contract | | | |
| Mobile GUI | | | |
| F-01 | | | |
| F-04 | | | |
| Backup | | | |
| Restore | | | |
| Deployment procedure | | | |

Non classificare come blocker una semplice limitazione di test se esiste un modo ragionevole per completarla prima del live.

---

# 6. Mobile Device Test

Se durante questa sessione è disponibile un:

- dispositivo Android reale;
- dispositivo iOS reale;

eseguire il test GUI previsto da MOD-012.

### Android

Verificare:

- installazione;
- Custom Server;
- connessione;
- login;
- logout;
- Asset;
- Work Order;
- allegati;
- QR/barcode;
- licensing;
- offline read.

### iOS

Verificare le stesse funzioni quando tecnicamente possibile.

Se nessun device è disponibile:

```text
NOT TESTED
```

Non installare SDK/emulatori se questo richiede una modifica significativa dell'ambiente e non è necessario.

Non modificare il codice mobile.

---

# 7. F-04

NON modificare F-04.

Il test MOD-012 ha stabilito:

```text
F-04 mobile impact = NONE OBSERVED
```

Mantenerlo come issue non urgente separata.

Non proporre una modifica globale a MapStruct solo per eliminare F-04.

---

# 8. Backup Production

Verificare che prima del deployment sia possibile eseguire almeno:

### PostgreSQL

```text
pg_dump
```

o la procedura già prevista dal progetto.

### MinIO

Identificare esattamente come verranno salvati:

```text
bucket
objects
metadata
```

### Configuration

Identificare quali file/configuration devono essere salvati:

- compose;
- `.env`;
- Caddyfile;
- nginx;
- certificati;
- configurazioni applicative.

NON stampare secret.

---

# 9. Rollback

Definire una procedura concreta:

```text
backup
↓
deployment
↓
verification
↓
FAIL?
↓
restore previous application version
↓
restore database/storage se necessario
```

Non inventare comandi.

Utilizzare esclusivamente procedure già supportate dal repository/server.

Se una procedura di rollback non è sufficientemente definita:

```text
BLOCKER / OPEN DECISION
```

a seconda dell'impatto.

---

# 10. Production Deployment Plan

Ricostruire la procedura esatta che dovrà essere eseguita sul server.

Deve includere almeno:

1. backup;
2. verifica spazio disco;
3. verifica Docker;
4. stop/start o recreate dei container necessari;
5. database;
6. MinIO;
7. backend;
8. frontend;
9. nginx/Caddy;
10. verifica rete;
11. verifica endpoint;
12. smoke test;
13. rollback se necessario.

NON eseguire questi comandi.

Documentarli soltanto.

---

# 11. Caddy / Domain

Verificare nella documentazione esistente la configurazione prevista per:

```text
wiki.firmabratex.pl
```

e distinguere chiaramente questa configurazione da Atlas.

Per Atlas identificare:

- dominio previsto;
- reverse proxy;
- porta interna;
- rete Docker;
- certificato;
- eventuale configurazione DNS.

NON modificare Caddy.

NON modificare DNS.

Se il dominio Atlas non è ancora definito:

```text
OPEN DECISION
```

---

# 12. Production Storage

Verificare che la strategia production utilizzi storage persistente e non storage effimero.

Per PostgreSQL verificare:

```text
/srv/data/databases/atlas/postgres
```

solo come riferimento documentale se questa è effettivamente la configurazione production già stabilita.

Per MinIO verificare:

```text
/srv/data/databases/atlas/minio
```

solo se confermato dalla configurazione reale.

NON modificare filesystem production.

---

# 13. Environment Variables

Creare una checklist delle variabili necessarie.

Non riportare valori.

Esempio:

```text
POSTGRES_USER              PRESENT
POSTGRES_PWD               PRESENT
MINIO_USER                 PRESENT
MINIO_PASSWORD             PRESENT
JWT_SECRET_KEY             PRESENT
PUBLIC_SERVER_URL          PRESENT
...
```

Per ogni variabile:

```text
REQUIRED
OPTIONAL
NOT REQUIRED
UNKNOWN
```

Se una variabile critica non è documentata:

```text
OPEN DECISION
```

---

# 14. Production Security

Verificare concettualmente:

- secret non presenti nel repository;
- TLS previsto;
- backend non esposto inutilmente;
- PostgreSQL non esposto pubblicamente;
- MinIO non esposto pubblicamente se non necessario;
- reverse proxy unico punto di ingresso;
- authorization attiva;
- company isolation attiva.

Non modificare security configuration durante questo MOD.

---

# 15. Health Checks

Definire gli smoke test post-deployment:

```text
HTTPS endpoint
    ↓
frontend 200
    ↓
API reachable
    ↓
login
    ↓
Asset
    ↓
Work Order
    ↓
attachment
```

Definire anche i comandi CLI che dovranno essere eseguiti sul server.

NON eseguirli sul server.

---

# 16. Data Migration

Determinare se esistono dati Atlas locali da migrare.

Se l'ambiente production è vuoto:

```text
DATA MIGRATION = NONE
```

Se esistono dati:

identificare:

- PostgreSQL;
- MinIO;
- configurazioni;
- eventuali secret;
- utenti;
- organization/company.

Non eseguire migrazioni.

---

# 17. Final Go-Live Checklist

Creare:

```text
[ ] Backup PostgreSQL
[ ] Backup MinIO
[ ] Backup configuration
[ ] Rollback procedure
[ ] Production compose verified
[ ] Environment variables verified
[ ] Domain verified
[ ] DNS verified
[ ] TLS verified
[ ] Caddy configuration ready
[ ] Storage paths verified
[ ] Docker networks verified
[ ] Backend image/version identified
[ ] Frontend image/version identified
[ ] Database version identified
[ ] MinIO version identified
[ ] Smoke tests defined
[ ] Mobile GUI verified OR explicitly deferred
```

---

# 18. Decision Matrix

Il report deve terminare con:

```text
GO-LIVE STATUS:
READY
READY WITH CONDITIONS
NOT READY
```

### READY

Solo se non esistono blocker tecnici.

### READY WITH CONDITIONS

Se esistono solamente attività operative da completare durante il deployment, già chiaramente definite.

### NOT READY

Se manca:

- backup;
- rollback;
- configurazione critica;
- sicurezza;
- database/storage;
- dominio/TLS;
- procedura deployment.

---

# 19. Documentation

Produrre:

```text
docs/self-hosted-audit/31-mod013-go-live-readiness.md
```

Struttura:

```text
# MOD-013 — Go-Live Readiness

## 1. Objective
## 2. Current Version
## 3. Audit Summary
## 4. Mobile Device Test
## 5. F-04 Status
## 6. Backup Readiness
## 7. Rollback Readiness
## 8. Production Architecture
## 9. Domain / Reverse Proxy
## 10. Storage
## 11. Environment Variables
## 12. Security
## 13. Health Checks
## 14. Data Migration
## 15. Deployment Procedure
## 16. Final Checklist
## 17. Blockers
## 18. Open Decisions
## 19. CLAUDE.md Update
## 20. Final Verdict
```

---

# 20. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

- Current Project State;
- MOD-013;
- Go-Live readiness;
- Mobile status;
- Known Issues;
- Open Decisions;
- Documentation Map.

Non lasciare informazioni obsolete.

---

# 21. Git

Consentito:

```bash
git status
git diff
git diff --check
git log
```

Non eseguire:

```bash
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

---

# 22. Anti-Hallucination

NON inventare:

- dominio Atlas;
- porte production;
- path;
- comandi;
- certificati;
- DNS;
- procedure backup;
- procedure rollback;
- secret;
- configurazioni.

Se non è documentato:

```text
UNKNOWN / TO VERIFY
```

e indicare esattamente cosa manca.

---

# 23. STOP CONDITION

Al termine:

**STOP.**

NON:

- fare deployment;
- modificare `websrv01`;
- modificare Caddy;
- modificare DNS;
- modificare certificati;
- modificare database production;
- modificare MinIO production;
- modificare frontend;
- modificare mobile;
- correggere F-04;
- iniziare MOD-014.

Il deployment verrà eseguito solo dopo la revisione del report MOD-013 e una decisione esplicita.

---

# 24. Final Output

Output obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: NONE
Mobile Android: PASS / NOT TESTED
Mobile iOS: PASS / NOT TESTED
F-04: NONE OBSERVED / OPEN
Backup readiness: PASS / FAIL
Rollback readiness: PASS / FAIL
Production configuration: READY / INCOMPLETE
Domain/TLS: READY / INCOMPLETE
Security: PASS / FINDINGS
P0: X
P1: X
P2: X
P3: X
Go-live status: READY / READY WITH CONDITIONS / NOT READY
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
