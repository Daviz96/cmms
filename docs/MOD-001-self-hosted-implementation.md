# Atlas CMMS — MOD-001
## Implementazione della modalità Self-Hosted centralizzata

## Ruolo

Sei il coding agent incaricato di implementare la prima modifica ufficiale del fork Atlas CMMS.

L'audit precedente è stato completato ed è considerato **attuale e verificato**. Anche il codice analizzato dall'audit è quello attualmente presente nel repository.

Il responsabile tecnico ha approvato l'implementazione di **MOD-001**.

Il tuo compito è implementare una modalità `Self-Hosted` centralizzata per il licensing, mantenendo intatto il comportamento del licensing commerciale quando la modalità self-hosted è disattivata.

---

# 1. Obiettivo

Vogliamo poter eseguire Atlas CMMS in modalità:

```text
Self-Hosted
```

senza richiedere una licenza Keygen per le funzionalità che sono già implementate nel codice ma protette dagli entitlement.

La soluzione deve essere centralizzata.

NON vogliamo modificare decine di controller/service.

NON vogliamo eliminare i controlli `hasEntitlement()`.

NON vogliamo sostituire globalmente `hasEntitlement()` con `true`.

NON vogliamo eliminare Keygen dal progetto.

Vogliamo invece introdurre una decisione centralizzata nel livello licensing:

```text
                     ┌──────────────────────┐
                     │ Self-hosted flag     │
                     └──────────┬───────────┘
                                │
                  ┌─────────────┴─────────────┐
                  │                           │
              TRUE                          FALSE
                  │                           │
        Self-hosted licensing           Commercial licensing
                  │                           │
                  ↓                           ↓
        entitlement locali                 Keygen
                  │                           │
                  └─────────────┬─────────────┘
                                ↓
                         LicensingState
                                ↓
                         hasEntitlement()
                                ↓
                       resto dell'applicazione
```

---

# 2. Nome della configurazione

Utilizza come configurazione ufficiale:

```text
LICENSING_SELF_HOSTED_MODE
```

Tipo:

```text
boolean
```

Valore:

```text
false
```

come default sicuro.

Il comportamento desiderato è:

```text
LICENSING_SELF_HOSTED_MODE=false
    ↓
comportamento commerciale attuale
```

e:

```text
LICENSING_SELF_HOSTED_MODE=true
    ↓
modalità self-hosted
```

Il valore deve poter essere impostato tramite environment variable nel deployment Docker.

Non hardcodare `true`.

---

# 3. Regola fondamentale: comportamento FALSE

Questa è una condizione di regressione critica.

Quando:

```text
LICENSING_SELF_HOSTED_MODE=false
```

Atlas deve comportarsi come prima.

Devono continuare a funzionare normalmente:

- LICENSE_KEY;
- LICENSE_FILE_PATH;
- Keygen;
- licensing validation;
- expiration;
- entitlement commerciali;
- plan;
- cache;
- error handling;
- limiti;
- subscription logic.

Non deve cambiare il comportamento del deployment commerciale.

---

# 4. Regola fondamentale: comportamento TRUE

Quando:

```text
LICENSING_SELF_HOSTED_MODE=true
```

il sistema deve:

1. NON richiedere `LICENSE_KEY`;
2. NON richiedere `LICENSE_FILE_PATH`;
3. NON contattare Keygen per determinare gli entitlement;
4. NON fallire per assenza di licenza commerciale;
5. restituire un `LicensingState` coerente;
6. permettere agli entitlement previsti dal self-hosted di risultare attivi;
7. mantenere funzionante `hasEntitlement()`;
8. permettere al frontend di ricevere uno stato coerente tramite `/license/state`;
9. non bypassare alcun controllo di autorizzazione applicativa.

---

# 5. Quali entitlement abilitare

NON creare un elenco arbitrario nel codice.

Utilizza come fonte di verità:

```text
docs/self-hosted-audit/
```

in particolare:

```text
01-license-entitlements.md
04-feature-matrix.md
11-modification-plan.md
12-test-plan.md
```

e il codice attuale di:

```text
LicenseEntitlement
PlanFeatures
LicenseService
```

La modalità self-hosted deve abilitare le feature che l'audit ha classificato come già implementate e sbloccabili tramite licensing.

Deve essere possibile spiegare, nel codice/documentazione, perché ogni entitlement è attivo.

Se esistono entitlement che rappresentano funzionalità commerciali realmente dipendenti da servizi esterni o codice non disponibile, NON abilitarli automaticamente solo perché appartengono all'enum.

Segnalare eventuali eccezioni.

---

# 6. Dove implementare

L'implementazione deve essere centralizzata nel livello licensing già esistente.

Prima di modificare, individua l'implementazione effettiva di:

```text
LicenseService
LicensingState
LicenseEntitlement
LicenseController
```

e segui lo stile architetturale già presente.

La preferenza è:

