# MOD-011 — Fix F-01 Asset PATCH + Regression Verification

## 1. Contesto

MOD-010 — Local Acceptance Test è concluso con:

```text
Production readiness: READY WITH FINDINGS
Final verdict: PASS WITH FINDINGS
Code changes: NONE
P0: 0
P1: 0
P2: 0
P3: 1
```

Il test end-to-end locale ha confermato che il core Atlas self-hosted funziona:

- backend buildato dai sorgenti;
- PostgreSQL;
- MinIO;
- licensing self-hosted;
- authentication;
- authorization;
- company isolation;
- assets;
- work orders;
- attachments;
- persistence;
- backup;
- frontend/API;
- restart/recovery.

È stato individuato un solo bug applicativo P3:

### F-01

`AssetService.patch` genera HTTP 500 quando una PATCH parziale non contiene `status`.

Evidenza del MOD-010:

```text
PATCH {name} → 500
PATCH {name,status} → 200
```

La causa indicata dal report è:

```text
AssetService.java:419
getStatus().isReallyDown()
```

con `status == null`.

Il bug è pre-esistente ai test MOD-010 e non è stato introdotto dalle modifiche licensing/security.

Il workaround attuale consiste nell'inviare `status` anche nelle PATCH.

---

# 2. Obiettivo

Correggere esclusivamente **F-01** in modo minimale e sicuro.

Il fix deve:

1. impedire il `NullPointerException`;
2. mantenere invariato il comportamento quando `status` è presente;
3. permettere PATCH parziali legittime senza `status`;
4. non modificare il contratto API;
5. non modificare licensing;
6. non modificare authorization;
7. non modificare multi-tenancy;
8. non modificare frontend;
9. non modificare mobile;
10. non modificare database/schema;
11. non introdurre nuove dipendenze.

Dopo il fix deve essere aggiunto un test di regressione appropriato.

---

# 3. Fonti da leggere

Prima di modificare:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/28-mod010-local-acceptance-test.md`;
3. il codice di `AssetService.patch`;
4. i DTO/model utilizzati dalla PATCH Asset;
5. eventuali test già esistenti per AssetService;
6. eventuali documenti relativi alla gestione degli Asset.

Non rileggere l'intero repository.

---

# 4. Analisi preliminare

Prima di modificare il codice determinare esattamente:

- perché `status` può essere null;
- quale comportamento è previsto quando `status` non viene modificato;
- se esistono altri accessi allo stesso campo che possono produrre lo stesso problema;
- se il comportamento attuale distingue correttamente:
  - `status` presente;
  - `status` assente;
  - `status` null esplicito.

Non inventare il comportamento desiderato.

Usare il codice e i test esistenti per determinare la semantica.

---

# 5. Implementazione

Applicare il **minimo fix possibile**.

Preferire un approccio locale al punto in cui avviene il null dereference.

NON riscrivere `AssetService`.

NON rifattorizzare l'intero patch system.

NON modificare DTO/API contract se non strettamente necessario.

NON introdurre nuove astrazioni.

NON modificare classi non coinvolte.

---

# 6. Regression Tests

Aggiungere test che coprano almeno:

### Test A — PATCH senza status

Input:

```json
{
  "name": "Updated Asset"
}
```

Expected:

```text
HTTP 200
```

e:

```text
name aggiornato
status invariato
```

### Test B — PATCH con status

Verificare che il comportamento già funzionante continui a funzionare.

Expected:

```text
HTTP 200
status aggiornato correttamente
```

### Test C — altre proprietà

Se esistono PATCH parziali per altre proprietà, verificare che non vengano introdotte regressioni.

### Test D — authorization

Se sono già presenti test di authorization sugli Asset, assicurarsi che continuino a passare.

Non creare un nuovo sistema di test.

---

# 7. Verification

Eseguire almeno:

```bash
./mvnw test
```

o il comando ufficiale del repository.

Prima verificare nel repository quale comando è previsto.

Poi eseguire:

- test unitari relativi ad Asset;
- test integration relativi ad Asset, se presenti;
- suite backend completa, se il tempo/ambiente lo permette.

Il fix deve passare anche i test esistenti.

---

# 8. Runtime Verification

Dopo i test automatici, se l'ambiente locale è facilmente riproducibile:

1. buildare il backend modificato;
2. avviare lo stack locale;
3. creare/identificare un Asset;
4. eseguire PATCH senza `status`;
5. verificare HTTP 200;
6. verificare che `status` sia invariato;
7. eseguire PATCH con `status`;
8. verificare HTTP 200;
9. verificare che `status` sia aggiornato.

Non utilizzare production.

---

# 9. Regression Check sulle aree critiche

Verificare che il fix non abbia alterato:

- licensing;
- authentication;
- authorization;
- company isolation;
- Asset CRUD;
- API response;
- database persistence.

Non è necessario ripetere l'intero MOD-010 se non è tecnicamente necessario.

Concentrarsi sulle aree direttamente coinvolte.

---

# 10. Security

Il fix NON deve introdurre bypass.

In particolare:

```text
User A
  ↓
