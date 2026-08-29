# MOD-018 — Comprehensive Project State Recap & Roadmap Audit

## 1. Scopo

Questo MOD non deve modificare il codice.

Il suo scopo è ricostruire lo **stato reale e attuale dell'intero progetto Atlas self-hosted**, utilizzando come fonte primaria tutta la documentazione persistente presente nella cartella:

```text
docs/
```

Il risultato deve permettere al responsabile del progetto di capire:

```text
COSA ABBIAMO FATTO
↓
COSA È STATO VERIFICATO
↓
COSA È ANCORA APERTO
↓
COSA MANCA
↓
QUAL È IL PERCORSO PIÙ CORRETTO PER ARRIVARE ALL'OBIETTIVO FINALE
```

Questo MOD nasce dalla necessità di ricostruire una visione completa dopo molte iterazioni e sessioni di lavoro.

Il documento prodotto diventerà il nuovo **checkpoint generale del progetto**.

---

# 2. Obiettivo finale del progetto

Ricostruire dalla documentazione qual è l'obiettivo complessivo che è stato perseguito nel progetto Atlas.

NON inventare l'obiettivo.

Deve essere ricostruito esclusivamente da:

- documentazione;
- audit;
- implementation docs;
- verification docs;
- architecture/design docs;
- decision documents;
- MOD precedenti.

Se l'obiettivo non è espresso chiaramente in un documento, segnalarlo come:

```text
NOT EXPLICITLY DOCUMENTED
```

e ricostruire solo ciò che è effettivamente supportato dalle evidenze.

---

# 3. Regola fondamentale: documentazione come fonte primaria

La fonte primaria di questo MOD è:

```text
docs/
```

Claude deve analizzare sistematicamente la documentazione del progetto.

NON deve basarsi principalmente sulla memoria della conversazione.

La conversazione può essere usata solo come contesto secondario quando necessario.

Se esiste una contraddizione:

```text
documentazione più recente
>
documentazione più vecchia
>
memoria della conversazione
>
inferenza
```

Non correggere silenziosamente le contraddizioni.

Documentarle.

---

# 4. Inventario iniziale della documentazione

Prima di analizzare i contenuti creare un inventario della cartella:

```text
docs/
```

Individuare almeno:

```text
MOD-xxx
audit
implementation
verification
architecture
design
decision
requirements
report
deployment
infrastructure
mobile
frontend
backend
licensing
i18n
```

Non limitarsi ai file che contengono `MOD`.

Devono essere inclusi anche documenti generali importanti.

---

# 5. Lettura della documentazione

Analizzare i documenti in ordine logico e temporale.

Per ogni documento rilevante determinare:

```text
File:
Purpose:
Date/version, if available:
Related MOD:
Status:
Key conclusions:
Current relevance:
Superseded by:
```

Non è necessario riportare tutto il contenuto dei documenti.

Estrarre solamente le informazioni necessarie per ricostruire lo stato del progetto.

---

# 6. Gerarchia delle informazioni

Quando più documenti parlano dello stesso argomento:

### Priorità 1
Documentazione di verifica più recente.

### Priorità 2
Documentazione di implementazione più recente.

### Priorità 3
Decisioni architetturali approvate.

### Priorità 4
Audit precedenti.

### Priorità 5
Documentazione storica.

Un documento vecchio non deve essere considerato ancora valido solo perché esiste.

---

# 7. Ricostruzione dei MOD

Creare una timeline sintetica dei MOD identificati.

Per ogni MOD:

```text
MOD:
Title:
Objective:
Status:
Implementation:
Verification:
Tests:
Documentation:
Known issues:
Dependencies:
Superseded?:
Current relevance:
```

Usare stati coerenti:

```text
PLANNED
IN PROGRESS
BLOCKED
PASS
PASS WITH FINDINGS
FAIL
SUPERSEDED
```

Non inventare lo stato di un MOD se non è documentato.

---

# 8. Stato tecnico generale

Ricostruire lo stato attuale di:

```text
Backend
Frontend Web
Mobile Android
Mobile iOS
Database
Authentication
Authorization
Licensing
i18n
API
Background Jobs
Multi-tenancy
Audit
Soft Delete
Docker
Infrastructure
Caddy
DNS
SSL
Testing
CI/CD
Build system
```

Per ogni area indicare:

```text
STATUS
WHAT IS WORKING
WHAT WAS MODIFIED
WHAT WAS VERIFIED
WHAT REMAINS
DOCUMENTATION
```

Se una tecnologia o funzionalità non è presente nel progetto, indicare:

```text
NOT PRESENT / NOT DOCUMENTED
```

Non inventare.

---

# 9. Backend

Ricostruire dalla documentazione:

- modifiche effettuate;
- funzionalità sbloccate;
- licensing;
- API;
- autenticazione;
- autorizzazione;
- database;
- migration;
- tenant isolation;
- soft delete;
- audit;
- eventuali background jobs;
- test.

Distinguere chiaramente:

```text
IMPLEMENTED
VERIFIED
NOT VERIFIED
OPEN
```

---

# 10. Frontend Web

Ricostruire cosa è già stato analizzato/modificato.

È particolarmente importante verificare se l'audit frontend relativo ai feature gate/licensing:

- è già stato eseguito;
- quali documenti lo attestano;
- quali gate sono stati trovati;
- quali sono stati modificati;
- quali funzionalità desktop sono state testate;
- quali restrizioni sono ancora presenti.

NON creare un nuovo audit frontend durante questo MOD.

Se l'audit esiste già, riportarlo.

Se non è possibile dimostrarlo dalla documentazione:

```text
FRONTEND AUDIT STATUS: NOT CONFIRMED
```

---

# 11. Mobile

Ricostruire separatamente:

```text
Android
iOS
```

Indicare:

- ambiente di test;
- AVD;
- ADB;
- UIAutomator;
- build;
- APK;
- Firebase;
- Custom Server;
- login;
- regression;
- bug;
- fix;
- stato attuale.

Distinguere ciò che è stato testato realmente da ciò che non è stato testato.

---

# 12. Mobile Agent Testing

Determinare lo stato attuale della capacità di far testare l'app a un coding agent.

Verificare:

```text
Android environment
AVD
ADB
UIAutomator
screenshots
logcat
APK build
APK installation
runtime interaction
```

Indicare chiaramente:

```text
AVAILABLE
PARTIAL
NOT AVAILABLE
```

e perché.

---

# 13. iOS

Determinare cosa è stato realmente verificato su iOS.

In particolare distinguere:

```text
manual human test
automated test
coding-agent test
build verification
runtime verification
```

Non assumere che il fatto che l'app si connetta da iOS significhi che l'intera app sia verificata.

---

# 14. i18n e polacco

Ricostruire lo stato esclusivamente della lingua polacca.

Indicare:

- audit effettuati;
- traduzioni corrette;
- key integrity;
- literal keys;
- fallback;
- test runtime;
- problemi residui.

Non creare un audit delle altre lingue.

La lingua inglese deve essere citata solo quando necessaria come base tecnica.

---

# 15. Licensing

Ricostruire:

```text
cosa era bloccato
↓
dove era bloccato
↓
cosa è stato modificato
↓
cosa è stato verificato
↓
cosa risulta ancora da verificare
```

Distinguere:

```text
LICENSE RESTRICTION
AUTHORIZATION
ROLE/PERMISSION
BUSINESS RULE
```

Non considerare automaticamente ogni controllo come licensing.

---

# 16. Infrastructure

Ricostruire lo stato documentato di:

```text
Docker
Atlas containers
PostgreSQL
MinIO
Nginx, if present
Caddy
DNS
SSL
LAN
public domain
reverse proxy
```

Indicare ciò che è:

```text
LOCAL
LAN
PUBLIC
MANUAL
AUTOMATED
```

senza introdurre nuove informazioni.

---

# 17. Stato live

Determinare dalla documentazione:

```text
LOCAL INSTANCE
LIVE INSTANCE
TEST INSTANCE
PRODUCTION INSTANCE
```

e quale versione del codice è presente dove.