```text
configuration
    ↓
LicenseService
    ↓
LicensingState / entitlement resolution
    ↓
hasEntitlement()
```

Evita di aggiungere controlli:

```text
if (selfHosted) ...
```

in controller o service che già utilizzano `hasEntitlement()`.

---

# 7. `hasEntitlement()`

Il metodo deve continuare a essere utilizzato normalmente.

Esempio concettuale:

```text
hasEntitlement(X)
       │
       ├── self-hosted → X abilitato secondo policy
       │
       └── commercial → comportamento licensing attuale
```

Non duplicare la logica di entitlement in ogni chiamante.

Se è possibile ottenere il comportamento tramite il `LicensingState` già esistente, preferire questa soluzione.

---

# 8. `LicensingState`

Verificare il significato attuale dei campi:

```text
valid
hasLicense
planName
entitlements
...
```

La modalità self-hosted deve produrre uno stato semanticamente coerente.

Non inventare valori senza verificarne l'utilizzo nel repository.

In particolare verificare come il frontend interpreta:

```text
valid
hasLicense
planName
entitlements
```

e assicurarsi che `/license/state` non mostri uno stato contraddittorio.

---

# 9. Frontend

NON modificare il frontend se non è necessario.

Prima verificare che il frontend utilizzi già:

```text
/license/state
```

e gli entitlement restituiti dal backend.

Se il nuovo `LicensingState` è sufficiente a sbloccare correttamente le funzionalità, limitare le modifiche al backend.

Modificare il frontend solo se esiste un blocco che non può essere risolto attraverso lo stato licensing.

Se modifichi il frontend, documentare esattamente:

- file;
- componente;
- motivo;
- comportamento precedente;
- comportamento nuovo.

---

# 10. Keygen

In modalità self-hosted:

```text
Keygen NON deve essere contattato.
```

Questo deve essere vero anche in assenza di:

```text
LICENSE_KEY
LICENSE_FILE_PATH
```

Verificare attentamente:

- startup;
- initialization;
- cache;
- scheduled jobs;
- refresh;
- background tasks;
- endpoint `/license/state`.

Non deve esistere un percorso secondario che contatta Keygen prima che venga valutata la modalità self-hosted.

---

# 11. Cache

La modalità self-hosted non deve introdurre uno stato cache incoerente.

Verificare:

- cache di `LicensingState`;
- invalidazione;
- refresh;
- startup;
- eventuali singleton;
- eventuali scheduler.

Il comportamento deve essere deterministico:

```text
self-hosted=true
    → self-hosted licensing

self-hosted=false
    → commercial licensing
```

senza dipendere dal fatto che una vecchia risposta Keygen sia presente nella cache.

---

# 12. PlanFeatures

Non modificare `PlanFeatures` se non è necessario.

L'audit precedente ha indicato che il piano/business e le feature sono già strutturati in modo tale da poter rappresentare le funzionalità necessarie.

Prima di modificare questo livello, verificare se MOD-001 può essere implementata esclusivamente tramite `LicenseService`.

La preferenza è:

```text
MOD-001
→ LicenseService
→ LicensingState
→ configuration
```

e non:

```text
MOD-001
→ modifica massiva di PlanFeatures
→ modifica di SubscriptionService
→ modifica di UserService
→ modifica di controller
```

Se una modifica a questi componenti è realmente necessaria, documentarla prima nel report finale.

---

# 13. Sicurezza

La modalità self-hosted deve bypassare esclusivamente il **licensing commerciale**.

NON deve bypassare:

- autenticazione;
- JWT;
- ruoli;
- permission;
- company/tenant isolation;
- ownership;
- authorization;
- rate limiting;
- API security;
- validation;
- audit logging.

Esempio:

```text
hasEntitlement(UNLIMITED_ASSETS)
    → self-hosted = true
    → ALLOW

hasPermission(user, ASSET_CREATE)
    → deve continuare a essere verificato
```

Il fatto che una feature sia disponibile non significa che tutti gli utenti debbano poterla utilizzare.

---

# 14. Logging

Aggiungere logging appropriato per permettere di capire che Atlas è in modalità self-hosted.

Esempio concettuale:

```text
Atlas licensing mode: SELF_HOSTED
```

Non loggare:

- password;
- JWT;
- secret;
- LICENSE_KEY;
- SMTP password;
- MinIO password;
- altri dati sensibili.

Il log deve essere utile durante il troubleshooting senza esporre credenziali.

---

# 15. Test obbligatori

Creare o aggiornare i test necessari.

Devono essere coperti almeno questi scenari.

## Test 1 — Self-hosted enabled

```text
LICENSING_SELF_HOSTED_MODE=true
```

Verificare:

- nessun errore per LICENSE_KEY assente;
- nessun errore per LICENSE_FILE_PATH assente;
- Keygen non viene chiamato;
- `LicensingState` è coerente;
- entitlement self-hosted disponibili;
- `hasEntitlement()` restituisce il risultato atteso.

