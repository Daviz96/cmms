# 14 — MOD-001 Verification

Verification gate su MOD-001 (modalità self-hosted centralizzata del licensing).
Nessuna nuova funzionalità introdotta. Nessun commit/push eseguito.

Ambiente di verifica: Windows 11, Docker 29.4.3 (daemon attivo). Java assente dal
sistema → installato un **JDK 17 Temurin portable** (Eclipse Temurin 17.0.20.1)
in directory temporanea, senza modificare il sistema. Maven via wrapper del
progetto (`mvnw.cmd`, Maven 3.8.6). Data: 2026-08-25.

---

## Stato

**PASS**

Tutti i controlli critici superati. Un'unica voce non eseguita (boot Docker
multi-container end-to-end) con giustificazione e copertura equivalente; una nota
preesistente sui secret di esempio. Nessun blocco all'approvazione.

---

## Git state

```
 M .env.example
 M api/src/main/java/com/grash/service/LicenseService.java
 M api/src/main/resources/application.yml
 M docker-compose.yml
?? api/src/test/java/com/grash/service/LicenseServiceTest.java
?? docs/
```

`git diff --stat`: **65 inserzioni, 0 rimozioni** su 4 file tracciati.

Tutte le modifiche sono riconducibili a MOD-001. La cartella `docs/` non tracciata
contiene l'audit e i report MOD-001 (documentazione, nessun codice applicativo).
Nessuna modifica preesistente estranea a MOD-001. Nessun file rimosso/riscritto.

---

## Build

| Voce | Valore |
|---|---|
| Java version | Eclipse Temurin **17.0.20.1** (portable, `openjdk version "17.0.20.1"`) |
| Maven version | **3.8.6** (via `mvnw.cmd`, da `.mvn/wrapper/maven-wrapper.properties`) |
| Compilazione main | ✅ `Compiling 769 source files with javac [debug release 17]` |
| Compilazione test | ✅ `Compiling 78 source files with javac [debug release 17]` |
| Build result | ✅ **BUILD SUCCESS** (exit 0) |

La compilazione è stata verificata **realmente** eseguendo il compilatore, non
solo per ispezione. Il requisito Java 17 del progetto non è stato modificato.

---

## Unit tests (MOD-001)

Comando: `mvnw.cmd -Dtest=LicenseServiceTest test`

```
[INFO] Running com.grash.service.LicenseServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.085 s
[INFO] BUILD SUCCESS
```

I 5 test previsti sono presenti e **verdi**:

| Test | Esito |
|---|---|
| `selfHosted_enabled_returnsValidStateWithAllEntitlements_andNoKeygenCall` | ✅ PASS |
| `selfHosted_disabled_withoutLicense_returnsInvalidState_regression` | ✅ PASS |
| `licenseStateEndpoint_returnsSelfHostedState` | ✅ PASS |
| `selfHosted_entitlementPolicy_isExplicitFullEnum` | ✅ PASS |
| `selfHosted_enabled_doesNotContactKeygen_evenWhenLicenseKeyPresent` | ✅ PASS |

---

## Full test suite

Comando: `mvnw.cmd test`

