# MOD-010 — Local Acceptance Test

## 1. Contesto

MOD-009 è concluso con verdict **PASS**.

Risultato consolidato:

- backend self-hosted: PASS;
- licensing self-hosted: PASS;
- frontend web: CLEAN;
- Android: COMPATIBLE WITH CONFIGURATION;
- iOS: COMPATIBLE WITH CONFIGURATION;
- API mobile: compatibili;
- autenticazione: compatibile;
- feature gate: soddisfatti dal backend self-hosted;
- allegati: compatibili;
- nessuna modifica necessaria al mobile;
- nessuna modifica necessaria al frontend web.

L'app mobile ufficiale può utilizzare il backend self-hosted tramite la schermata `CustomServer`.

Restano due aspetti non ancora verificati:

- push FCM lato backend;
- effettivo write-sync offline.

Il progetto non deve ancora essere considerato pronto per il deployment live.

---

# 2. Obiettivo

Eseguire il **primo vero Acceptance Test locale** dell'Atlas modificato.

Lo scopo è verificare che il sistema completo funzioni realmente come prodotto utilizzabile prima di intervenire sul server production.

Il test deve comprendere:

```text
Backend
+
PostgreSQL
+
MinIO
+
Frontend web
+
API
+
Mobile Android/iOS
```

quando tecnicamente possibile.

Il risultato deve stabilire se Atlas è sufficientemente stabile per passare alla successiva fase di deployment live.

---

# 3. Principio fondamentale

Questo MOD è principalmente un'attività di **verifica**, non di sviluppo.

NON introdurre nuove funzionalità.

NON modificare il licensing.

NON modificare il frontend.

NON modificare il mobile.

NON implementare CFG-02.

NON effettuare modifiche architetturali.

Se viene trovato un problema:

```text
TEST FAIL
    ↓
documentare
    ↓
analizzare causa
    ↓
se è un bug semplice e chiaramente nello scope:
    proporre fix / chiedere approvazione secondo CLAUDE.md
altrimenti:
    STOP e registrare finding
```

Non correggere automaticamente problemi architetturali o fuori scope.

---

# 4. Fonti da leggere

Prima di iniziare leggere:

1. `CLAUDE.md`;
2. `22-audit-consolidation.md`;
3. `23-mod005-runtime-integration-verification.md`;
4. `24-mod006-deployment-alignment.md`;
5. `25-mod007-documentation-baseline.md`;
6. `26-mod008-frontend-licensing-audit.md`;
7. `27-mod009-mobile-compatibility-audit.md`.

Questi documenti costituiscono la baseline.

Non rileggere automaticamente tutto il repository.

---

# 5. Ambiente

L'Acceptance Test deve essere eseguito **localmente**.

Non utilizzare il server production `websrv01`.

Non modificare:

- `/srv/docker/...`;
- Caddy;
- nginx production;
- DNS;
- certificati production;
- database production;
- MinIO production.

Se il repository dispone già di Docker Compose per lo sviluppo, utilizzare quello.

Se esistono più modalità di avvio, scegliere quella documentata dal progetto.

Prima di avviare lo stack verificare:

```bash
git status
docker compose config
```

e identificare:

- servizi;
- porte;
- volumi;
- variabili ambiente;
- database;
- storage.

Non stampare secret nei report.

---

# 6. Pre-flight

Prima dei test:

### Repository

```bash
git status
git branch --show-current
git log -1 --oneline
```

Registrare commit/versione testata.

### Compose

```bash
docker compose config
```

Deve essere valido.

### Build

Verificare che le immagini/build necessarie siano disponibili.

Se il progetto richiede build dal source, verificare che il backend utilizzato sia realmente quello modificato e non un'immagine upstream contenente il backend originale.

Questo punto è fondamentale.

---

# 7. Avvio

Avviare lo stack locale secondo la configurazione documentata.

Verificare:

```bash
docker compose ps
```

Tutti i servizi necessari devono essere `running`/`healthy` secondo la loro configurazione.

Controllare i log solo quando necessario:

```bash
docker compose logs --tail=...
```

Non scaricare indiscriminatamente grandi quantità di log.

---

# 8. Infrastructure Test

Verificare:

### PostgreSQL

- container attivo;
- database raggiungibile;
- schema presente;
- connessione backend funzionante.

### MinIO

- container attivo;
- bucket presente;
- backend connesso;
- storage scrivibile.

### Backend

- startup senza errori bloccanti;
- API raggiungibili;
- configurazione licensing corretta.

### Frontend

- pagina caricabile;
- asset JS/CSS caricati;
- API URL corretto.

---

# 9. Licensing Acceptance

Questo è uno dei test fondamentali.

Verificare a runtime:

```text
/license/state
```

o equivalente endpoint già identificato nella documentazione.

Expected:

```text
SELF_HOSTED
BUSINESS
valid=true
```

Verificare inoltre che il piano contenga tutte le `PlanFeatures` previste dal progetto.

Non assumere che la presenza della configurazione sia sufficiente: verificare il comportamento runtime.

---

# 10. Authentication Test

Creare/usa un account di test.

Verificare:

1. login;
2. logout;
3. login nuovamente;
4. sessione/token;
5. accesso alle pagine autorizzate;
6. accesso negato alle risorse non autorizzate.

Documentare:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

Non utilizzare account production.

---

# 11. Organization / Company / Authorization

Verificare il modello di isolamento già definito dal progetto.

Creare almeno, se supportato dall'ambiente:

```text
Company A
Company B
User A
User B
```

Verificare che:

- User A veda Company A;
- User B veda Company B;
- non sia possibile accedere direttamente a dati dell'altra company;
- i ruoli continuino a funzionare;
- gli admin abbiano i permessi previsti.

Questo test è importante per confermare il comportamento multi-tenant a runtime.

---

# 12. Core CMMS Test

Verificare almeno:

### Assets

- creazione;
- visualizzazione;
- modifica;
- ricerca;
- eliminazione se prevista.

### Work Orders

- creazione;
- modifica;
- assegnazione;
- cambio stato;
- visualizzazione;
- ricerca.

### Parts / Components

Se disponibili:

- creazione;
- associazione;
- modifica;
- visualizzazione.

### Meters

Se disponibili:

- creazione;
- lettura;
- aggiornamento.

Non trasformare questo test in un inventario completo di ogni funzione Atlas.

L'obiettivo è verificare il flusso principale end-to-end.

---

# 13. Attachments

Eseguire un test completo:

```text
UPLOAD
  ↓
STORAGE
  ↓
DOWNLOAD
  ↓
VIEW
  ↓
DELETE
```

Testare almeno:

- immagine;
- PDF;
- file non immagine.

Verificare che:

- upload riuscito;
- file presente in MinIO;
- download riuscito;
- immagine visualizzabile;
- file non immagine scaricato come attachment;
- delete riuscito;
- file eliminato dallo storage quando previsto;
- nessun accesso non autorizzato.

Verificare specificamente la compatibilità con MOD-004B/004C.

---

# 14. Security Test

Eseguire solo test non distruttivi.

Verificare almeno:

- accesso a risorsa appartenente ad altra company;
- accesso a file non autorizzato;
- modifica di risorsa non autorizzata;
- endpoint protetti senza token;
- endpoint protetti con token di altro utente.

Non effettuare penetration testing aggressivo.

---

# 15. Persistence Test

Questo è fondamentale.

Creare dati di test:

```text
asset
work order
attachment
```

Poi:

```bash
docker compose restart
```

Verificare che i dati rimangano.

Successivamente, se sicuro e documentato:

```bash
docker compose down
docker compose up -d
```

Verificare nuovamente:

- database;
- asset;
- work order;
- allegati;
- MinIO.

NON utilizzare `docker compose down -v`.

NON cancellare volumi.

---

# 16. Backup / Restore

Se la procedura di backup locale è già disponibile:

1. creare backup PostgreSQL;
2. verificare che il file sia valido;
3. documentare la procedura di restore.

Non distruggere l'ambiente funzionante per provare un restore distruttivo.

Un restore completo può essere lasciato come test separato se richiede un ambiente dedicato.

---

# 17. Frontend Web Acceptance

Verificare le funzionalità già analizzate in MOD-008:

- login;
- dashboard;
- company;
- users;
- roles;
- assets;
- work orders;
- attachments;
- feature già sbloccate.

Prestare attenzione a eventuali:

- pulsanti nascosti;
- upgrade prompt;
- errori console;
- API 401/403;
- API 404/500.

Non modificare il frontend se un test fallisce.

---

# 18. Mobile Acceptance

Se è possibile utilizzare un dispositivo/emulatore senza modificare il codice:

### Configurazione

Aprire:

```text
Login
→ Custom Server
```

Impostare l'URL del backend locale raggiungibile dal dispositivo.

ATTENZIONE:

`localhost` dal telefono indica il telefono stesso, non il PC/server.

Utilizzare quindi l'indirizzo LAN appropriato del computer che ospita Atlas.

### Test

Verificare:

- connessione;
- login;
- assets;
- work orders;
- allegati;
- visualizzazione immagini;
- download file;
- QR/barcode se disponibile.

Non modificare il codice mobile.

---

# 19. FCM / Push

Tentare di verificare il comportamento push solo se l'ambiente locale è già configurato.

Se richiede configurazione Firebase/backend non presente:

```text
DA VERIFICARE
```

Non creare automaticamente una nuova infrastruttura Firebase.

---

# 20. Offline

Verificare almeno:

- caching locale;
- riapertura app senza rete;
- disponibilità dei dati già caricati.

Per il write-sync:

- creare una modifica offline solo se il comportamento è chiaramente supportato;
- verificare successivamente il sync.

Se il comportamento non è implementato o non è determinabile:

```text
DA VERIFICARE
```

Non implementare il sync.

---

# 21. Restart / Recovery

Simulare:

```text
backend restart
frontend restart
database restart
MinIO restart
```

solo singolarmente e in ambiente locale.

Verificare che:

- i servizi ripartano;
- le connessioni vengano ristabilite;
- i dati rimangano;
- non ci siano corruption/errori persistenti.

---

# 22. Test Matrix

