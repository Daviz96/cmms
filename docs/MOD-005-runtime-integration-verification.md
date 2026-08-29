# MOD-005 — Verifica di integrazione runtime self-hosted

## 1. Ruolo

Sei il coding agent incaricato di eseguire **MOD-005** sul repository Atlas CMMS self-hosted.

Il responsabile tecnico ha deciso di procedere con MOD-005 sulla base dell'audit consolidato `22-audit-consolidation.md`.

Questa attività ha come obiettivo principale **verificare nel processo applicativo reale** che le modifiche già realizzate nei MOD precedenti siano effettivamente presenti e funzionanti quando il backend viene costruito dai sorgenti e avviato nello stack Docker self-hosted.

Non devi inventare nuove funzionalità e non devi riaprire decisioni già approvate.

---

# 2. Obiettivo

Verificare end-to-end, usando un'immagine backend costruita dai sorgenti del repository:

1. self-hosted licensing;
2. entitlement;
3. PlanFeatures / piano BUSINESS;
4. allegati;
5. upload;
6. download;
7. comportamento HTTP degli allegati;
8. delete;
9. lifecycle MinIO;
10. isolamento tra company/tenant;
11. corretto funzionamento dell'intero stack applicativo.

L'obiettivo è dimostrare che:

```text
repository sorgente
        ↓
build immagine backend
        ↓
container backend
        ↓
PostgreSQL + MinIO
        ↓
nginx
        ↓
API reale
        ↓
comportamento self-hosted atteso
```

funziona realmente.

---

# 3. Fonte primaria

Prima di iniziare devi leggere:

1. `CLAUDE.md`
2. `docs/self-hosted-audit/22-audit-consolidation.md`
3. documentazione MOD-001
4. documentazione MOD-004B
5. documentazione MOD-004C
6. `docker-compose.yml` / `compose.yml`
7. `Dockerfile` o Dockerfile backend, se presente
8. configurazioni nginx correlate
9. documentazione relativa al build/deploy presente nel repository.

In particolare devi considerare vincolanti le conclusioni dell'audit 22:

- MOD-001 PASS;
- MOD-002 PASS;
- MOD-003A PASS;
- MOD-004B PASS WITH FINDINGS;
- MOD-004C PASS;
- suite 1445/1445 PASS;
- targeted allegati 6/6 PASS;
- immagini Docker attualmente utilizzate dal compose sono prebuilt upstream;
- MOD-005 serve a validare il codice modificato **nel processo applicativo reale**.

Non ripetere inutilmente l'intero audit.

---

# 4. Regola fondamentale: prima analisi, poi esecuzione

La prima fase di MOD-005 è esclusivamente diagnostica.

Prima di costruire o avviare qualcosa:

- identifica come viene costruito il backend;
- identifica il Dockerfile corretto;
- identifica il contesto Docker;
- verifica se esiste già una procedura ufficiale di build;
- identifica quali immagini vengono utilizzate dal compose;
- verifica quali immagini contengono realmente il codice sorgente modificato;
- identifica eventuali variabili necessarie;
- verifica i volumi;
- verifica PostgreSQL;
- verifica MinIO;
- verifica nginx;
- verifica eventuali network;
- verifica eventuali secret.

Non modificare ancora il repository.

---

# 5. Divieto di contaminare l'ambiente esistente

L'ambiente Atlas attualmente in esecuzione potrebbe utilizzare dati reali/test già presenti.

NON fare:

- `docker compose down` sull'ambiente esistente senza necessità;
- `docker volume rm`;
- cancellazione database;
- cancellazione bucket;
- cancellazione file;
- `docker system prune`;
- modifica irreversibile dell'ambiente esistente;
- sostituzione delle immagini production senza approvazione;
- modifica dei file `.env` esistenti senza necessità;
- modifica della configurazione DNS;
- modifica nginx/Caddy production;
- modifica del database esistente.

Preferisci creare un **ambiente di verifica isolato**.

Se l'isolamento non è possibile, fermati e documenta il problema prima di procedere.

---

# 6. Strategia ambiente di verifica

Determina la strategia migliore tra:

### A — Stack temporaneo isolato

Preferibile.

