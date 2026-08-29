# 13 — MOD-001: Implementazione modalità Self-Hosted centralizzata

Stato: **implementata**. Modifica isolata, default sicuro (`false`), nessun
refactoring collaterale. Commit **non** eseguito (come da istruzioni).

---

## Implementazione

### File modificati

| File | Modifica |
|---|---|
| [`api/.../service/LicenseService.java`](../../api/src/main/java/com/grash/service/LicenseService.java) | Flag `selfHostedMode`, policy entitlement, short-circuit in `getLicensingState()`, builder self-hosted, log di avvio |
| [`api/src/main/resources/application.yml`](../../api/src/main/resources/application.yml) | Nuova proprietà `licensing.self-hosted-mode` |
| [`.env.example`](../../.env.example) | Variabile `LICENSING_SELF_HOSTED_MODE=false` documentata |
| [`docker-compose.yml`](../../docker-compose.yml) | Passaggio env `LICENSING_SELF_HOSTED_MODE` al servizio `api` |

### File aggiunti

| File | Contenuto |
|---|---|
| [`api/.../service/LicenseServiceTest.java`](../../api/src/test/java/com/grash/service/LicenseServiceTest.java) | Test unit MOD-001 |
| `docs/self-hosted-audit/13-mod001-implementation.md` | Questo documento |

Diff complessivo: **65 inserzioni, 0 rimozioni** su 4 file tracciati (+ 1 test, +
docs). Nessuna riga esistente rimossa/riscritta.

### Classi / metodi toccati (`LicenseService`)

- Campo `boolean selfHostedMode` — `@Value("${licensing.self-hosted-mode:false}")`.
- Costante `SELF_HOSTED_PLAN_NAME = "Self-Hosted"`.
- Costante `SELF_HOSTED_ENTITLEMENTS` — insieme immutabile costruito da
  `LicenseEntitlement.values()` (policy esplicita, vedi sotto).
- `@PostConstruct logLicensingMode()` — logga la modalità all'avvio.
- `getLicensingState()` — **short-circuit** in cima: se `selfHostedMode` →
  `buildSelfHostedLicensingState()`.
- `buildSelfHostedLicensingState()` — costruisce lo stato self-hosted.

**Motivazione:** centralizzare la decisione nel livello licensing (come indicato
in MOD sez. 6/7), senza aggiungere `if (selfHosted)` in controller/service e senza
sostituire `hasEntitlement()` con `true`.

---

## Architettura del nuovo flusso

```
ENV: LICENSING_SELF_HOSTED_MODE
        ↓
application.yml: licensing.self-hosted-mode
        ↓
LicenseService.selfHostedMode  (@Value)
        ↓
getLicensingState()
   ├── selfHostedMode == true  → buildSelfHostedLicensingState()   (nessun Keygen, nessuna cache Keygen)
   └── selfHostedMode == false → percorso commerciale invariato    (cache → key/file → Keygen)
        ↓
LicensingState { valid, hasLicense, planName, entitlements, expirationDate, usersCount }
        ↓
hasEntitlement(X) = state.isValid() && state.entitlements.contains(X)   ← invariato
        ↓
resto dell'applicazione (backend)  +  /license/state (frontend)
```

`hasEntitlement()` **non è stato modificato**: continua a leggere il
`LicensingState`. Cambia solo *come* lo stato viene prodotto quando la modalità
self-hosted è attiva.

---

## Comportamento con `LICENSING_SELF_HOSTED_MODE=true`

`getLicensingState()` ritorna sempre:

| Campo | Valore |
|---|---|
| `valid` | `true` |
| `hasLicense` | `true` |
| `planName` | `"Self-Hosted"` |
| `entitlements` | tutti i nomi di `LicenseEntitlement` |
| `expirationDate` | `null` (nessuna scadenza) |
| `usersCount` | `Integer.MAX_VALUE` (illimitato) |

Conseguenze:

- `LICENSE_KEY` e `LICENSE_FILE_PATH` **non** sono necessari.
- **Keygen non viene contattato** (verificato: l'unico ingresso al path
  `validate-key`/entitlements è `getLicensingState()`, cortocircuitato prima di
  qualunque controllo cache/chiave/file; nessun `@Scheduled`/`@PostConstruct`
  esegue validazioni Keygen). `KeygenService` (provisioning licenze) resta
  invocato **solo** dai webhook Paddle, gated da `cloud-version` e non toccato in
  self-hosted.
