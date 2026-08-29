# 15 — MOD-002 Verification

Verifica funzionale: dimostrare che in modalità self-hosted i **limiti numerici
commerciali** sono già disattivati dagli entitlement `UNLIMITED_*` introdotti da
MOD-001, **senza modifiche al codice applicativo**.

Ambiente: Windows 11, JDK 17 Temurin 17.0.20.1 (portable), Maven 3.8.6 (wrapper),
Docker 29.4.3. Data: 2026-08-25.

---

## Stato

**PASS**

I limiti commerciali sono bypassati esclusivamente tramite
`hasEntitlement(UNLIMITED_*)` reso `true` da MOD-001. Nessuna modifica al codice
applicativo. Build + 1430 test verdi. Un chiarimento sui nomi degli entitlement e
lo stato di `MULTI_INSTANCE` (invariato) tra i findings.

---

## Limiti analizzati

Tutti e 8 i limiti free-tier sono definiti in
[`utils/Consts.java`](../../api/src/main/java/com/grash/utils/Consts.java#L52)
(`usageBasedFreeLimits`) e applicati con lo **stesso identico pattern**:

```java
if (!licenseService.hasEntitlement(UNLIMITED_X) && repository.hasMoreThan(companyId, threshold-1))
    throw new CustomException(... "Free Limit reached" ...);
```

Quando `hasEntitlement(UNLIMITED_X)` è `true`, l'operatore `&&` va in
**short-circuit**: la query di conteggio non viene eseguita e l'eccezione non
viene lanciata → limite **bypassato**.

| Feature | Entitlement (nome reale) | Limite free | Service | Metodo | Bypass self-hosted |
|---|---|---:|---|---|---|
| Assets | `UNLIMITED_ASSETS` | 50 | `AssetService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |
| Users | `UNLIMITED_USERS` | 5 | `UserService` | `checkUsageBasedLimit(int)` (public) | ✅ sì |
| Locations | `UNLIMITED_LOCATIONS` | 10 | `LocationService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |
| Parts | `UNLIMITED_PARTS` | 100 | `PartService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |
| PM | `UNLIMITED_PM_SCHEDULES` | 10 | `PreventiveMaintenanceService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |
| Work Orders | `UNLIMITED_ACTIVE_WORK_ORDERS` | 30 | `WorkOrderService` | `checkUsageBasedLimit(Company)` (package-private) | ✅ sì |
| Checklists | `UNLIMITED_CHECKLISTS` | 10 | `ChecklistService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |
| Meters | `UNLIMITED_METERS` | 10 | `MeterService` | `checkUsageBasedLimit(Company)` (private) | ✅ sì |

> **Nota nomi (importante):** il brief MOD-002 elenca
> `UNLIMITED_PREVENTIVE_MAINTENANCE` e `UNLIMITED_WORK_ORDERS`. Nel repository i
> nomi reali sono **`UNLIMITED_PM_SCHEDULES`** e
> **`UNLIMITED_ACTIVE_WORK_ORDERS`**. Documentato, non "corretto" arbitrariamente.

---

## Asset limits

`AssetService.checkUsageBasedLimit(Company)`
([riga 150](../../api/src/main/java/com/grash/service/AssetService.java#L150)):
gate `UNLIMITED_ASSETS`, free 50, conteggio `assetRepository.hasMoreThan`.
Bypass self-hosted: ✅.

## User limits

`UserService.checkUsageBasedLimit(int)`
([riga 134](../../api/src/main/java/com/grash/service/UserService.java#L134)):
gate `UNLIMITED_USERS`, free 5, conteggio `userRepository.hasMorePaidUsersThan`.
È l'unico che legge anche `licensingState.getUsersCount()` (usato solo se
`hasLicense` e **solo** dentro il ramo `!hasEntitlement(...)`, quindi mai
raggiunto in self-hosted). Bypass self-hosted: ✅.

## Location limits

`LocationService.checkUsageBasedLimit(Company)`
([riga 91](../../api/src/main/java/com/grash/service/LocationService.java#L91)):
gate `UNLIMITED_LOCATIONS`, free 10. Bypass self-hosted: ✅.

## Part limits

`PartService.checkUsageBasedLimit(Company)`
([riga 131](../../api/src/main/java/com/grash/service/PartService.java#L131)):
gate `UNLIMITED_PARTS`, free 100. Bypass self-hosted: ✅.

## Preventive Maintenance limits

`PreventiveMaintenanceService.checkUsageBasedLimit(Company)`
([riga 171](../../api/src/main/java/com/grash/service/PreventiveMaintenanceService.java#L171)):
gate `UNLIMITED_PM_SCHEDULES`, free 10. Bypass self-hosted: ✅.

## Work Order limits

`WorkOrderService.checkUsageBasedLimit(Company)`
([riga 183](../../api/src/main/java/com/grash/service/WorkOrderService.java#L183)):
gate `UNLIMITED_ACTIVE_WORK_ORDERS`, free 30 (work order **attivi/incompleti**),
conteggio `workOrderRepository.hasMoreActiveThan`. Bypass self-hosted: ✅.

## Checklist limits

`ChecklistService.checkUsageBasedLimit(Company)`
([riga 71](../../api/src/main/java/com/grash/service/ChecklistService.java#L71)):
gate `UNLIMITED_CHECKLISTS`, free 10. Usato come target del test sul **codice
reale** del limite (vedi *Test results*). Bypass self-hosted: ✅.

> Nota minore (pre-esistente, non MOD-002): il confronto usa
> `hasMoreThan(companyId, limit.longValue())` **senza** il `-1` presente negli
> altri service (`threshold-1`). È una differenza di off-by-one sul confine del
> free-tier, non incide sul bypass (il gate `hasEntitlement` viene valutato per
> primo). Non modificato.

## Meter limits

`MeterService.checkUsageBasedLimit(Company)`
([riga 68](../../api/src/main/java/com/grash/service/MeterService.java#L68)):
gate `UNLIMITED_METERS`, free 10. Bypass self-hosted: ✅.

---

## Commercial mode verification

Con `LICENSING_SELF_HOSTED_MODE=false` e nessuna licenza:

- `hasEntitlement(UNLIMITED_*)` = `false` per tutti gli 8 (test parametrico
  `commercialWithoutLicense_deniesEveryUnlimitedEntitlement`, real
  `LicenseService`).
- Il limite viene **applicato**: test sul codice reale
  `commercialMode_enforcesChecklistFreeLimit_whenExceeded` — con entitlement
  assente e conteggio oltre il limite, `checkUsageBasedLimit` lancia
  `CustomException`.
- Copertura di regressione ulteriore: l'intera suite (1430 test) gira in modalità
  commerciale di default e passa → il comportamento free-tier commerciale è
  intatto.

## Self-hosted mode verification

Con `LICENSING_SELF_HOSTED_MODE=true`:

- `hasEntitlement(UNLIMITED_*)` = `true` per tutti gli 8 (test parametrico
  `selfHosted_grantsEveryUnlimitedEntitlement`, real `LicenseService`).
- Il limite **non** viene applicato anche a conteggio oltre soglia: test sul
  codice reale `selfHostedMode_bypassesChecklistLimit_evenWhenExceeded` —
  `checkUsageBasedLimit` non lancia, e la query di conteggio
  (`checklistRepository.hasMoreThan`) **non viene mai chiamata**
  (`verify(..., never())`), a conferma dello short-circuit.

Questo distingue "licensing sbloccato" da "feature realmente utilizzabile":
l'operazione oltre il limite free è effettivamente permessa.

---

## Test results

Nuovo file di test:
[`api/.../service/SelfHostedUsageLimitsTest.java`](../../api/src/test/java/com/grash/service/SelfHostedUsageLimitsTest.java)
(JUnit 5 + Mockito, parametrico + reflection sul metodo reale del limite).

Test dedicato:

```
[INFO] Running com.grash.service.SelfHostedUsageLimitsTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

18 test = 8 (self-hosted, tutti gli `UNLIMITED_*`) + 8 (commerciale, tutti negati)
+ 2 (codice reale del limite Checklist: enforcement commerciale / bypass
self-hosted).

Suite completa (regressione):

```
[INFO] Tests run: 1430, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Confronto con il baseline MOD-001 (**1412**): **+18** test, tutti e soli quelli
aggiunti da MOD-002. **0 fallimenti, 0 errori, 0 skip.** Nessun test preesistente
modificato o rotto.

| | Baseline (MOD-001) | Ora (MOD-002) | Δ |
|---|---:|---:|---:|
| Test totali | 1412 | 1430 | +18 |
| Failures / Errors / Skipped | 0 / 0 / 0 | 0 / 0 / 0 | — |

---

## Docker runtime verification

**Non eseguita** (build immagine locale + creazione di >50 record via API
autenticata). Motivazione e copertura equivalente:

- Il bypass è dimostrato sul **codice reale** del limite (test Checklist:
  enforcement in commerciale, bypass in self-hosted con verifica dello
  short-circuit), oltre che sul lato licensing (parametrico sugli 8 entitlement).
- Un boot live richiederebbe stack multi-container (Postgres + MinIO), immagine
  buildata dal `Dockerfile` locale, flusso di autenticazione e creazione massiva
  di record — sproporzionato rispetto all'evidenza già ottenuta a livello di
  codice.
- `docker compose config` (verificato in MOD-001) conferma già che
  `LICENSING_SELF_HOSTED_MODE` è iniettato nel container API.

Procedura per una prova live futura: buildare l'immagine dal `Dockerfile` locale,
avviare con `LICENSING_SELF_HOSTED_MODE=true`, autenticarsi e creare asset oltre
50 → l'operazione deve riuscire (nessun HTTP 403 "Free Limit reached").

---

## MULTI_INSTANCE status

```
MULTI_INSTANCE: enabled
```

Gate a [`UserService:190`](../../api/src/main/java/com/grash/service/UserService.java#L190):
`!hasEntitlement(MULTI_INSTANCE) && companyService.existsAtLeastOneWithMinWorkOrders()`.
In self-hosted l'entitlement è concesso → è possibile creare più aziende nella
stessa istanza.

**Non è un limite numerico d'uso** e **non è stato modificato in MOD-002.** È la
stessa **decisione di prodotto** già segnalata nel report MOD-001
([14-mod001-verification.md](14-mod001-verification.md), *Issues requiring
decision*): la scelta se mantenerlo abilitato spetta al responsabile tecnico ed è
separata da MOD-002.

---

## Findings

1. ✅ Tutti gli 8 limiti free-tier usano `hasEntitlement(UNLIMITED_*)` con lo
   stesso pattern di short-circuit → bypassati automaticamente da MOD-001.
2. ✅ Test sul codice reale del limite (Checklist): enforcement in commerciale,
   bypass in self-hosted con query di conteggio mai eseguita.
3. ✅ 18/18 test dedicati verdi; 1430/1430 totali (+18 vs baseline), 0 regressioni.
4. ✅ Nessun `return true`, nessuna modifica a `usageBasedFreeLimits`, nessun
   `if (selfHosted)` nei service. Il flusso resta
   `LicenseService → LicensingState → hasEntitlement()`.
5. ℹ️ **Nomi entitlement**: `UNLIMITED_PM_SCHEDULES` / `UNLIMITED_ACTIVE_WORK_ORDERS`
   (reali) ≠ `UNLIMITED_PREVENTIVE_MAINTENANCE` / `UNLIMITED_WORK_ORDERS` (brief).
6. ℹ️ **Limiti non commerciali** correttamente esclusi da MOD-002: validazione
   `Subscription.setUsersCount` (min 1), rate limiting (`RateLimiterService`),
   vincoli DB/validazione. Non legati agli entitlement, non toccati.
7. ℹ️ Off-by-one pre-esistente in `ChecklistService` (`hasMoreThan` senza `-1`):
   segnalato, fuori scope MOD-002.
8. ℹ️ `MULTI_INSTANCE: enabled` — decisione di prodotto, invariata.

---

## Code changes

**None** (codice applicativo).

- Nessuna modifica a service, controller, `Consts.usageBasedFreeLimits`,
  `LicenseService` o configurazione rispetto a MOD-001.
- Unica aggiunta: il file di test
  `api/src/test/java/com/grash/service/SelfHostedUsageLimitsTest.java`.
- `git status`: le modifiche applicative tracciate restano quelle di MOD-001
  (LicenseService +59, application.yml +3, docker-compose.yml +1, .env.example +2).

---

## Recommendation

**MOD-002 non richiede modifiche al codice.** I limiti commerciali numerici sono
già disattivati dagli entitlement `UNLIMITED_*` introdotti da MOD-001, come
dimostrato dai test sul codice reale e dalla suite completa (1430/0/0/0).

L'attività di verifica termina qui. Come da sezione 17 del brief, **non** si
procede con MOD-003/004/005/006: la decisione del prossimo passo spetta al
responsabile tecnico.