---

## Test 2 — Self-hosted disabled

```text
LICENSING_SELF_HOSTED_MODE=false
```

Verificare che il comportamento precedente rimanga invariato.

Questo è il test di regressione più importante.

---

## Test 3 — Frontend licensing state

Verificare l'endpoint:

```text
GET /license/state
```

in modalità self-hosted.

Verificare che il frontend riceva:

- stato coerente;
- plan coerente;
- entitlement coerenti.

---

## Test 4 — Entitlement negativo

Se esistono entitlement che NON devono essere automaticamente attivati, verificare che rimangano disattivati.

Non assumere che "self-hosted = qualsiasi entitlement possibile".

La policy deve essere esplicita.

---

## Test 5 — Permission

Verificare che lo sblocco dell'entitlement non bypassi i permessi utente.

Esempio:

```text
feature disponibile
+
utente senza permission
=
ACCESS DENIED
```

---

## Test 6 — Keygen isolation

In self-hosted mode verificare che nessuna chiamata Keygen venga effettuata.

Utilizzare mocking/verifica delle chiamate se l'architettura di test lo permette.

---

# 16. Test di integrazione

Dopo i test unitari eseguire i test pertinenti del backend.

Non eseguire automaticamente una suite enorme se richiede servizi esterni non disponibili.

Documentare:

```text
test eseguiti
test superati
test falliti
test non eseguibili
motivo
```

---

# 17. Verifica Docker

Dopo l'implementazione, verificare che la configurazione Docker possa utilizzare:

```env
LICENSING_SELF_HOSTED_MODE=true
```

senza dover fornire:

```env
LICENSE_KEY=
LICENSE_FILE_PATH=
```

Non modificare inutilmente il compose.

Se il progetto utilizza `.env`, aggiornare eventualmente un `.env.example`, ma **mai inserire secret reali**.

---

# 18. Documentazione

Aggiornare/creare:

```text
docs/self-hosted-audit/13-mod001-implementation.md
```

Il documento deve contenere:

### Implementazione

- file modificati;
- classi;
- metodi;
- motivazione.

### Architettura

Spiegare il nuovo flusso:

```text
ENV
 ↓
configuration
 ↓
LicenseService
 ↓
LicensingState
 ↓
hasEntitlement
 ↓
application
```

### Self-hosted behavior

Spiegare cosa succede con:

```text
LICENSING_SELF_HOSTED_MODE=true
```

### Commercial behavior

Spiegare cosa succede con:

```text
LICENSING_SELF_HOSTED_MODE=false
```

### Entitlements

Elencare gli entitlement effettivamente abilitati e il motivo.

### Security

Spiegare cosa NON viene bypassato.

### Tests

Elencare test eseguiti e risultato.

### Rollback

Spiegare come disabilitare la modalità:

```text
LICENSING_SELF_HOSTED_MODE=false
```

e come tornare al comportamento commerciale.

---

# 19. Git

Mantieni la modifica isolata.

Prima controlla:

```bash
git status
```

Dopo la modifica:

```bash
git diff
git status
```

NON modificare file non necessari.

NON fare commit se non richiesto esplicitamente.

NON pushare il repository.

---

# 20. Criteri di accettazione

MOD-001 è considerata correttamente implementata solo se:

```text
[ ] Self-hosted mode configurabile tramite environment
[ ] Default = false
[ ] Keygen non viene contattato in self-hosted mode
[ ] LICENSE_KEY non è necessario in self-hosted mode
[ ] LICENSE_FILE_PATH non è necessario in self-hosted mode
[ ] LicensingState coerente
[ ] /license/state coerente
[ ] Entitlement corretti
[ ] hasEntitlement() continua a essere usato
[ ] Nessun bypass dei permission
[ ] Nessun bypass dell'autenticazione
[ ] Nessun bypass della company isolation
[ ] Commercial mode invariata
[ ] Test self-hosted presenti
[ ] Test commercial regression presenti
[ ] Test permission presenti
[ ] Documentazione prodotta
[ ] Nessun secret committato
[ ] Nessun refactoring non necessario
```

---

# 21. Regola finale

Non cercare di "sbloccare tutto" indiscriminatamente.

L'obiettivo è creare una modalità self-hosted **tecnicamente pulita e mantenibile**.

Se durante l'implementazione trovi una situazione in cui:

- un entitlement è ambiguo;
- una feature dipende da un servizio commerciale;
- un gate ha anche una funzione di sicurezza;
- il comportamento del frontend non è coerente;
- una modifica richiede un refactoring significativo;

FERMATI su quel punto, documentalo e non introdurre workaround improvvisati.

Alla fine produci:

```text
docs/self-hosted-audit/13-mod001-implementation.md
```

e un riepilogo tecnico finale.

Non procedere autonomamente con MOD-002 o altre funzionalità.

Questa attività è esclusivamente:

```text
IMPLEMENTAZIONE MOD-001
```