- La cache Keygen non viene né letta né scritta: il risultato è deterministico e
  non dipende da risposte Keygen precedenti.
- `hasEntitlement(X)` è `true` per ogni `X`; `isSSOEnabled()` è `true`.
- Il frontend riceve da `/license/state` uno stato coerente
  (`valid=true`, `planName="Self-Hosted"`, entitlements completi) e sblocca le UI
  gate da `useLicenseEntitlement` senza modifiche lato client.

### `usersCount = Integer.MAX_VALUE`

Il frontend **non** usa questo campo (il tipo TS `LicensingState` espone solo
`valid/entitlements/expirationDate/planName`). L'unico lettore backend è
`UserService.checkUsageBasedLimit`, che però è già cortocircuitato
dall'entitlement `UNLIMITED_USERS` (presente in self-hosted); il valore alto è
quindi solo semanticamente coerente ("illimitato") e non entra mai in un calcolo.

---

## Comportamento con `LICENSING_SELF_HOSTED_MODE=false` (default)

**Invariato** rispetto a prima. Continuano a funzionare: `LICENSE_KEY`,
`LICENSE_FILE_PATH`, validazione Keygen, license file offline, scadenza,
entitlement commerciali, `planName`, cache 12h, rate limit giornaliero,
error handling, limiti numerici, logica subscription. Il short-circuit è l'unica
diramazione aggiunta e non viene presa quando il flag è `false`.

Questo è il criterio di **regressione critica** ed è coperto dal test
`selfHosted_disabled_withoutLicense_returnsInvalidState_regression`.

---

## Entitlement abilitati e motivazione (policy esplicita)

La policy self-hosted abilita **l'intero enum `LicenseEntitlement`**. Non è un
elenco arbitrario: deriva programmaticamente da `LicenseEntitlement.values()` e
si fonda sull'audit ([04-feature-matrix.md](04-feature-matrix.md),
[01-license-entitlements.md](01-license-entitlements.md)), che ha verificato che
**ogni** entitlement protegge una feature **già implementata** nel codice AGPL.

Nessun entitlement è stato escluso perché l'audit **non** ha trovato feature
🔴 (non implementate) né 🟠 (dipendenti da codice commerciale non disponibile).

### Eccezioni da conoscere (NON bloccanti, gestite altrove)

Alcuni entitlement, una volta attivi, restano subordinati a **configurazione /
servizi esterni** — che MOD-001 **non** attiva:

| Entitlement | Dipendenza esterna residua | Gate separato |
|---|---|---|
| `SSO` (LDAP) | server LDAP/AD | `ldap.enabled=true` (`@ConditionalOnProperty`) |
| `SSO` (OAuth2) | provider OAuth2 | `enable-sso=true` + config `oauth2.*` |
| `FILE_ATTACHMENTS` | object storage | `STORAGE_TYPE` + credenziali MinIO/GCP |
| `LOW_STOCK_ALERTS`, email | SMTP | `SMTP_*` / `INVITATION_VIA_EMAIL` |

Abilitare l'entitlement **non** forza questi servizi: rimuove solo il blocco
commerciale. Vedi [05-ldap-ad.md](05-ldap-ad.md), [06-storage-attachments.md](06-storage-attachments.md).

### Gate morti (nessun impatto)

`PARTS_COST_TRACKING` e `ADVANCED_ANALYTICS` sono nell'enum ma non hanno
enforcement backend: la loro presenza nel set è innocua.

---

## Security — cosa NON viene bypassato

MOD-001 agisce **esclusivamente** sul licensing commerciale (Livello A). Restano
pienamente attivi e invariati:

- autenticazione / JWT / API key / OAuth2;
- permessi di ruolo (`PermissionEntity`, `getViewPermissions`/`getCreatePermissions`…);
- isolamento multi-tenant per company;
- ownership e authorization;
- rate limiting (upload, scan, request portal);
- validazione input e audit logging (Hibernate Envers).

Esempio: `FileController` continua a richiedere il permesso `FILES` e
`WorkOrderService` i controlli di accesso; sbloccare `FILE_ATTACHMENTS` non
concede l'azione a utenti privi del permesso. Nessuna riga di questi controlli è
stata modificata.