Asset A
  ↓
PATCH
  ↓
allowed

User B
  ↓
Asset A
  ↓
PATCH
  ↓
forbidden
```

Se esistono test di isolamento già presenti, devono continuare a passare.

---

# 11. Scope Restrictions

NON modificare:

- licensing;
- PlanFeatures;
- Keygen;
- frontend;
- mobile;
- Docker architecture;
- nginx;
- Caddy;
- database schema;
- migrations;
- API contract;
- authentication architecture;
- authorization architecture;
- tenant/company model.

Se durante l'analisi emerge la necessità di una modifica fuori scope:

```text
STOP
documentare
non implementare
```

---

# 12. Documentation

Produrre:

```text
docs/self-hosted-audit/29-mod011-f01-fix-verification.md
```

Struttura:

```text
# MOD-011 — F-01 Fix & Verification

## 1. Objective
## 2. Finding F-01
## 3. Root Cause
## 4. Implementation
## 5. Tests Added
## 6. Test Results
## 7. Runtime Verification
## 8. Regression Verification
## 9. Security Verification
## 10. Files Changed
## 11. Remaining Issues
## 12. CLAUDE.md Update
## 13. Final Verdict
```

Il report deve indicare chiaramente:

```text
Before:
PATCH without status → 500

After:
PATCH without status → 200
status unchanged
```

---

# 13. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

- Current Project State;
- Known Issues;
- MOD status;
- Documentation Map;
- eventuale stato di F-01.

Dopo un PASS, F-01 deve essere rimosso dalla lista dei problemi aperti o marcato come:

```text
RESOLVED
```

Non lasciare informazioni obsolete.

---

# 14. Git

Consentito:

```bash
git status
git diff
git diff --check
git log
```

Prima e dopo le modifiche verificare il diff.

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

# 15. Definition of Done

MOD-011 è completato quando:

- root cause F-01 identificata;
- fix minimale implementato;
- PATCH senza status funziona;
- status rimane invariato quando non viene fornito;
- PATCH con status continua a funzionare;
- test di regressione aggiunti;
- test esistenti passano;
- runtime locale verificato, se possibile;
- nessun comportamento di authorization alterato;
- nessun altro componente modificato inutilmente;
- report prodotto;
- `CLAUDE.md` aggiornato;
- F-01 marcato RESOLVED.

---

# 16. Final Output

Output obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: LIST
F-01 status: RESOLVED / NOT RESOLVED
Tests added: X
Tests passed: X
Tests failed: X
Runtime verification: PASS / NOT PERFORMED
Security regression: PASS / FAIL
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```

---

# 17. STOP CONDITION

Al termine:

**STOP.**

Non fare deployment live.

Non modificare `websrv01`.

Non modificare Caddy/DNS/certificati.

Non iniziare MOD-012.

La decisione sul prossimo passo verrà presa dopo la revisione del report MOD-011.
