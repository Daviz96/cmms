# MOD-008 — Frontend Licensing & Feature-Gate Audit

## 1. Contesto

MOD-007 è concluso con verdict **PASS**.

La documentazione persistente è stata riallineata e `CLAUDE.md` stabilisce ora che:

- il backend deve essere buildato dai sorgenti;
- il licensing self-hosted è gestito localmente;
- le feature backend già sbloccate sono state verificate a runtime;
- `CFG-01` è RESOLVED;
- `CFG-02` è OPEN / OPTIONAL;
- il frontend build-from-source è una decisione OPEN / OPTIONAL;
- ogni MOD che cambia lo stato del progetto deve aggiornare `CLAUDE.md`.

Il prossimo obiettivo non è modificare il frontend, ma capire se il frontend ufficiale contiene proprie restrizioni commerciali/licensing che potrebbero impedire all'utente di utilizzare funzionalità già disponibili nel backend modificato.

## 2. Obiettivo

Eseguire un audit completo ma mirato del **frontend web** del fork Atlas per determinare:

1. se esistono feature gate lato frontend;
2. se esistono controlli licensing/subscription/plan;
3. se esistono funzionalità nascoste o disabilitate graficamente;
4. se il frontend applica limiti indipendenti dal backend;
5. se esistono controlli specifici Cloud/Self-hosted;
6. se il frontend usa i `PlanFeatures` o equivalenti;
7. se esistono route/componenti accessibili solo per determinati piani;
8. se esistono controlli commerciali che richiederebbero modifiche;
9. se il frontend attuale può essere utilizzato senza modificarne il codice;
10. quali verifiche runtime sono necessarie per confermare l'analisi.

**NON modificare il frontend durante questo MOD.**

Il risultato deve essere una decisione tecnica documentata, non un'implementazione.

---

# 3. Fonti da leggere prima

Leggere nell'ordine:

1. `CLAUDE.md`;
2. `22-audit-consolidation.md`;
3. `23-mod005-runtime-integration-verification.md`;
4. `24-mod006-deployment-alignment.md`;
5. `25-mod007-documentation-baseline.md`;
6. eventuali documenti precedenti relativi a licensing e PlanFeatures;
7. struttura del repository relativa esclusivamente al frontend.

Non rileggere automaticamente backend, database o tutti i documenti storici.

Usare la documentazione MOD come fonte primaria.

---

# 4. Scope

## IN SCOPE

Analisi del codice frontend web, incluse se presenti:

- React/TypeScript;
- routing;
- context/provider;
- hooks;
- feature flags;
- permission checks;
- plan/subscription checks;
- licensing checks;
- Cloud/Self-hosted detection;
- component visibility;
- disabled UI;
- upgrade prompts;
- premium/business/enterprise checks;
- API client;
- gestione delle informazioni provenienti dal backend;
- build/configuration del frontend;
- variabili d'ambiente rilevanti;
- eventuali test frontend già presenti.

## OUT OF SCOPE

NON modificare:

- frontend source;
- backend source;
- database;
- Docker Compose;
- Dockerfile;
- nginx;
- Caddy;
- mobile application;
- licensing backend;
- API contract.

Non risolvere problemi trovati.

---

# 5. Strategia di analisi

Non eseguire una scansione indiscriminata dell'intero repository.

Identificare prima:

```text
frontend/
├── package.json
├── source directories
├── routing
├── auth
├── permissions
├── feature/license logic
├── API client
└── configuration
```

Poi cercare sistematicamente pattern come:

```text
license
licensing
subscription
plan
feature
featureFlag
entitlement
premium
business
enterprise
pro
cloud
selfHosted
self-hosted
isCloud
upgrade
pricing
trial
canAccess
hasFeature
hasPermission
permission
disabled
hidden
locked
```

Usare sia ricerca testuale sia analisi semantica del codice.

Non assumere che i nomi dei pattern siano esatti: seguire anche i flussi di dati.

---

# 6. Analisi dei feature gate

Per ogni feature gate trovato determinare:

```text
FEATURE:
FILE:
COMPONENT/ROUTE:
CONDIZIONE:
SOURCE DELLA CONDIZIONE:
EFFETTO UI:
EFFETTO API:
PIANO/LICENZA COINVOLTO:
```

Classificare ogni gate come:

- licensing commerciale;
- permission/authorization legittima;
- configurazione;
- feature tecnica;
- Cloud-only;
- Self-hosted;
- non rilevante.

È fondamentale NON classificare come "restriction" un normale controllo di autorizzazione.

---

# 7. Confronto con backend

Confrontare il frontend con le feature già verificate nel backend.

In particolare:

- `PlanFeatures`;
- `FILE`;
- BUSINESS;
- attachment functionality;
- tenant/company isolation;
- eventuali feature già sbloccate nei MOD precedenti.

Obiettivo:

```text
BACKEND FEATURE AVAILABLE
        ↓
FRONTEND FEATURE VISIBLE?
        ↓
FRONTEND FEATURE USABLE?
```

Se una feature è disponibile nel backend ma non raggiungibile dalla UI, documentare precisamente perché.

Non modificare il codice per correggerla.

---

# 8. Cloud vs Self-hosted

Determinare se il frontend distingue tra:

```text
Cloud
Self-hosted
```

e se questa distinzione produce:

- feature nascoste;
- pagine non accessibili;
- pulsanti disabilitati;
- messaggi di upgrade;
- route differenti;
- chiamate verso servizi esterni;
- dipendenze da servizi Atlas Cloud.

Separare chiaramente:

```text
commercial restriction
```

da:

```text
deployment/configuration behavior
```

---

# 9. Licensing

Verificare se il frontend:

- contatta Keygen;
- legge dati di licenza;
- verifica subscription;
- richiede token commerciali;
- utilizza API di licensing;
- nasconde funzionalità in base al piano.

Se vengono trovati riferimenti a Keygen o servizi commerciali:

1. documentare il percorso esatto;
2. determinare se viene realmente eseguito;
3. determinare se è necessario per il funzionamento self-hosted;
4. NON rimuoverlo in questo MOD.

La decisione sulla modifica verrà presa dopo l'audit.

---

# 10. API

Verificare come il frontend determina:

- API base URL;
- authentication endpoint;
- user/company information;
- plan information;
- feature information;
- permission information.

Determinare se il frontend richiede endpoint che potrebbero essere influenzati dalle modifiche backend.

Non modificare API client.

---

# 11. Runtime verification

Se l'ambiente locale del repository è già disponibile, eseguire solo test non distruttivi utili a confermare l'audit.

Verificare almeno:

- applicazione frontend avviabile;
- login;
- accesso alla dashboard;
- accesso alle feature già verificate;
- comportamento di eventuali feature gate identificati;
- console/network errors rilevanti.

Non creare un nuovo ambiente di deployment se non necessario.

Non modificare configurazioni persistenti.

---

# 12. Mobile

Il mobile è **fuori scope**.

Non analizzare o modificare Android/iOS in MOD-008.

Il mobile verrà analizzato in un MOD separato dopo aver concluso l'audit frontend web.

---

# 13. Classificazione finale

Ogni possibile restrizione deve essere classificata:

### A — Nessuna restrizione

La feature è disponibile e non esistono gate commerciali frontend.

### B — Gate legittimo

Il controllo è normale authorization/permission e non deve essere rimosso.

### C — Gate commerciale

La UI limita una funzionalità in base a licensing/plan/subscription.

### D — Gate Cloud/Self-hosted

La limitazione dipende dal deployment e deve essere valutata separatamente.

### E — Da verificare

Il codice non permette una conclusione certa.

---

# 14. Decisione richiesta

Il report deve concludere con una delle seguenti decisioni:

```text
FRONTEND STATUS: CLEAN
```

Il frontend non presenta restrizioni commerciali rilevanti e non deve essere modificato.

oppure:

```text
FRONTEND STATUS: FINDINGS
```

Sono state identificate restrizioni che richiedono una futura decisione/implementazione.

oppure:

```text
FRONTEND STATUS: INCONCLUSIVE
```

Mancano evidenze sufficienti e occorre un'analisi ulteriore.

---

# 15. Regole di autonomia

Claude può autonomamente:

- leggere codice;
- cercare pattern;
- analizzare flussi;
- eseguire test non distruttivi;
- produrre documentazione;
- aggiornare `CLAUDE.md` se lo stato del progetto cambia o se vengono aggiunte decisioni/finding rilevanti.

Richiede approvazione prima di:

- modificare frontend;
- rimuovere licensing;
- modificare API;
- aggiungere dipendenze;
- cambiare architettura;
- modificare Docker;
- modificare configurazione production.

---

# 16. Anti-hallucination

NON assumere che una feature sia limitata solo perché:

- esiste una variabile `plan`;
- esiste un componente chiamato `Premium`;
- esiste una pagina pricing;
- esiste codice Cloud;
- esiste un controllo permission.

Deve essere dimostrato il percorso:

```text
condition
→ execution
→ effect on feature
```

Se il comportamento non è dimostrabile, classificare come `DA VERIFICARE`.

Non inventare API, feature, piani o comportamenti.

---

# 17. Documentazione

Produrre:

```text
docs/self-hosted-audit/26-mod008-frontend-licensing-audit.md
```

Il report deve contenere:

```text
# MOD-008 — Frontend Licensing & Feature-Gate Audit

## 1. Objective
## 2. Sources Reviewed
## 3. Frontend Architecture Relevant to Audit
## 4. Licensing Checks
## 5. Feature Gates
## 6. Cloud/Self-hosted Checks
## 7. Plan/Permission Checks
## 8. Backend ↔ Frontend Feature Comparison
## 9. Runtime Verification
## 10. Findings
## 11. Classification
## 12. Recommended Next Action
## 13. CLAUDE.md Update
## 14. Final Verdict
```

Ogni verifica importante deve utilizzare:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

---

# 18. CLAUDE.md

Al termine:

- aggiornare `CLAUDE.md` se emergono informazioni nuove sullo stato reale;
- registrare eventuali finding frontend;
- registrare eventuali decisioni aperte;
- aggiornare Documentation Map;
- aggiornare Current Project State con MOD-008.

Il report deve dichiarare:

```text
CLAUDE.md updated: YES/NO
Reason:
```

Se non è necessario modificarlo, dimostrarne la coerenza.

---

# 19. Git

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

# 20. Definition of Done

MOD-008 è completato quando:

- l'intero frontend rilevante per licensing/feature gates è stato analizzato;
- sono stati identificati i controlli commerciali eventualmente presenti;
- sono stati separati licensing e authorization;
- è stato verificato Cloud vs Self-hosted;
- è stato confrontato frontend/backend;
- sono state prodotte evidence sufficienti;
- nessun codice è stato modificato;
- il report è stato creato;
- `CLAUDE.md` è stato verificato/aggiornato;
- il verdict è esplicito.

---

# 21. STOP CONDITION

Al termine:

**STOP.**

Non implementare alcuna correzione frontend.

Non iniziare MOD-009.

Non analizzare mobile.

Non modificare Docker/nginx/backend.

Non implementare CFG-02.

La decisione successiva verrà presa dopo la revisione del report MOD-008.

Final output obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: NONE
Frontend status: CLEAN / FINDINGS / INCONCLUSIVE
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
