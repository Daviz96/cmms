# MOD-001 — Verification Gate e Hardening

## Contesto

Il coding agent ha completato MOD-001 e prodotto:

```text
docs/self-hosted-audit/13-mod001-implementation.md
```

L'implementazione risulta coerente con l'obiettivo architetturale: il flag
`LICENSING_SELF_HOSTED_MODE` viene gestito centralmente da `LicenseService`,
`hasEntitlement()` non è stato modificato e il comportamento commerciale
dovrebbe rimanere invariato.

Il report dichiara però che i test non sono stati eseguiti perché nell'ambiente
di audit non erano disponibili Java/Maven.

Questa attività NON è una nuova MOD e NON deve introdurre nuove funzionalità.
È un **verification gate** prima di approvare MOD-001 e passare alla fase
successiva.

---

# 1. Obiettivo

Verificare concretamente che MOD-001:

1. compili;
2. superi i test dedicati;
3. non introduca regressioni nel licensing commerciale;
4. non contatti Keygen in modalità self-hosted;
5. produca lo stato `/license/state` atteso;
6. funzioni realmente nel deployment Docker;
7. non alteri autenticazione, authorization, ruoli o tenant isolation;
8. non introduca secret nel repository;
9. utilizzi realmente i file persistenti corretti;
10. sia riproducibile da un ambiente pulito.

NON implementare MOD-002.

---

# 2. Prima fase — ispezione dello stato Git

Eseguire:

```bash
git status --short
git diff --stat
git diff
```

Verificare che le sole modifiche relative a questa attività siano quelle
prodotte da MOD-001.

Non modificare il codice in questa fase salvo bug direttamente necessario per
correggere MOD-001.

Se trovi modifiche preesistenti non riconducibili a MOD-001:

- non eliminarle;
- non sovrascriverle;
- documentarle.

---

# 3. Build backend

Individuare il wrapper Maven corretto.

Il progetto dovrebbe usare Java 17.

Verificare:

```bash
java -version
```

e:

```bash
cd api
./mvnw -version
```

Su Windows usare:

```powershell
.\mvnw.cmd -version
```

Se Java 17 non è disponibile, installarlo/configurarlo nell'ambiente di
sviluppo se hai autorizzazione a farlo.

NON cambiare il requisito Java del progetto.

Eseguire almeno:

```bash
cd api
./mvnw test -Dtest=LicenseServiceTest
```

Poi, se il test dedicato passa:

```bash
./mvnw test
```

Se la suite completa fallisce:

- distinguere errori preesistenti da regressioni MOD-001;
- riportare test, errore e causa;
- non ignorare semplicemente i fallimenti.

---

# 4. Verifica compilazione

La compilazione deve essere verificata realmente, non solo tramite ispezione
del codice.

Eseguire almeno:

```bash
cd api
./mvnw -DskipTests package
```

oppure il comando equivalente previsto dal progetto.

Registrare:

```text
Java version:
Maven version:
Build result:
Test result:
```

---

# 5. Verifica test MOD-001

I cinque test indicati nel documento precedente devono essere eseguiti
realmente.

Verificare almeno:

```text
selfHosted_enabled_returnsValidStateWithAllEntitlements_andNoKeygenCall
selfHosted_disabled_withoutLicense_returnsInvalidState_regression
licenseStateEndpoint_returnsSelfHostedState
selfHosted_entitlementPolicy_isExplicitFullEnum
selfHosted_enabled_doesNotContactKeygen_evenWhenLicenseKeyPresent
```

Non modificare i test semplicemente per farli passare.

Se un test fallisce, analizzare la causa.

---

# 6. Verifica runtime Docker

Se l'ambiente di sviluppo consente di avviare Atlas:

```bash
docker compose config
```

e verificare che:

```text
LICENSING_SELF_HOSTED_MODE
```

sia effettivamente passato al container API.

Poi avviare l'applicazione con:

```env
LICENSING_SELF_HOSTED_MODE=true
```

senza:

```env
LICENSE_KEY
LICENSE_FILE_PATH
```

Verificare:

```bash
docker compose ps
docker compose logs api
```

Non devono esserci errori di licensing all'avvio.

---

# 7. Verifica stato licensing via API

Con Atlas avviato in modalità self-hosted, verificare l'endpoint:

```text
GET /license/state
```

Utilizzare il meccanismo di autenticazione già previsto dal progetto.

Lo stato deve essere coerente con il report:

```text
valid = true
planName = Self-Hosted
expirationDate = null
entitlements = policy self-hosted
```

NON assumere che il solo test unitario sia sufficiente.

Documentare la risposta senza includere token o cookie sensibili.

---

# 8. Verifica Keygen

Questo è un punto critico.

In modalità:

```env
LICENSING_SELF_HOSTED_MODE=true
```

verificare che non venga effettuata alcuna richiesta Keygen.

Se possibile:

- usare mock/spia nei test;
- controllare i log;
- controllare eventuali richieste HTTP;
- controllare il codice dei percorsi di startup e scheduled task.

Verificare anche il caso:

```env
LICENSING_SELF_HOSTED_MODE=true
LICENSE_KEY=<eventuale valore di test>
```

Il flag self-hosted deve avere precedenza e non deve provocare una validazione
commerciale.

NON utilizzare una vera license key o secret reale nel repository.

---

# 9. Verifica modalità commerciale

Questo è il test di regressione più importante.

Avviare Atlas con:

```env
LICENSING_SELF_HOSTED_MODE=false
```

e, quando necessario, con la configurazione licensing prevista dal progetto.

Verificare che il percorso commerciale originale continui a essere utilizzato:

```text
false
 ↓
cache / license file / license key
 ↓
Keygen
 ↓
LicensingState
```