Creare un compose/project name separato, ad esempio:

```text
atlas-cmms-mod005
```

con:

- backend buildato localmente;
- frontend esistente o necessario;
- PostgreSQL dedicato;
- MinIO dedicato;
- nginx dedicato se necessario.

Usare volumi temporanei o directory dedicate.

### B — Build locale senza sostituzione production

Se il repository permette di costruire l'immagine e testarla tramite container temporaneo, preferire questa soluzione.

### C — Ambiente esistente

Usarlo solamente se non esiste alternativa sicura e se è dimostrato che non vengono modificati dati/configurazioni persistenti.

Documentare la scelta.

---

# 7. Build del backend

Identifica il Dockerfile reale del backend.

Prima di eseguire il build, documenta:

```text
Dockerfile:
Build context:
Base image:
Java/runtime:
Build command:
Output image:
Tag:
Source commit:
```

Costruisci l'immagine dai sorgenti attuali.

NON usare semplicemente:

```text
intelloop/atlas-cmms-backend
```

come backend di verifica se questo significa usare l'immagine upstream.

L'immagine MOD-005 deve contenere il codice presente nel repository che include almeno:

- MOD-001;
- MOD-002;
- MOD-004B.

Verifica realmente il contenuto dell'immagine.

Non assumere che un build sia corretto solo perché termina con exit code 0.

---

# 8. Verifica del codice presente nell'immagine

Dopo il build devi dimostrare che il container utilizza il codice modificato.

Usa evidenze appropriate, ad esempio:

- versione;
- commit;
- timestamp;
- classi;
- jar;
- checksum;
- stringhe caratteristiche;
- comportamento runtime.

L'evidenza deve essere riportata nel verification report.

---

# 9. Self-hosted licensing

Verificare nel processo reale:

```text
licensing.self-hosted-mode=true
```

e verificare che il backend:

1. riconosca self-hosted;
2. produca `LicensingState` valido;
3. non contatti Keygen per il percorso self-hosted;
4. conceda gli entitlement previsti;
5. risolva correttamente `PlanFeatures`;
6. non restituisca 403 per feature che MOD-001 dovrebbe sbloccare.

Verificare almeno alcuni entitlement rappresentativi, incluso quello relativo agli allegati.

NON modificare il codice licensing durante questa attività.

Se il comportamento runtime non corrisponde all'audit:

- non applicare immediatamente un fix;
- registrare il problema;
- identificare esattamente la causa;
- classificare il problema.

---

# 10. Verifica piano BUSINESS

L'audit 22 ha lasciato una domanda aperta:

> l'assegnazione del piano BUSINESS in self-hosted è effettivamente garantita nel codice runtime?

Questa verifica è obbligatoria.

Devi determinare:

1. dove viene assegnato il piano;
2. quale piano vede la company;
3. quali `PlanFeatures` risultano attivi;
4. se `FILE` è effettivamente disponibile;
5. se il controller reale supera il controllo.

Se il piano BUSINESS non viene effettivamente risolto:

```text
STOP
```

e produci un finding dettagliato invece di modificare il licensing.

---

# 11. Verifica allegati

Usare il processo applicativo reale.

Non limitarti a chiamare direttamente MinIO.

La catena da verificare è:

```text
Client
 ↓
nginx
 ↓
API
 ↓
FileController
 ↓
FileService
 ↓
StorageService
 ↓
MinIO
```

Verificare almeno:

### Upload

- autenticazione;
- autorizzazione;
- entitlement;
- PlanFeature;
- permesso `FILES`;
- upload reale;
- metadata DB;
- object MinIO.

### Download

Verificare:

- autorizzazione;
- presigned URL;
- status HTTP;
- `Content-Type`;
- `Content-Disposition`;
- `X-Content-Type-Options`;
- comportamento per file potenzialmente attivi, secondo il comportamento definito da MOD-004B.

### Delete

Verificare:

1. autorizzazione;
2. eliminazione metadata;
3. eliminazione object MinIO;
4. eventuale thumbnail;
5. comportamento in caso di errore storage.

Il finding VF-01 resta accettato salvo evidenza di regressione.

---

# 12. Verifica stored-XSS

