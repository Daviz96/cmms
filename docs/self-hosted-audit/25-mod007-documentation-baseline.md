# MOD-007 — Documentation Baseline

MOD documentale: riallineare la memoria persistente del repository (`CLAUDE.md`) allo
stato reale e verificato post-MOD-006, così che una nuova sessione possa riprendere il
progetto senza ricostruire la storia dalle conversazioni. **Nessuna modifica al codice,
Docker, nginx, DB, licensing, test.**

Secret: nessuno riportato.

---

## 1. Objective

Rendere `CLAUDE.md` la baseline autorevole dello stato del progetto dopo MOD-006:
Current Project State sintetico, decisioni approvate e aperte separate e aggiornate,
workflow che impone l'aggiornamento di `CLAUDE.md` alla fine di ogni MOD, mappa
documentale aggiornata, e chiusura delle open question dell'audit 22 risolte da
MOD-005/006.

---

## 2. Documentation Reviewed

`CLAUDE.md`; [22-audit-consolidation.md](22-audit-consolidation.md);
[23-mod005-runtime-integration-verification.md](23-mod005-runtime-integration-verification.md);
[24-mod006-deployment-alignment.md](24-mod006-deployment-alignment.md); più
`docker-compose.yml`, `api/Dockerfile`, `application.yml`, `nginx.conf` (per la verifica
di coerenza). I documenti dei MOD sono usati come fonte primaria dello stato verificato.

---

## 3. CLAUDE.md Changes

`CLAUDE.md updated: YES.` Modifiche applicate:

- **Current focus** → MOD-006 (PASS) + riepilogo del lavoro completato a monte
  (MOD-004B/004C, MOD-005, MOD-001/002/003A).
- **Current Project State** → aggiunta una **tabella sintetica** (MOD | status |
  documento | nota) per MOD-001→007, più le sottosezioni **MOD-005** e **MOD-006**.
- **Architectural Decisions / Approved** → aggiunte le decisioni 17–21: backend
  build-from-source (no upstream), self-hosted licensing runtime-verificato,
  attachment security MOD-004B/004C, tenant isolation `@PostLoad`, default vuoto di
  `MAIL_RECIPIENTS`.
- **Open decisions** → aggiunte **CFG-02** (OPEN/OPTIONAL HARDENING) e **Frontend
  build-from-source** (OPEN/OPTIONAL).
- **MOD Workflow / Documentation** → regola permanente: ogni MOD che cambia lo stato
  DEVE aggiornare `CLAUDE.md` prima di essere completato (precede eventuali clausole di
  singoli prompt che lo vietano); il report deve dichiarare `CLAUDE.md updated: YES/NO`
  + `Reason`.
- **Documentation Workflow list** + **Documentation Map** → aggiunti `23-`, `24-`,
  `25-`.
- Rititolata la sezione storica "Current MOD — MOD-003A" → "Historical MOD detail".

(Le modifiche a `CLAUDE.md` non compaiono in `git diff`: l'intera cartella `docs/` è
**untracked** — verifica leggendo il file, non il diff.)

---

## 4. Current Project State

Tabella registrata in `CLAUDE.md` (sintesi):

| MOD | Status | Doc |
|---|---|---|
| MOD-001 | PASS | 13/14 |
| MOD-002 | PASS | 15 |
| MOD-003/003A | PASS | 16/17 |
| MOD-004 (audit) | PASS | 18 |
| MOD-004B | PASS WITH FINDINGS | 19/20 |
| MOD-004C | PASS | 21 |
| Audit consolidation | — | 22 |
| MOD-005 | PASS WITH FINDINGS | 23 |
| MOD-006 | PASS | 24 |
| MOD-007 | PASS (documental) | 25 (questo) |

Baseline test **1445/1445**; backend buildato dai sorgenti; CFG-01 RESOLVED; CFG-02
OPEN/OPTIONAL; nessun ambiente production modificato.

---

## 5. Approved Decisions

Registrate in `CLAUDE.md` (Architectural Decisions):

- **Backend build-from-source** (MOD-006): `build: ./api` + `atlas-cmms-backend:local`;
  il deployment non deve tornare a `intelloop/atlas-cmms-backend`.
- **Self-hosted licensing** (MOD-001/005): entitlement risolti localmente, nessun
  Keygen; non reintrodurre l'integrazione Keygen come alternativa.
- **Attachment security** (MOD-004B/004C): disposition per-tipo + `nosniff`/
  `X-Frame-Options` + delete lifecycle — parte dello stato approvato.