Particolare attenzione a non confondere:

```text
codice locale
vs
server live
vs
APK locale
vs
app installata sul device
```

---

# 18. Git e repository

Ricostruire:

- repository/fork;
- branch/workflow documentato;
- commit policy;
- file sensibili;
- file gitignored;
- build artifacts;
- modifiche non committate note;
- eventuali regole di push/deployment.

Non eseguire operazioni Git distruttive.

---

# 19. Testing

Ricostruire tutti i livelli di test documentati:

```text
Static
Unit
Integration
E2E
Runtime
Manual
Mobile
Desktop
```

Per ciascuno:

```text
AVAILABLE
PARTIAL
NOT DOCUMENTED
```

Non assumere che un test esista solo perché sarebbe teoricamente utile.

---

# 20. Security

Ricostruire le misure di sicurezza già documentate:

- secret handling;
- Firebase;
- authentication;
- authorization;
- tenant isolation;
- Docker;
- HTTPS;
- certificates;
- DNS;
- credentials;
- Git.

Indicare solamente ciò che è documentato o verificabile.

---

# 21. Problemi aperti

Creare un registro sintetico:

| ID | Area | Problema | Severity | Stato | Evidenza | Prossima azione |
|---|---|---|---|---|---|---|

Non includere problemi già risolti come aperti.

Non inventare severity se non determinabile.

---

# 22. Decisioni architetturali

Ricostruire le decisioni già approvate.

Separarle in:

```text
APPROVED
TEMPORARY
OPEN
SUPERSEDED
```

Una decisione APPROVED non deve essere riproposta come alternativa senza una nuova motivazione.

---

# 23. Contraddizioni

Cercare esplicitamente contraddizioni tra documenti.

Per esempio:

```text
documento A: stato X
documento B più recente: stato Y
```

Per ogni contraddizione:

```text
Old:
New:
Which is authoritative:
Reason:
Action needed:
```

Se il documento più recente non è sufficiente per decidere:

```text
OPEN DECISION
```

---

# 24. Gap Analysis

Dopo aver ricostruito lo stato, confrontarlo con l'obiettivo finale.

Produrre:

```text
GOAL
↓
CURRENT STATE
↓
GAP
↓
REQUIRED ACTION
```

Classificare ogni gap:

```text
MUST HAVE
SHOULD HAVE
NICE TO HAVE
UNKNOWN
```

Non trasformare automaticamente un desiderio futuro in requisito.

---

# 25. Roadmap proposta

Sulla base esclusiva della documentazione, proporre una sequenza di prossimi MOD.

Per ogni candidato:

```text
MOD:
Purpose:
Why now:
Dependencies:
Risk:
Expected outcome:
```

La roadmap è una **raccomandazione**, non una decisione definitiva.

Non iniziare nessun MOD successivo.

---

# 26. Cosa NON fare

Questo MOD è un audit/documentation task.

NON:

- modificare codice applicativo;
- modificare frontend;
- modificare backend;
- modificare database;
- modificare Docker;
- modificare Caddy;
- modificare DNS;
- modificare SSL;
- modificare mobile;
- creare nuove feature;
- correggere bug;
- fare deployment;
- fare commit;
- fare push.

L'unico file di progetto che può essere aggiornato è `CLAUDE.md`, se necessario per riflettere il checkpoint generale.

---

# 27. Documentazione da produrre

Produrre:

```text
docs/self-hosted-audit/38-mod018-project-state-recap.md
```

Struttura obbligatoria:

```text
# MOD-018 — Comprehensive Project State Recap

## 1. Executive Summary

## 2. Final Project Objective

## 3. Documentation Inventory

## 4. MOD Timeline

## 5. Current Architecture

## 6. Backend State

## 7. Frontend Web State

## 8. Mobile Android State

## 9. Mobile iOS State

## 10. Polish i18n State

## 11. Licensing State

## 12. Infrastructure State

## 13. Git & Repository State

## 14. Testing State

## 15. Security State

## 16. Completed Work

## 17. Open Issues

## 18. Approved Decisions

## 19. Temporary Decisions

## 20. Open Decisions

## 21. Contradictions Found

## 22. Goal vs Current State

## 23. Remaining Gaps

## 24. Recommended Roadmap

## 25. Documentation Map

## 26. CLAUDE.md Update

## 27. Final Verdict
```

