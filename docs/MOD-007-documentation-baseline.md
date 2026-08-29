# MOD-007 — Consolidamento documentazione e baseline del progetto

## 1. Contesto

MOD-006 è concluso con verdict **PASS**.

Risultati consolidati:

- il deployment self-hosted ufficiale costruisce il backend dai sorgenti tramite `build: ./api`;
- l'immagine backend è `atlas-cmms-backend:local`;
- il riferimento all'immagine upstream `intelloop/atlas-cmms-backend` è stato rimosso dal deployment ufficiale;
- `MAIL_RECIPIENTS` ora ha un default vuoto;
- CFG-01 è **RESOLVED**;
- CFG-02 è **OPEN / accettato**, come hardening opzionale;
- licensing self-hosted verificato a runtime;
- Plan BUSINESS / `PlanFeatures.FILE` verificati;
- allegati upload/download/delete verificati;
- tenant isolation verificato;
- security headers verificati;
- regression suite: **1445/1445 PASS**;
- nessun ambiente production modificato durante MOD-006.

Il report MOD-006 dichiara esplicitamente che `CLAUDE.md` non è stato aggiornato durante quella fase e indica questo come lavoro documentale successivo.

Questo MOD serve quindi a riallineare la memoria persistente del repository allo stato reale del progetto.

---

# 2. Obiettivo

Aggiornare la documentazione persistente affinché una nuova sessione di Claude Code possa riprendere il progetto senza ricostruire la storia dalle conversazioni.

La priorità è:

```text
CLAUDE.md
    ↓
audit / architecture / decisions
    ↓
MOD documentation
    ↓
codice
```

Dopo MOD-007 `CLAUDE.md` deve rappresentare lo stato reale e verificato del repository.

---

# 3. Fonti obbligatorie

Prima di modificare documentazione leggere:

1. `CLAUDE.md` attuale;
2. `22-audit-consolidation.md`;
3. `23-mod005-runtime-integration-verification.md`;
4. `24-mod006-deployment-alignment.md`;
5. documentazione dei MOD precedenti già indicata nella `Documentation Map`;
6. `docker-compose.yml`;
7. `api/Dockerfile`;
8. `api/src/main/resources/application.yml`;
9. `nginx.conf`.

Non rileggere l'intero repository.

Usare i documenti MOD come fonte primaria per lo stato verificato.

---

# 4. Regola fondamentale

`CLAUDE.md` deve essere mantenuto aggiornato **alla fine di ogni MOD**.

Questa regola diventa parte del workflow permanente.

Ogni MOD futuro deve:

1. leggere `CLAUDE.md` all'inizio;
2. rispettarne le decisioni;
3. aggiornarlo alla fine quando lo stato del progetto cambia;
4. verificare che il contenuto aggiornato non contraddica i documenti più recenti;
5. lasciare il repository riprendibile in una nuova sessione.

Non rimandare automaticamente l'aggiornamento di `CLAUDE.md` a un MOD successivo.

---

# 5. Scope

## IN SCOPE

- aggiornamento `CLAUDE.md`;
- aggiornamento della mappa documentale;
- aggiornamento sintetico dello stato dei MOD;
- aggiornamento delle decisioni architetturali;
- aggiornamento dello stato deployment;
- registrazione dei finding aperti;
- eventuale aggiornamento minimale dell'audit consolidation per eliminare open question ormai risolte;
- verifica coerenza tra documentazione e codice/configurazione attuale.

## OUT OF SCOPE

NON modificare:

- codice Java;
- frontend;
- Docker Compose;
- Dockerfile;
- nginx;
- database;
- licensing;
- authorization;
- storage;
- test.

Questo MOD è principalmente documentale.

---

# 6. Aggiornamento CLAUDE.md

Aggiornare il file secondo la struttura già definita nel progetto.

Deve contenere almeno:

```text
Project Overview
Core Architecture
Technology Stack
Repository Structure
Architectural Decisions
Non-Negotiable Rules
Coding Conventions
Security Rules
Database Rules
API Rules
Authentication and Authorization
Testing and Verification
MOD Workflow
Documentation Workflow
Git Workflow
Claude Code Operating Rules
Context Management
Current Project State
Known Issues
Open Decisions
Documentation Map
```

