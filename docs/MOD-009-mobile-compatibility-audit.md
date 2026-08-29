# MOD-009 — Mobile Android/iOS Licensing & Compatibility Audit

## 1. Contesto

MOD-008 è concluso con verdict **PASS / FRONTEND CLEAN**.

Il frontend web non presenta restrizioni commerciali bloccanti per il self-hosted:

- nessuna validazione Keygen lato frontend;
- licensing letto dal backend;
- entitlement self-hosted soddisfatti;
- BUSINESS con tutte le 17 `PlanFeatures`;
- `isCloudVersion` limita billing/marketing/support cloud, non le feature;
- i controlli permission sono normali autorizzazioni;
- nessuna modifica al frontend web è necessaria.

Il mobile Android/iOS è rimasto fuori scope e deve ora essere analizzato separatamente.

L'obiettivo di questo MOD non è modificare l'app mobile, ma determinare se l'app ufficiale mobile del progetto può essere utilizzata con il nostro backend self-hosted modificato.

---

# 2. Obiettivo

Determinare se l'app mobile Android/iOS:

1. può collegarsi al nostro backend self-hosted;
2. utilizza le stesse API del backend modificato;
3. contiene controlli propri di licensing/subscription/plan;
4. contiene feature gate commerciali;
5. distingue Cloud e Self-hosted;
6. richiede servizi Atlas Cloud;
7. effettua chiamate Keygen o altri servizi commerciali;
8. nasconde funzionalità in base al piano;
9. richiede modifiche al codice mobile;
10. può essere utilizzata nella configurazione attuale senza fork/modifiche.

La domanda finale è:

> **Possiamo usare l'app mobile ufficiale con il nostro Atlas self-hosted modificato senza modificarla?**

**NON modificare il codice mobile durante MOD-009.**

---

# 3. Fonti obbligatorie

Prima di analizzare il codice leggere:

1. `CLAUDE.md`;
2. `22-audit-consolidation.md`;
3. `23-mod005-runtime-integration-verification.md`;
4. `24-mod006-deployment-alignment.md`;
5. `25-mod007-documentation-baseline.md`;
6. `26-mod008-frontend-licensing-audit.md`.

Poi individuare esclusivamente la parte mobile del repository.

Non rileggere l'intero backend/frontend se non necessario.

---

# 4. Scope

## IN SCOPE

Analizzare:

- progetto Android;
- progetto iOS;
- React Native/shared mobile code, se presente;
- API client mobile;
- configurazione server URL;
- autenticazione;
- token/session management;
- licensing;
- subscription;
- plan;
- feature flags;
- entitlement;
- Cloud/Self-hosted;
- Keygen;
- Paddle;
- analytics/support cloud;
- route/component visibility;
- offline functionality;
- upload/download allegati;
- QR/barcode;
- work orders;
- asset;
- notifiche;
- eventuali controlli commerciali.

## OUT OF SCOPE

NON modificare:

- mobile source;
- backend;
- frontend web;
- Docker;
- nginx;
- Caddy;
- database;
- licensing backend.

Non implementare correzioni.

---

# 5. Struttura da identificare

Individuare prima la struttura reale del repository.

Determinare:

```text
mobile/
android/
ios/
shared/
packages/
```

o eventuali percorsi differenti.

Non assumere che questi percorsi esistano.

Documentare il percorso effettivo trovato.

---

# 6. Server URL

Questo è uno degli aspetti più importanti.

Determinare come l'app mobile sceglie il backend:

```text
hardcoded URL?
environment variable?
build-time configuration?
runtime configuration?
manual server selection?
deep link?
QR?
```

Individuare:

- URL default;
- URL Cloud;
- possibilità di impostare un server custom;
- eventuale blocco a domini Atlas;
- eventuale verifica TLS/certificato;
- eventuali redirect.

Determinare se è tecnicamente possibile configurare:

```text
https://<nostro-server>/api
```

senza modificare il codice.

Se il codice supporta server custom, documentare esattamente come.

NON inventare una procedura utente se non è supportata dal codice.

---

# 7. API Compatibility

Analizzare il client API mobile.

Determinare:

- base URL;
- endpoint utilizzati;
- autenticazione;
- headers;
- token;
- refresh token;
- versioning API;
- upload file;
- download file;
- websocket, se presente;
- notifiche/push;
- endpoint specifici Cloud.

Confrontare solo gli endpoint rilevanti con il backend già verificato.

Non fare un audit completo di tutte le API Atlas.

---