---

# 28. Executive Summary

La prima sezione deve essere leggibile in pochi minuti.

Deve rispondere:

```text
Dove siamo?
Cosa funziona?
Cosa abbiamo già sbloccato?
Cosa è stato verificato?
Quali sono i problemi principali?
Quanto manca all'obiettivo?
Qual è il prossimo passo consigliato?
```

Non trasformarla in un elenco infinito.

---

# 29. Documentation Map

Creare una mappa sintetica:

```text
Area
→ documento principale
→ documenti di supporto
```

Esempio:

```text
Mobile
→ MOD-014A
→ MOD-014B
→ MOD-015

Polish i18n
→ MOD-016
→ MOD-017

Licensing
→ [documento reale individuato dall'audit]
```

Usare i nomi reali dei file presenti.

NON inventare percorsi.

---

# 30. CLAUDE.md

Aggiornare `CLAUDE.md` con il checkpoint generale solo se necessario.

Aggiornare:

- Current Project State;
- Current Focus;
- Completed MODs;
- Open Issues;
- Documentation Map;
- Next recommended step.

Non copiare l'intero report nel `CLAUDE.md`.

Il report MOD-018 deve essere la fonte dettagliata del recap.

---

# 31. Anti-Hallucination

Questo MOD è particolarmente importante per evitare la perdita di contesto.

Claude MUST NOT inventare:

- stato di un MOD;
- implementazioni;
- test;
- decisioni;
- architettura;
- requisiti;
- feature;
- bug;
- deployment;
- stato live.

Se non documentato:

```text
NOT DOCUMENTED
```

Se contraddittorio:

```text
CONFLICT
```

Se non verificabile:

```text
NOT VERIFIED
```

Non convertire:

```text
"probabilmente"
```

in:

```text
"fatto"
```

---

# 32. Definition of Done

MOD-018 è completo quando:

- tutta la documentazione pertinente in `docs/` è stata inventariata;
- i documenti rilevanti sono stati analizzati;
- i MOD sono stati ricostruiti;
- lo stato backend/frontend/mobile è documentato;
- licensing è documentato;
- i18n polacco è documentato;
- infrastructure è documentata;
- test e verification sono documentati;
- decisioni approvate/open sono separate;
- contraddizioni sono evidenziate;
- problemi aperti sono sintetizzati;
- gap rispetto all'obiettivo sono identificati;
- una roadmap raccomandata è stata prodotta;
- Documentation Map aggiornata;
- `CLAUDE.md` aggiornato se necessario;
- nessun codice applicativo modificato.

---

# 33. Final Output

Alla fine fornire:

```text
CLAUDE.md updated: YES/NO

Documentation files analyzed: X
MODs identified: X
MODs completed: X
MODs open: X
MODs blocked: X
MODs superseded: X

Backend:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Frontend:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Android:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

iOS:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Polish i18n:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Licensing:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Infrastructure:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Testing:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Security:
PASS / PARTIAL / BLOCKED / NOT VERIFIED

Open issues:
X

Critical gaps:
X

Contradictions:
X

Approved decisions:
X

Open decisions:
X

Recommended next MOD:
MOD-XXX / USER DECISION

Final project status:
X

Final verdict:
PASS / PASS WITH FINDINGS / BLOCKED
```

---

# 34. Regola finale

Questo documento deve diventare un **checkpoint del progetto**, non una nuova analisi tecnica infinita.

Il suo scopo è permettere a un nuovo agente/sessione di comprendere rapidamente:

```text
da dove siamo partiti
↓
cosa è stato fatto
↓
cosa è stato realmente verificato
↓
cosa è rimasto aperto
↓
cosa dobbiamo fare dopo
```

Al termine:

**NON implementare nulla.**

**NON iniziare il MOD successivo.**

**STOP dopo aver prodotto il report e aggiornato `CLAUDE.md`.**