Non deve diventare:

```text
false
 ↓
Self-Hosted
```

Documentare il risultato.

---

# 10. Verifica `hasEntitlement()`

Verificare che:

```text
hasEntitlement(X)
```

continui a dipendere dal `LicensingState`.

NON accettare una soluzione in cui il metodo è stato trasformato in:

```java
return true;
```

o equivalente.

In self-hosted:

```text
LicensingState
  ↓
entitlements
  ↓
hasEntitlement()
```

In commercial:

```text
commercial LicensingState
  ↓
entitlements
  ↓
hasEntitlement()
```

---

# 11. Verifica sicurezza

Concentrarsi esclusivamente sul fatto che MOD-001 non abbia alterato i livelli
di sicurezza.

Controllare il diff e verificare che non siano stati modificati
involontariamente:

- JWT;
- authentication;
- API key;
- role permissions;
- company/tenant isolation;
- ownership;
- authorization;
- rate limiting;
- audit logging.

NON serve una nuova security audit completa del progetto.

Serve una verifica di regressione limitata a MOD-001.

---

# 12. Punto critico — policy degli entitlement

Il report dichiara:

```text
SELF_HOSTED_ENTITLEMENTS = LicenseEntitlement.values()
```

Questa scelta deve essere verificata prima dell'approvazione definitiva.

Non modificarla automaticamente.

Analizzare ogni valore di:

```text
LicenseEntitlement
```

e classificare:

```text
ENTITLEMENT
→ enforcement reale?
→ feature realmente implementata?
→ dipendenza commerciale?
→ dipendenza da servizio esterno?
→ rischio di attivare una feature che non dovrebbe essere self-hosted?
```

Confrontare il risultato con:

```text
docs/self-hosted-audit/01-license-entitlements.md
docs/self-hosted-audit/04-feature-matrix.md
```

Se l'analisi conferma che tutti gli entitlement possono essere abilitati in
self-hosted, non cambiare il codice.

Se invece trovi un entitlement che non dovrebbe essere incluso:

- NON correggere autonomamente la policy;
- produrre un report con il nome dell'entitlement e la motivazione;
- proporre una modifica separata.

Questo punto verrà deciso dal responsabile tecnico.

---

# 13. Verifica frontend

Verificare che il frontend:

```text
useLicenseEntitlement
```

o equivalente utilizzi realmente gli entitlement ricevuti dal backend.

Verificare almeno una funzionalità precedentemente protetta da licensing.

L'obiettivo è dimostrare il flusso completo:

```text
LicenseService
      ↓
LicensingState
      ↓
/license/state
      ↓
frontend
      ↓
entitlement
      ↓
feature UI
```

Non modificare il frontend se non emerge un bug causato da MOD-001.

---

# 14. Verifica persistenza

MOD-001 non dovrebbe richiedere nuovi dati persistenti.

Verificare che non siano stati creati:

- nuove tabelle;
- migrazioni DB;
- file persistenti;
- cache nuove;
- volumi Docker nuovi.

Se non esistono, documentare:

```text
Persistence changes: none
```

---

# 15. Verifica secret

Controllare:

```bash
git diff
git status
```

e i file:

```text
.env
.env.example
docker-compose.yml
application.yml
```

Assicurarsi che:

- `.env.example` contenga solo placeholder;
- nessuna password reale sia stata aggiunta;
- nessun JWT secret reale sia stato committato;
- nessuna Keygen key reale sia stata aggiunta;
- nessuna credenziale SMTP/MinIO sia stata aggiunta.

Se trovi secret già presenti nel repository prima di MOD-001, non rimuoverli
senza autorizzazione: segnalali separatamente.

---

# 16. Docker Compose

Verificare:

```bash
docker compose config
```

e confermare:

```text
LICENSING_SELF_HOSTED_MODE
```

sia risolto correttamente.

Controllare inoltre che:

```text
LICENSING_SELF_HOSTED_MODE=false
```

sia il comportamento di default quando la variabile non è presente.

---

# 17. Risultato richiesto

NON effettuare altre modifiche funzionali.

Produrre:

```text
docs/self-hosted-audit/14-mod001-verification.md
```

con questa struttura:

```markdown
# 14 — MOD-001 Verification

## Stato
PASS / PASS WITH ISSUES / FAIL

## Git state

## Build

## Unit tests

## Full test suite

## Docker verification

## Self-hosted runtime verification

## Commercial runtime verification

## Keygen isolation

## /license/state verification

## hasEntitlement verification

## Security regression verification

## Entitlement policy review

## Frontend verification

## Secret verification

## Findings

## Issues requiring decision

## Recommendation
```

---

# 18. Regole operative

### Consentito

- installare/configurare Java 17 e Maven nell'ambiente di sviluppo;
- eseguire test;
- avviare container;
- leggere log;
- eseguire richieste HTTP locali;
- correggere esclusivamente bug evidenti introdotti da MOD-001, ma solo se
  indispensabile per ottenere un'implementazione corretta.

### NON consentito

- implementare MOD-002;
- aggiungere nuove funzionalità;
- modificare la policy entitlement senza segnalarlo;
- eliminare controlli di sicurezza;
- eliminare Keygen dal progetto;
- modificare `hasEntitlement()` per bypassare il licensing;
- fare refactoring generale;
- aggiornare dipendenze non necessario;
- fare commit;
- fare push.

---

# 19. Decision gate

Al termine NON decidere autonomamente il prossimo modulo.

Il responsabile tecnico analizzerà:

```text
14-mod001-verification.md
```

e deciderà se:

```text
A → approvare MOD-001
B → correggere MOD-001
C → richiedere ulteriori verifiche
D → procedere con il modulo successivo
```

La tua attività termina con il report di verifica.