Creare un test controllato usando un file innocuo che permetta di verificare il comportamento HTTP senza eseguire codice malevolo.

Lo scopo è verificare che il comportamento implementato da MOD-004B sia realmente presente nel backend buildato.

Verificare almeno:

```text
Content-Disposition
X-Content-Type-Options
Content-Type
```

Non introdurre payload pericolosi né trasformare il test in un'attività di exploit.

---

# 13. Verifica lifecycle MinIO

Per un allegato creato tramite API reale:

```text
upload
 ↓
DB record exists
 ↓
MinIO object exists
 ↓
delete API
 ↓
DB record removed
 ↓
MinIO object removed
```

Acquisire evidenze.

Testare anche il comportamento di errore se esiste già un test unitario per VF-01.

Non modificare VF-01 durante MOD-005.

---

# 14. Verifica tenant/company isolation

L'audit richiede una verifica di isolamento company.

Creare o utilizzare in ambiente isolato almeno due company/tenant, se il sistema lo permette senza modificare il codice.

Verificare che:

- un utente/company A non possa accedere all'allegato di B;
- download;
- delete;
- metadata;
- endpoint correlati.

Verificare che `CompanyAudit` e le autorizzazioni restino attive.

Se creare due company richiede una procedura non documentata, cercare prima nella documentazione e nel codice.

NON inventare una procedura.

---

# 15. Frontend

Il frontend non è il focus principale di MOD-005.

Non analizzarlo integralmente.

Verificare soltanto ciò che è necessario per dimostrare:

```text
browser/client
→ nginx
→ frontend
→ API
```

Se l'API funziona ma il frontend presenta problemi, registrarli separatamente.

Non iniziare modifiche frontend.

---

# 16. nginx

Verificare solo la catena necessaria:

- routing API;
- routing frontend;
- routing storage;
- header rilevanti;
- proxy verso backend;
- proxy verso MinIO.

Non modificare nginx salvo che sia impossibile completare la verifica senza una modifica.

Se emerge una configurazione errata, fermarsi prima di modificarla e documentare.

---

# 17. Test

Eseguire:

1. suite esistente;
2. test mirati già presenti;
3. eventuali smoke test necessari;
4. test runtime MOD-005.

NON modificare test esistenti per farli passare.

Se un test fallisce:

```text
TEST FAIL
→ identificare causa
→ classificare
→ verificare se regressione
→ non applicare fix automaticamente
```

I test nuovi sono consentiti solo se strettamente necessari per dimostrare MOD-005.

---

# 18. Criteri di successo

MOD-005 è PASS se possiamo dimostrare:

### Licensing

- self-hosted mode attiva;
- LicensingState valido;
- entitlement disponibili;
- nessun blocco Keygen;
- PlanFeatures corretti;
- FILE disponibile.

### Allegati

- upload reale PASS;
- download reale PASS;
- security headers PASS;
- Content-Disposition corretto;
- delete metadata PASS;
- delete object MinIO PASS.

### Security

- company isolation PASS;
- autorizzazione PASS;
- nessun bypass introdotto.

### Infrastructure

- backend realmente buildato dai sorgenti;
- container utilizza l'immagine locale;
- PostgreSQL funzionante;
- MinIO funzionante;
- nginx funzionante.

### Regression

- suite precedente non degradata;
- 1445/1445 deve rimanere PASS, salvo differenze esplicitamente documentate.

---

# 19. Stati possibili

Usa esclusivamente:

```text
PASS
PASS WITH FINDINGS
FAIL
BLOCKED
NOT VERIFIED
```

Non usare PASS se manca un'evidenza fondamentale.

---

# 20. Se trovi problemi

Non correggere automaticamente.

Classifica:

### A — Problema di build
Il codice modificato non viene incluso nell'immagine.

### B — Problema di deployment
Il compose utilizza ancora l'immagine upstream.

### C — Problema runtime
Il codice è presente ma il comportamento è errato.

### D — Problema di configurazione
Il comportamento richiede una configurazione non presente.

### E — Problema di codice
Il MOD precedente non funziona nel processo reale.

### F — Problema di test
Il comportamento funziona ma manca una verifica automatica.