# 8. Licensing

Cercare sistematicamente:

```text
license
licensing
keygen
subscription
plan
feature
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
paddle
```

Usare sia ricerca testuale sia analisi del flusso.

Per ogni risultato determinare:

```text
FILE:
COMPONENT:
CONDITION:
DATA SOURCE:
EFFECT:
COMMERCIAL / AUTHORIZATION / CONFIGURATION:
```

Non considerare automaticamente un controllo come restrizione commerciale.

---

# 9. Keygen / servizi commerciali

Verificare specificamente se l'app:

- contatta Keygen;
- valida licenze localmente;
- usa token commerciali;
- chiama API di subscription;
- chiama Paddle;
- dipende da Atlas Cloud per l'autenticazione o per le feature.

Se esistono chiamate:

1. documentare endpoint;
2. documentare chi le attiva;
3. verificare se sono obbligatorie;
4. determinare se sono bypassabili tramite normale configurazione self-hosted;
5. NON modificarle.

---

# 10. Cloud vs Self-hosted

Determinare se esistono condizioni del tipo:

```text
if cloud
    feature
else
    feature disabled
```

Distinguere:

### A
Feature esclusivamente cloud.

### B
Servizio cloud opzionale.

### C
Funzionalità commerciale.

### D
Configurazione/deployment.

### E
Autorizzazione normale.

Particolare attenzione a:

- billing;
- subscription;
- analytics;
- Intercom/support;
- trial;
- push notification service;
- cloud storage;
- cloud-only endpoints.

---

# 11. Feature gate mobile

Creare una tabella:

| Feature | Gate | Condizione | Effetto | Self-hosted |
|---|---|---|---|---|
| Assets | ... | ... | ... | ... |
| Work Orders | ... | ... | ... | ... |
| Attachments | ... | ... | ... | ... |
| QR/Barcode | ... | ... | ... | ... |
| Offline | ... | ... | ... | ... |
| Notifications | ... | ... | ... | ... |

Non limitarsi alle feature elencate: includere quelle effettivamente trovate nel codice.

---

# 12. Confronto con backend

Per le feature già verificate nei MOD precedenti verificare:

```text
BACKEND AVAILABLE
        ↓
API USED BY MOBILE
        ↓
MOBILE UI AVAILABLE
        ↓
MOBILE FEATURE USABLE
```

Particolare attenzione a:

- FILE;
- allegati;
- asset;
- work orders;
- utenti;
- company;
- permissions.

Se l'app utilizza endpoint standard invariati, documentarlo.

---

# 13. Allegati

Gli allegati sono particolarmente importanti perché MOD-004B/004C ha introdotto controlli di sicurezza nel backend.

Verificare se il mobile:

- effettua upload;
- scarica file;
- visualizza file;
- richiede un content type specifico;
- richiede un comportamento diverso dal frontend web.

NON modificare il backend per adattarlo al mobile.

Se viene rilevata incompatibilità, documentarla come finding.

---

# 14. Autenticazione

Determinare:

- login endpoint;
- token storage;
- refresh;
- logout;
- company selection;
- ruoli;
- permission handling;
- eventuale SSO;
- eventuale LDAP indiretto.

Non modificare il sistema di autenticazione.

---

# 15. Offline

Determinare:

- se esiste modalità offline;
- quali dati vengono memorizzati;
- come avviene il sync;
- se il sync dipende da servizi Cloud;
- se il comportamento cambia in self-hosted.

Non modificare la sincronizzazione.

Se la documentazione/codice non permette di determinare completamente il comportamento, segnare:

```text
DA VERIFICARE
```

---

# 16. Android e iOS

Analizzare entrambi.

Se condividono lo stesso codice React Native, non duplicare inutilmente l'analisi.

Separare solo le differenze:

```text
Android-specific
iOS-specific
Shared
```

Verificare anche eventuali differenze nella configurazione del server.

---

# 17. Runtime

Se il progetto dispone già di un ambiente mobile configurabile senza modificare il codice, è possibile eseguire test non distruttivi.

NON è obbligatorio creare un ambiente mobile completo in questo MOD.

Se il test runtime richiede:

- build;
- signing;
- account store;
- certificati;
- modifica configurazione;
- infrastruttura esterna;

documentare il requisito e fermarsi.

Non introdurre modifiche per ottenere il test.

---

# 18. Classificazione

Ogni finding deve essere classificato:

### A — Compatible

L'app può utilizzare il backend self-hosted senza modifiche.