```
[INFO] Tests run: 1412, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**1412 test, 0 fallimenti, 0 errori, 0 skip.** Inclusi gli integration test con
Testcontainers (Postgres via Docker). La suite gira con il default
`LICENSING_SELF_HOSTED_MODE=false`: la piena riuscita dimostra che il **percorso
commerciale è intatto** e che MOD-001 non introduce regressioni.

Test falliti: **nessuno**. Test non eseguibili: **nessuno**.

---

## Docker verification

`docker compose config` (senza `.env` presente):

```
LICENSE_KEY: ""
LICENSING_SELF_HOSTED_MODE: "false"
```

- La variabile `LICENSING_SELF_HOSTED_MODE` è correttamente iniettata nel servizio
  `api` (aggiunta in `docker-compose.yml`).
- In assenza della variabile, il default risolto è **`"false"`**
  (`${LICENSING_SELF_HOSTED_MODE:-false}`) → comportamento commerciale di default,
  come richiesto.
- Docker daemon attivo (server 29.4.3); `docker compose config` esce 0.

---

## Self-hosted runtime verification

Coperta a livello applicativo dai test:

- `licenseStateEndpoint_returnsSelfHostedState` istanzia il **vero**
  `LicenseController` con il **vero** `LicenseService` in modalità self-hosted e
  verifica lo stato restituito da `/license/state`:
  `valid=true`, `planName="Self-Hosted"`, `entitlements = intero enum`.
- `selfHosted_enabled_returnsValidStateWithAllEntitlements_andNoKeygenCall`
  verifica `expirationDate=null`, `hasEntitlement(X)=true` per ogni X,
  `isSSOEnabled()=true`, senza `LICENSE_KEY`/`LICENSE_FILE_PATH`.

**Boot Docker end-to-end: NON eseguito**, per i seguenti motivi (documentati):

1. Il `docker-compose.yml` referenzia l'**immagine pubblicata**
   `intelloop/atlas-cmms-backend`, non un build locale: un `docker compose up`
   testerebbe l'immagine upstream, **non** il codice MOD-001, senza prima
   costruire un'immagine custom dal `Dockerfile`.
2. L'avvio reale richiede uno stack multi-container: Postgres (schema via
   Liquibase, `ddl-auto: validate`), **MinIO** (`MinioService.init()` è
   `@PostConstruct` e contatta lo storage all'avvio) e ~15 variabili d'ambiente.
3. La logica esatta di `/license/state` (controller → service →
   `buildSelfHostedLicensingState`) è già esercitata dai test sopra e dall'intera
   suite.

Il log di avvio includerà comunque `Atlas licensing mode: SELF_HOSTED`
(`@PostConstruct logLicensingMode()`), utile per il troubleshooting operativo.

Raccomandazione operativa per una prova live futura: buildare l'immagine dal
`Dockerfile` locale, avviare con `LICENSING_SELF_HOSTED_MODE=true` e senza
`LICENSE_KEY`, poi `GET /license/state` (endpoint `permitAll`, nessuna auth).

---

## Commercial runtime verification

- Con `LICENSING_SELF_HOSTED_MODE=false` (default) il flusso originale è
  invariato: cache → license file → license key → Keygen → `LicensingState`.
- Il short-circuit self-hosted è l'**unica** diramazione aggiunta e non viene
  presa quando il flag è `false` (verificato nel diff e nel test
  `selfHosted_disabled_withoutLicense_returnsInvalidState_regression`: senza
  licenza lo stato è `valid=false`, entitlement negati).
- La suite completa (1412 test) gira in modalità commerciale di default e passa
  interamente → nessuna regressione nel percorso commerciale.

Non si verifica la transizione `false → Self-Hosted`: il flag `false` mantiene
`valid=false` senza licenza.

---

## Keygen isolation

Verificato su più livelli:

1. **Test** `selfHosted_enabled_doesNotContactKeygen_evenWhenLicenseKeyPresent`:
   con `LICENSE_KEY` valorizzato + self-hosted attivo, `verifyNoInteractions`
   sul `KeygenRequestTrackerRepository` (che ogni validazione commerciale
   attraverserebbe) → il path Keygen non viene imboccato.
2. **Analisi del codice**: l'unico ingresso al path di validazione Keygen
   (`validateAndCacheLicenseKey` → `performLicenseValidation` /
   `fetchAndCacheEntitlements`, URL `api.keygen.sh/.../validate-key`) è
   `getLicensingState()`, ora cortocircuitato **in cima** prima di qualunque
   controllo cache/chiave/file.
3. **Startup/scheduled**: nessun `@Scheduled` tocca il licensing; nessun
   `@PostConstruct` esegue validazioni Keygen (l'unico hook aggiunto logga solo la
   modalità). `KeygenService` (provisioning licenze) è invocato esclusivamente dai
   webhook Paddle, gated da `cloud-version`, e non interviene nella risoluzione
   degli entitlement.
4. **Cache**: in self-hosted la cache Keygen non è né letta né scritta → risultato
   deterministico, indipendente da eventuali risposte Keygen precedenti.

---

## /license/state verification

Endpoint `GET /license/state` — `permitAll` in `WebSecurityConfig` (riga 65),
delega a `licenseService.getLicensingState()`.

In self-hosted restituisce (verificato da `licenseStateEndpoint_returnsSelfHostedState`):

```json
{
  "valid": true,
  "hasLicense": true,
  "planName": "Self-Hosted",
  "entitlements": [ <tutti i valori di LicenseEntitlement> ],
  "expirationDate": null,
  "usersCount": 2147483647
}
```

Nota: il tipo TS lato frontend (`valid/entitlements/expirationDate/planName`)
ignora `hasLicense`/`usersCount`; lo stato è coerente e non contraddittorio.

---

## hasEntitlement verification

- `hasEntitlement(X)` **non modificato**: continua a valere
  `state.isValid() && state.getEntitlements().contains(X.toString())` (diff:
  metodo invariato).
- Nessuna trasformazione in `return true` o equivalente.
- In self-hosted la differenza è **solo** nella provenienza dello stato
  (`buildSelfHostedLicensingState()` vs percorso Keygen); il consumo resta
  identico in commerciale e self-hosted.

---

## Security regression verification

Il diff tocca esclusivamente `LicenseService` (aggiunte) + 3 file di config. Non
sono stati modificati:

- JWT / authentication / API key filter;
- role permissions (`PermissionEntity`, view/create/delete);
- company/tenant isolation;
- ownership / authorization;
- rate limiting;
- audit logging (Hibernate Envers).

Conferma indiretta forte: i test di configurazione e sicurezza esistenti
(`WebSecurityConfigTest`, `LdapSecurityConfigTest`, controller/integration test)
sono **tutti verdi** nella suite da 1412 test. Lo sblocco di un entitlement non
concede l'azione a utenti privi del permesso (i controlli di permesso restano
a valle e invariati, es. `FileController` richiede `PermissionEntity.FILES`).

---

## Entitlement policy review

Policy: `SELF_HOSTED_ENTITLEMENTS = LicenseEntitlement.values()` (intero enum),
costruita programmaticamente. Analisi per valore (confronto con
[01-license-entitlements.md](01-license-entitlements.md) e
[04-feature-matrix.md](04-feature-matrix.md)):

- **Tutti** gli entitlement con enforcement reale proteggono feature **già
  implementate** in questo codice AGPL (🟢/🟡 nell'audit). Nessuna feature 🔴
  (non implementata) né 🟠 (dipendente da codice commerciale non disponibile).
- **Dipendenze esterne (non commerciali)**: `SSO` (server LDAP/AD),
  `FILE_ATTACHMENTS` (storage), email/`LOW_STOCK_ALERTS` (SMTP) restano
  subordinate ai propri flag di configurazione (`ldap.enabled`, `STORAGE_TYPE`,
  `SMTP_*`). Abilitare l'entitlement **non** attiva il servizio esterno → nessun
  rischio di "accendere" una feature che il deployment non ha configurato.
- **Gate morti** (nessun enforcement): `PARTS_COST_TRACKING`, `ADVANCED_ANALYTICS`
  — inclusione innocua.
- `MULTI_INSTANCE` (creazione di più company nella stessa istanza) è l'unico
  entitlement la cui opportunità in self-hosted è una **scelta di prodotto**, non
  un problema tecnico: la feature è implementata e sicura, ma se il deployment
  aziendale deve restare mono-azienda si può valutare di escluderlo.

**Esito**: la policy "intero enum" è coerente con l'audit. **Nessuna modifica di
codice proposta.** Unico punto rimesso al responsabile tecnico:
`MULTI_INSTANCE` (vedi *Issues requiring decision*). Non modificato autonomamente.

---

## Frontend verification

Flusso verificato per ispezione:

```
LicenseService → LicensingState → GET /license/state
   → slices/license.ts (getLicenseValidity)
   → store.license.state
   → useLicenseEntitlement(entitlement)   [hooks/useLicenseEntitlement.ts]
   → feature UI