### G — Problema documentale
Il comportamento reale non corrisponde alla documentazione.

Non applicare fix salvo che il problema sia chiaramente un errore della procedura di test e la correzione non modifichi il progetto.

---

# 21. Documentazione

Creare:

```text
docs/self-hosted-audit/23-mod005-runtime-integration-verification.md
```

Struttura obbligatoria:

```text
# MOD-005 — Runtime Integration Verification

## 1. Objective
## 2. Scope
## 3. Sources
## 4. Environment
## 5. Build Strategy
## 6. Image Provenance
## 7. Self-Hosted Licensing Verification
## 8. Plan / PlanFeatures Verification
## 9. Attachment Upload Verification
## 10. Attachment Download Verification
## 11. Attachment Delete Verification
## 12. MinIO Lifecycle Verification
## 13. Security / Tenant Isolation
## 14. nginx / Proxy Verification
## 15. Test Results
## 16. Findings
## 17. Deviations
## 18. Final Verdict
## 19. Evidence
```

Per ogni verifica usa:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

---

# 22. Aggiornamento documentazione esistente

NON modificare automaticamente:

- `CLAUDE.md`;
- audit precedenti;
- implementation docs;
- verification docs precedenti.

Il nuovo documento MOD-005 deve essere autosufficiente.

Alla fine, se MOD-005 è concluso, puoi indicare quali documenti dovrebbero essere aggiornati successivamente, ma non modificarli senza necessità.

---

# 23. Git

Consentito:

```text
git status
git diff
git log
git branch
git show
```

NON eseguire autonomamente:

```text
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza esplicita autorizzazione.

---

# 24. Secret handling

NON riportare nel report:

- password;
- JWT secret;
- SMTP password;
- MinIO secret;
- database password;
- OAuth secret;
- token.

Mascherare eventuali valori:

```text
********
```

Non committare secret.

---

# 25. Context management

Mantieni il contesto focalizzato su MOD-005.

Non:

- rileggere tutto il repository;
- analizzare MOD non correlati;
- aprire file enormi senza necessità;
- ripetere l'intero audit;
- cercare alternative architetturali già escluse.

Se una sessione diventa molto lunga, usa `/compact` quando appropriato.

Prima di ogni nuova esplorazione chiediti:

> Questo file è necessario per verificare MOD-005?

Se no, non leggerlo.

---

# 26. Anti-hallucination

MUST NOT inventare:

- API;
- endpoint;
- credenziali;
- variabili;
- Dockerfile;
- architetture;
- comportamenti;
- risultati;
- test;
- feature;
- decisioni.

Se un'informazione non è disponibile:

```text
UNKNOWN / DA VERIFICARE
```

Non trasformare un'ipotesi in un risultato.

---

# 27. Definition of Done

MOD-005 è completato quando:

- il backend è stato buildato dai sorgenti;
- è dimostrato che il container usa l'immagine buildata;
- stack isolato o procedura sicura documentata;
- self-hosted licensing verificato runtime;
- PlanFeatures verificati;
- piano BUSINESS verificato;
- upload allegato reale verificato;
- download verificato;
- header sicurezza verificati;
- delete verificato;
- lifecycle MinIO verificato;
- tenant/company isolation verificata;
- nginx verificato;
- test eseguiti;
- eventuali failure classificati;
- nessun secret esposto;
- `23-mod005-runtime-integration-verification.md` prodotto.

---

# 28. STOP CONDITION

Al termine:

**STOP.**

NON iniziare MOD-006.

NON implementare nuove feature.

NON modificare licensing.

NON modificare LDAP.

NON implementare filesystem storage.

NON correggere VF-01.

NON modificare l'architettura.

NON modificare production.

La decisione successiva verrà presa dal responsabile tecnico dopo la revisione di:

```text
docs/self-hosted-audit/23-mod005-runtime-integration-verification.md
```

Restituisci infine:

1. risultato MOD-005;
2. ambiente utilizzato;
3. immagine buildata;
4. test eseguiti;
5. evidenze principali;
6. finding;
7. eventuali blocchi;
8. percorso del documento prodotto;
9. eventuali raccomandazioni per il prossimo MOD.