### B — Compatible with configuration

Funziona, ma richiede una configurazione documentabile.

### C — Mobile commercial gate

Una feature è limitata da licensing/plan.

### D — Cloud dependency

Una feature dipende realmente da un servizio Cloud.

### E — API incompatibility

L'app richiede API non disponibili o modificate.

### F — Code modification required

La compatibilità richiede una modifica al codice mobile.

### G — Unknown

Non ci sono evidenze sufficienti.

---

# 19. Decisione finale

Il report deve rispondere chiaramente:

```text
OFFICIAL MOBILE APP:
COMPATIBLE / COMPATIBLE WITH CONFIGURATION /
REQUIRES MODIFICATION / INCOMPATIBLE / UNKNOWN
```

e separatamente:

```text
ANDROID:
...

IOS:
...

SHARED MOBILE CODE:
...
```

La risposta deve distinguere sempre tra:

```text
"compatibile tecnicamente"
```

e:

```text
"pubblicabile/distribuibile tramite store"
```

La seconda questione non deve essere analizzata in questo MOD se non emerge direttamente dal codice/documentazione.

---

# 20. Regole di autonomia

Claude può autonomamente:

- leggere codice;
- cercare pattern;
- analizzare API;
- analizzare configurazione;
- eseguire test non distruttivi;
- produrre documentazione;
- aggiornare `CLAUDE.md`.

Richiede conferma prima di:

- modificare mobile;
- modificare API;
- modificare backend;
- aggiungere dipendenze;
- modificare Docker;
- modificare configurazioni production;
- modificare sistemi esterni.

---

# 21. Anti-hallucination

NON assumere:

- che l'app supporti server custom;
- che esista una modalità self-hosted;
- che Android e iOS siano identici;
- che una feature mobile sia disponibile;
- che un endpoint sia compatibile;
- che un servizio Cloud sia opzionale.

Ogni conclusione deve essere supportata da:

```text
codice
documentazione
runtime evidence
```

Quando non è possibile dimostrarla:

```text
DA VERIFICARE
```

---

# 22. Documentazione

Produrre:

```text
docs/self-hosted-audit/27-mod009-mobile-compatibility-audit.md
```

Struttura:

```text
# MOD-009 — Mobile Compatibility Audit

## 1. Objective
## 2. Repository Mobile Structure
## 3. Android
## 4. iOS
## 5. Shared Mobile Code
## 6. Server Configuration
## 7. API Compatibility
## 8. Authentication
## 9. Licensing
## 10. Cloud/Self-hosted
## 11. Feature Gates
## 12. Attachments
## 13. Offline
## 14. Runtime Verification
## 15. Findings
## 16. Compatibility Matrix
## 17. Required Changes
## 18. CLAUDE.md Update
## 19. Final Verdict
```

Ogni verifica importante:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

---

# 23. CLAUDE.md

Alla fine aggiornare `CLAUDE.md` se il MOD introduce nuove informazioni sullo stato reale.

Aggiornare:

- Current Project State;
- Documentation Map;
- eventuali Open Decisions;
- eventuali Approved Decisions.

Il report deve dichiarare:

```text
CLAUDE.md updated: YES/NO
Reason:
```

---

# 24. Git

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

# 25. Secret handling

Non riportare:

- password;
- token;
- API key;
- JWT;
- OAuth secrets;
- signing credentials;
- store credentials.

Usare:

```text
********
```

---

# 26. Definition of Done

MOD-009 è completato quando:

- Android è stato analizzato;
- iOS è stato analizzato;
- il codice condiviso è stato analizzato;
- server URL è stato identificato;
- API compatibility è stata analizzata;
- licensing è stato analizzato;
- Cloud/Self-hosted è stato analizzato;
- feature gate sono stati classificati;
- allegati sono stati verificati a livello di compatibilità;
- eventuali dipendenze Cloud sono state identificate;
- non è stato modificato codice;
- è stato prodotto il report;
- `CLAUDE.md` è stato verificato/aggiornato;
- il verdetto finale è esplicito.

---

# 27. STOP CONDITION

Al termine:

**STOP.**

Non modificare il mobile.

Non iniziare il Local Acceptance Test.

Non iniziare il deployment live.

Non modificare frontend web.

Non modificare backend.

Non implementare CFG-02.

La decisione successiva verrà presa dopo la revisione del report MOD-009.

Output finale obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: NONE
ANDROID: COMPATIBLE / ...
IOS: COMPATIBLE / ...
SHARED: ...
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