- **Tenant isolation** `CompanyAudit.@PostLoad` (MOD-005): non sostituire/aggirare.
- **`MAIL_RECIPIENTS`** con default vuoto (MOD-006): configurazione approvata.

---

## 6. Open Decisions

- **CFG-02** — frontend/nginx startup coupling: **OPEN / OPTIONAL HARDENING**. Non si
  manifesta nel deployment ufficiale (frontend env con default → frontend up → nginx
  up); non è un bug bloccante. Un hardening con `resolver` dinamico richiede decisione
  esplicita.
- **Frontend build-from-source**: **OPEN / OPTIONAL**. Il frontend usa l'immagine
  upstream; nessun MOD ne ha modificato i sorgenti. Non è un requisito senza decisione.
- (Restano le decisioni aperte LDAP preesistenti: `memberOf`→ruoli, StartTLS,
  truststore, multi-company, ecc.)

---

## 7. Documentation Map

La mappa in `CLAUDE.md` è aggiornata (path → scopo, una riga per documento), includendo
audit consolidato (22), MOD-005 (23), MOD-006 (24), MOD-007 (25) e i documenti
precedenti (04, 11–21). Serve come indice di ripresa per una nuova sessione.

---

## 8. Audit Updates

`Audit updated: YES` (solo informazioni obsolete, senza riscrivere l'audit).
In `22-audit-consolidation.md`:

- **Open Question 1** (assegnazione piano BUSINESS) → **RISOLTA (MOD-005)**:
  `UserService.signup` assegna BUSINESS; company con 17 `PlanFeatures` incl. `FILE`
  (DB + upload a runtime).
- **Open Question 2** (strategia di deploy) → **RISOLTA (MOD-006)**: compose builda dai
  sorgenti.
- Aggiornate le righe corrispondenti nella tabella §7 (da `F — UNKNOWN` a RISOLTO) e nei
  Prioritized Gaps (P1/P2 marcati DONE).

---

## 9. Consistency Verification

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| CLAUDE.md riflette MOD-005 | presente, PASS WITH FINDINGS | riga tabella + sottosezione | PASS |
| CLAUDE.md riflette MOD-006 | presente, PASS | riga tabella + sottosezione | PASS |
| Decisione vs `docker-compose.yml` | `build: ./api` | compose ha `context: ./api` + `atlas-cmms-backend:local` | PASS |
| Nessun claim "backend usa upstream" | assente | solo la decisione "non tornare a upstream" + nota storica | PASS |
| CFG-01 | RESOLVED | indicato RESOLVED | PASS |
| CFG-02 | OPEN/OPTIONAL | indicato OPEN/OPTIONAL | PASS |
| MOD-005 non FAIL | PASS WITH FINDINGS | così registrato | PASS |
| MOD-006 | PASS | così registrato | PASS |

EVIDENCE: grep di coerenza su `CLAUDE.md` e `docker-compose.yml`; `git diff --check`
pulito.

---

## 10. Git Diff Summary

- **Tracked** (invariati in MOD-007): nessuna modifica a codice/compose/config. Le
  modifiche tracciate presenti nel repo restano quelle dei MOD precedenti
  (MOD-001/003A/004B/006). `git diff --check` → **clean**.
- **Untracked** (`docs/`): aggiornati `CLAUDE.md`, `22-audit-consolidation.md`; nuovo
  `25-mod007-documentation-baseline.md`.

`Code changes: NONE.`

---

## 11. Findings

Nessun nuovo finding. Nessuna regressione di codice rilevata durante la verifica di
coerenza. Le decisioni aperte (CFG-02, frontend build-from-source) sono registrate,
non risolte (fuori scope MOD-007).

---

## 12. Final Verdict

```text
CLAUDE.md updated: YES
Audit updated: YES (22 — open questions 1/2 risolte; tabella/gaps aggiornati)
Code changes: NONE
Final verdict: PASS
```

`CLAUDE.md` rappresenta ora lo stato reale e verificato del repository post-MOD-006, con
decisioni approvate e aperte separate, Current Project State sintetico, workflow che
impone l'aggiornamento di `CLAUDE.md` ad ogni MOD, e mappa documentale completa.

⏹️ **STOP** — non avvio MOD-008, non implemento CFG-02, non avvio il frontend
build-from-source, non modifico codice applicativo. La decisione successiva spetta al
responsabile tecnico dopo la revisione di questo report e del nuovo `CLAUDE.md`.