Creare una matrice finale:

| Area | Test | Expected | Actual | Result |
|---|---|---|---|---|
| Infrastructure | ... | ... | ... | PASS/FAIL |
| Licensing | ... | ... | ... | ... |
| Auth | ... | ... | ... | ... |
| Authorization | ... | ... | ... | ... |
| Company isolation | ... | ... | ... | ... |
| Assets | ... | ... | ... | ... |
| Work Orders | ... | ... | ... | ... |
| Attachments | ... | ... | ... | ... |
| Persistence | ... | ... | ... | ... |
| Frontend | ... | ... | ... | ... |
| Mobile | ... | ... | ... | ... |
| Push | ... | ... | ... | ... |
| Offline | ... | ... | ... | ... |

---

# 23. Classification

Ogni problema deve essere classificato:

### P0 — Critical

Impedisce l'avvio o compromette dati/sicurezza.

### P1 — Blocking

Impedisce l'utilizzo di una funzione core.

### P2 — Major

Problema importante ma aggirabile.

### P3 — Minor

Problema non bloccante.

### INFO

Miglioramento o comportamento da conoscere.

---

# 24. Fix Policy

Durante MOD-010:

### Consentito senza nuova approvazione

Solo se il problema è:

- chiaramente un bug;
- minimale;
- direttamente nello scope;
- non architetturale;
- non modifica database schema;
- non modifica licensing;
- non modifica security model;
- non modifica API contract.

### Richiede decisione

- modifica architetturale;
- nuova dipendenza;
- database migration;
- licensing;
- authentication;
- authorization;
- API contract;
- Docker architecture;
- production configuration.

Se un fix richiede decisione:

```text
STOP
documenta
proponi soluzione
attendi decisione
```

---

# 25. No Production

È VIETATO utilizzare durante questo MOD:

- server `websrv01` come target;
- database production;
- MinIO production;
- Caddy production;
- DNS production;
- certificati production.

Il test deve rimanere locale.

---

# 26. Documentation

Produrre:

```text
docs/self-hosted-audit/28-mod010-local-acceptance-test.md
```

Struttura:

```text
# MOD-010 — Local Acceptance Test

## 1. Objective
## 2. Environment
## 3. Version Tested
## 4. Pre-flight
## 5. Infrastructure
## 6. Licensing
## 7. Authentication
## 8. Authorization
## 9. Company Isolation
## 10. Assets
## 11. Work Orders
## 12. Attachments
## 13. Security
## 14. Persistence
## 15. Backup
## 16. Frontend
## 17. Mobile
## 18. Push Notifications
## 19. Offline
## 20. Restart/Recovery
## 21. Test Matrix
## 22. Findings
## 23. Fixes
## 24. Remaining Issues
## 25. Production Readiness
## 26. CLAUDE.md Update
## 27. Final Verdict
```

---

# 27. CLAUDE.md

Aggiornare `CLAUDE.md` al termine.

Aggiornare:

- Current Project State;
- Documentation Map;
- Known Issues;
- Open Decisions;
- MOD-010 status.

Se vengono effettuate modifiche al codice, registrarle.

Se non vengono effettuate modifiche:

```text
Code changes: NONE
```

---

# 28. Git

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

# 29. Secret Handling

Non inserire nei report:

- password;
- token;
- JWT;
- API key;
- OAuth secret;
- Firebase credentials;
- database password;
- SMTP password.

Mascherare:

```text
********
```

---

# 30. Definition of Done

MOD-010 è completato quando:

- l'ambiente locale è stato avviato;
- la versione testata è identificata;
- backend verificato;
- PostgreSQL verificato;
- MinIO verificato;
- licensing verificato a runtime;
- authentication verificata;
- authorization verificata;
- company isolation verificata;
- assets verificati;
- work orders verificati;
- attachments verificati;
- persistence verificata;
- frontend verificato;
- mobile verificato se tecnicamente possibile;
- push classificato;
- offline classificato;
- restart/recovery verificato;
- findings classificati;
- report prodotto;
- `CLAUDE.md` aggiornato;
- verdict finale espresso.

---

# 31. Production Readiness

Il report deve terminare con una valutazione:

```text
PRODUCTION READINESS:
READY
READY WITH FINDINGS
NOT READY
```

Non considerare il sistema READY se esistono P0/P1 aperti.

Non considerare automaticamente un sistema NOT READY per problemi P3 o INFO.

Separare:

```text
BLOCKERS
NON-BLOCKERS
OPTIONAL
DA VERIFICARE
```

---

# 32. STOP CONDITION

Al termine:

**STOP.**

Non eseguire deployment live.

Non modificare server production.

Non configurare Caddy production.

Non configurare DNS.

Non effettuare migrazioni production.

Non iniziare un nuovo MOD.

La decisione sul deployment live verrà presa dopo la revisione del report MOD-010.

Output finale obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: NONE / LIST
Environment: LOCAL
Tests executed: X
PASS: X
FAIL: X
P0: X
P1: X
P2: X
P3: X
Mobile: PASS / PARTIAL / NOT TESTED
Production readiness: READY / READY WITH FINDINGS / NOT READY
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