Non trasformarlo in una copia degli audit.

---

# 7. Decisioni approvate da registrare

Verificare e registrare come decisioni approvate almeno:

### Self-hosted deployment

Il backend ufficiale deve essere costruito dai sorgenti:

```yaml
build:
  context: ./api
image: atlas-cmms-backend:local
```

Il deployment non deve tornare accidentalmente a:

```text
intelloop/atlas-cmms-backend
```

### Self-hosted licensing

La modalità self-hosted implementata nei MOD precedenti è una decisione approvata e verificata a runtime.

Non riproporre automaticamente l'integrazione Keygen come soluzione alternativa.

### Attachment security

Le modifiche già verificate in MOD-004B/004C sono considerate parte dello stato approvato.

### Tenant isolation

`CompanyAudit.@PostLoad` e i controlli già verificati non devono essere sostituiti autonomamente.

### MAIL_RECIPIENTS

La configurazione:

```yaml
MAIL_RECIPIENTS:
```

con default vuoto è la configurazione corrente approvata.

---

# 8. Decisioni aperte

Registrare chiaramente:

## CFG-02

Stato:

```text
OPEN / OPTIONAL HARDENING
```

Il problema nginx/frontend non si manifesta nel deployment ufficiale verificato.

Non deve essere trattato come bug bloccante.

Un eventuale hardening tramite resolver dinamico richiede una decisione esplicita.

## Frontend build-from-source

Stato:

```text
OPEN / OPTIONAL
```

Il frontend attualmente utilizza l'immagine upstream.

Nessun MOD precedente ha modificato il frontend.

Non trasformare questa possibilità in requisito senza decisione esplicita.

---

# 9. Current Project State

Creare una tabella sintetica.

Per ogni MOD conosciuto riportare:

```text
MOD
stato
documento principale
nota sintetica
```

Non duplicare i dettagli dei report.

In particolare MOD-005:

```text
PASS WITH FINDINGS
runtime licensing / plan / attachments / tenant isolation verificati
```

MOD-006:

```text
PASS
deployment backend allineato ai sorgenti
CFG-01 resolved
CFG-02 open optional
```

---

# 10. Documentation Map

La mappa deve permettere a Claude Code di trovare rapidamente:

- audit consolidato;
- architecture/design;
- implementation docs;
- verification docs;
- MOD-005;
- MOD-006;
- documenti precedenti dei MOD;
- stato corrente.

Per ogni documento indicare una sola riga:

```text
path → scopo
```

Non descrivere dettagli tecnici già presenti nel documento.

---

# 11. MOD Workflow permanente

Formalizzare il seguente workflow:

### Fase 1 — Orientamento

Leggere:

```text
CLAUDE.md
↓
documentazione del MOD
↓
decisioni pertinenti
```

### Fase 2 — Scope

Definire:

```text
IN SCOPE
OUT OF SCOPE
```

### Fase 3 — Analisi

Ispezionare solo il codice necessario.

### Fase 4 — Implementazione

Modificare esclusivamente ciò che è necessario allo scope.

### Fase 5 — Test

Eseguire test mirati e regression test appropriati.

### Fase 6 — Verification

Produrre evidence reale.

### Fase 7 — Documentation

Aggiornare:

```text
MOD report
CLAUDE.md
eventuale audit consolidato
```

### Fase 8 — Stop

Non iniziare automaticamente il MOD successivo.

---

# 12. Regola di aggiornamento documentale

Aggiungere esplicitamente:

> Ogni MOD che modifica lo stato del progetto deve aggiornare `CLAUDE.md` prima di essere considerato completato.

Se il MOD non modifica lo stato globale, Claude può limitarsi a verificare che `CLAUDE.md` resti coerente.

Il report del MOD deve dichiarare:

```text
CLAUDE.md updated: YES/NO
Reason:
```

Un `NO` deve essere motivato.

---

# 13. Anti-hallucination

Confermare nel `CLAUDE.md`:

Claude MUST NOT inventare:

- requisiti;
- API;
- modelli;
- configurazioni;
- decisioni;
- dipendenze;
- comportamento runtime.

Gerarchia delle fonti:

```text
decisione più recente documentata
        ↓
MOD verification
        ↓
audit
        ↓
codice
        ↓
inferenza
```