Log: nessun dato sensibile (password, JWT, secret, `LICENSE_KEY`, credenziali
SMTP/MinIO) viene loggato; il log di avvio riporta solo `SELF_HOSTED` / `COMMERCIAL`.

---

## Tests

File: [`LicenseServiceTest.java`](../../api/src/test/java/com/grash/service/LicenseServiceTest.java)
(JUnit 5 + Mockito, stile allineato a `LdapSecurityConfigTest`).

| Test | Scenario MOD | Copre |
|---|---|---|
| `selfHosted_enabled_returnsValidStateWithAllEntitlements_andNoKeygenCall` | Test 1 | stato valido, tutti gli entitlement, `hasEntitlement`/`isSSOEnabled` true, nessuna interazione col tracker Keygen |
| `selfHosted_disabled_withoutLicense_returnsInvalidState_regression` | Test 2 | regressione: senza licenza → invalido, entitlement negati |
| `licenseStateEndpoint_returnsSelfHostedState` | Test 3 | `LicenseController.getValidity` restituisce lo stato self-hosted |
| `selfHosted_entitlementPolicy_isExplicitFullEnum` | Test 4 | policy esplicita = intero enum |
| `selfHosted_enabled_doesNotContactKeygen_evenWhenLicenseKeyPresent` | Test 6 | isolamento Keygen anche con `LICENSE_KEY` presente |

**Isolamento Keygen (Test 6):** poiché ogni validazione commerciale passa dal
`KeygenRequestTrackerRepository` (`canMakeKeygenRequest`/`incrementKeygenRequestCount`),
i test verificano `verifyNoInteractions(keygenRequestTrackerRepository)` come
prova che il path Keygen non è stato imboccato.

### Esecuzione

| Voce | Esito |
|---|---|
| Test scritti | ✅ 5 (LicenseServiceTest) |
| Test eseguiti in locale | ❌ **non eseguibili** |
| Motivo | Nessun JDK/JRE né Maven presenti sulla macchina di audit (verificato: `java`/`mvn` assenti dal PATH, nessun JDK in `Program Files`/`.jdks`/JetBrains). Il progetto richiede Java 17 (`api/system.properties`). |
| Come eseguirli | Nell'ambiente di build/CI: `cd api && ./mvnw.cmd test -Dtest=LicenseServiceTest` (suite completa: `./mvnw.cmd test`; gli integration test richiedono Docker/Testcontainers). |

I test T-permission (Test 5) sono garantiti a livello **architetturale**: MOD-001
non modifica alcun codice di permessi/auth/tenant; i test esistenti
(`RoleControllerTest`, controller/integration test) restano validi e non impattati.

---

## Verifica manuale effettuata (in assenza di build locale)

- Tutti i chiamanti di `getLicensingState()` (`LicenseController`,
  `hasEntitlement`, `UserService.checkUsageBasedLimit`) passano dal short-circuit.
- Il frontend non legge `usersCount`/`hasLicense`; usa
  `valid/entitlements/planName/expirationDate` → coerenti.
- `entitlement.toString()` (usato in `hasEntitlement`) == `name()` (usato nel set
  self-hosted) → i confronti combaciano.
- Diff pulito: 0 rimozioni, solo aggiunte.

---

## Rollback

Impostare:

```env
LICENSING_SELF_HOSTED_MODE=false
```

(o rimuovere la variabile: il default è `false`) e riavviare il backend. Si torna
integralmente al comportamento commerciale basato su Keygen. In alternativa,
`git revert` del commit MOD-001 rimuove ogni traccia (modifica isolata).

---

## Note

- La discrepanza pre-esistente nella lista frontend
  (`UNLIMITED_CHECKLIST` vs backend `UNLIMITED_CHECKLISTS`, ed entitlement mancanti
  in `frontend/src/models/owns/license.ts`) **non** è stata toccata: non incide
  sullo sblocco (l'enforcement dei limiti è server-side) ed esula da MOD-001.
  Segnalata in [01-license-entitlements.md](01-license-entitlements.md) §5.
- `PlanFeatures`/`SubscriptionService`/`UserService`/controller: **non** modificati
  (come da MOD sez. 12), il Livello B è già aperto in self-hosted.