```

- `useLicenseEntitlement` usa realmente gli entitlement ricevuti dal backend:
  `license.valid && license.entitlements.some(e => e === entitlement)`.
- Esempi di feature precedentemente gated che si sbloccano quando
  `/license/state` diventa valido: `App.tsx` → `useLicenseEntitlement('BRANDING')`;
  `Settings/Integrations` → `useLicenseEntitlement('API_ACCESS')`;
  `WorkOrders/index` → `RESOURCE_PLANNING`.
- **Nessuna modifica frontend necessaria**: lo sblocco è interamente guidato dallo
  stato backend. Nessun bug frontend causato da MOD-001.
- Discrepanza pre-esistente (non MOD-001, non risolta qui):
  `frontend/src/models/owns/license.ts` usa `UNLIMITED_CHECKLIST` (singolare) vs
  enum backend `UNLIMITED_CHECKLISTS`, e omette alcuni entitlement. Non incide
  sullo sblocco (limiti enforced server-side). Vedi
  [01-license-entitlements.md](01-license-entitlements.md) §5.

---

## Secret verification

- La sola aggiunta di MOD-001 a `.env.example` è
  `LICENSING_SELF_HOSTED_MODE=false` (+ commento) → **nessun secret**.
- `application.yml` / `docker-compose.yml`: aggiunte solo interpolazioni env,
  nessun valore reale.
- **Nessun** secret introdotto da MOD-001 (verificato su `git diff`).
- **Nota (pre-esistente, non MOD-001)**: `.env.example` conteneva già valori di
  esempio `JWT_SECRET_KEY=sD1HBM6...`, `POSTGRES_PWD=mypassword`,
  `MINIO_PASSWORD=minio123`. Sono placeholder upstream. Come da istruzioni **non
  rimossi**; si segnala di rigenerarli per ogni deployment (in particolare
  `JWT_SECRET_KEY`).

---

## Persistence verification

**Persistence changes: none.** MOD-001 non aggiunge tabelle, migrazioni Liquibase,
entità, cache o volumi Docker. Lo stato self-hosted è costruito in memoria a ogni
chiamata (nessuna nuova persistenza).

---

## Findings

1. ✅ Compilazione reale OK (769+78 file, javac 17).
2. ✅ 5/5 test MOD-001 verdi; 1412/1412 test totali verdi (0 regressioni).
3. ✅ Keygen non contattato in self-hosted (test + analisi statica dei percorsi).
4. ✅ `LICENSING_SELF_HOSTED_MODE` cablato in Docker, default `false`.
5. ✅ `hasEntitlement()` invariato; nessun bypass di sicurezza.
6. ✅ Nessuna modifica a persistenza/frontend/secret.
7. ℹ️ Boot Docker end-to-end non eseguito (immagine pubblicata nella compose +
   stack multi-container); logica coperta dai test.
8. ℹ️ Secret di esempio pre-esistenti in `.env.example` (da rigenerare in prod).
9. ℹ️ Discrepanza naming entitlement frontend pre-esistente (fuori MOD-001).

## Issues requiring decision

- **`MULTI_INSTANCE` nella policy self-hosted.** Tecnicamente sicuro e implementato,
  ma consente di creare più aziende nella stessa istanza. Se il deployment interno
  deve restare mono-azienda, il responsabile tecnico può decidere di escluderlo
  dalla policy (modifica separata, non applicata autonomamente).

## Recommendation

**A → approvare MOD-001.**

L'implementazione è corretta, isolata, testata (build + 1412 test verdi), non
contatta Keygen in self-hosted, non regredisce il percorso commerciale e non
altera sicurezza/persistenza. Le uniche voci aperte sono una decisione di prodotto
(`MULTI_INSTANCE`) e note pre-esistenti non introdotte da MOD-001. Se in futuro si
vuole una prova live, buildare l'immagine dal `Dockerfile` locale ed eseguire il
boot con `LICENSING_SELF_HOSTED_MODE=true`.

L'attività di verifica termina qui. Il prossimo modulo non è deciso
autonomamente.