Quando esiste una contraddizione, prevale la decisione/documentazione più recente e deve essere segnalata.

---

# 14. Context Management

Aggiornare le regole già presenti affinché Claude:

- non scansioni tutto il repository;
- legga prima `CLAUDE.md`;
- legga solo la documentazione pertinente;
- non rilegga automaticamente tutti i MOD;
- non riapra decisioni già approvate;
- non analizzi moduli non coinvolti;
- mantenga il contesto focalizzato;
- aggiorni la memoria persistente prima della fine della sessione;
- usi `/compact` quando la sessione diventa eccessivamente lunga;
- preferisca una nuova sessione per attività completamente indipendenti.

---

# 15. Verifica di coerenza

Dopo l'aggiornamento:

1. confrontare `CLAUDE.md` con MOD-005;
2. confrontare `CLAUDE.md` con MOD-006;
3. confrontare le decisioni con `docker-compose.yml`;
4. confrontare lo stato deployment con il compose reale;
5. verificare che non rimanga scritto che il backend usa l'immagine upstream;
6. verificare che CFG-01 non risulti ancora aperto;
7. verificare che CFG-02 risulti OPEN/OPTIONAL;
8. verificare che MOD-005 non venga classificato come FAIL;
9. verificare che MOD-006 risulti PASS.

---

# 16. Audit consolidation

Valutare se `22-audit-consolidation.md` contiene ancora domande aperte già risolte da MOD-005/006.

Se sì, aggiornare **solo** le informazioni diventate obsolete.

Non riscrivere l'intero audit.

In particolare verificare la precedente open question relativa a:

```text
BUSINESS plan
PlanFeatures.FILE
runtime entitlement
```

che MOD-005 ha risolto.

---

# 17. Test

Non sono richiesti nuovi test applicativi.

È sufficiente:

```text
document consistency check
git diff
git status
```

Non modificare il codice.

Se per errore emergesse una regressione del codice durante la verifica, non correggerla in questo MOD: documentarla e fermarsi.

---

# 18. Git

Consentito:

```bash
git status
git diff
git diff --check
git log
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

# 19. Secret handling

Non riportare mai nei documenti:

- password;
- JWT secret;
- DB credentials;
- MinIO credentials;
- SMTP credentials;
- OAuth secrets;
- API tokens.

Usare:

```text
********
```

---

# 20. Definition of Done

MOD-007 è completato quando:

- `CLAUDE.md` è aggiornato;
- il file riflette lo stato reale post-MOD-006;
- MOD-005 e MOD-006 sono presenti nella Current Project State;
- Documentation Map è aggiornata;
- decisioni approvate e open decisions sono separate;
- CFG-01 è indicato come RESOLVED;
- CFG-02 è indicato come OPEN/OPTIONAL;
- deployment backend-from-source è indicato come decisione approvata;
- il workflow MOD impone l'aggiornamento di `CLAUDE.md`;
- eventuali open questions obsolete dell'audit 22 sono aggiornate;
- `git diff --check` passa;
- nessun codice applicativo è stato modificato;
- viene prodotto un report:

```text
docs/self-hosted-audit/25-mod007-documentation-baseline.md
```

---

# 21. Report finale

Il report deve contenere:

```text
# MOD-007 — Documentation Baseline

## 1. Objective
## 2. Documentation Reviewed
## 3. CLAUDE.md Changes
## 4. Current Project State
## 5. Approved Decisions
## 6. Open Decisions
## 7. Documentation Map
## 8. Audit Updates
## 9. Consistency Verification
## 10. Git Diff Summary
## 11. Findings
## 12. Final Verdict
```

Ogni verifica deve utilizzare:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

---

# 22. STOP CONDITION

Al termine:

**STOP.**

Non iniziare MOD-008.

Non implementare CFG-02.

Non iniziare il frontend build-from-source.

Non modificare codice applicativo.

Non introdurre nuove funzionalità.

La decisione sul prossimo MOD verrà presa dopo la revisione del report e del nuovo `CLAUDE.md`.

Il report finale deve indicare chiaramente:

```text
CLAUDE.md updated: YES
Audit updated: YES/NO
Code changes: NONE
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
