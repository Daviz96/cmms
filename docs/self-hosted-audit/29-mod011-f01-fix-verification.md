# MOD-011 — F-01 Fix & Verification

Correzione mirata del finding **F-01** (MOD-010): `AssetService.patch` andava in HTTP 500
su una PATCH parziale priva di `status`. Fix minimale + test di regressione + verifica
runtime. Nessuna modifica a licensing/authorization/multi-tenancy/frontend/mobile/
schema/API contract. Secret mascherati.

```text
Before:  PATCH /api/assets/{id} {name}          → 500 (NullPointerException)
After:   PATCH /api/assets/{id} {name}          → 200, status unchanged
         PATCH /api/assets/{id} {name,status}   → 200, status updated (invariato)
```

---

## 1. Objective

Impedire l'NPE su PATCH senza `status`, mantenendo invariato il comportamento quando
`status` è presente e lasciando `status` invariato quando non fornito. Aggiungere un test
di regressione. Modifica minima e locale.

## 2. Finding F-01

Da MOD-010 (doc 28): `PATCH {name}` → **500**; `PATCH {name,status}` → **200**. Causa
indicata: `AssetService.java:419` `getStatus().isReallyDown()` con `status == null`.
Severità **P3**, **pre-esistente** (non introdotto dalle modifiche dei MOD).

## 3. Root Cause

In `AssetService.patch`, il blocco che gestisce le transizioni di **downtime** dereferenzia
lo `status` **in ingresso**:

```java
if (!asset.getStatus().isReallyDown() && savedAsset.getStatus().isReallyDown()) { ... }
else if (asset.getStatus().isReallyDown() && ...) { ... }
```

Se la PATCH non include `status` (`AssetPatchDTO.status == null`, campo `AssetStatus`
nullable), `asset.getStatus()` è null → **NullPointerException** → HTTP 500. Semantica
attesa quando `status` non è fornito: **nessuna transizione** e **status invariato**.

## 4. Implementation

Fix **locale al punto del dereference** (`AssetService.patch`): se `status` non è fornito,
lo si **default-a allo status corrente** dell'asset, così il blocco di transizione non va
in NPE, non innesca alcuna transizione (i due valori coincidono) e il mapper riscrive lo
stesso status (invariato):

```java
if (savedAsset.canBeEditedBy(user)) {
    // MOD-011 (F-01): a partial PATCH may omit status; default it to the asset's
    // current status so the downtime-transition check below does not NPE and the
    // status is left unchanged when the caller did not send it.
    if (asset.getStatus() == null) {
        asset.setStatus(savedAsset.getStatus());
    }
    if (!asset.getStatus().isReallyDown() && savedAsset.getStatus().isReallyDown()) { ... }
    ...
```

`AssetService` **non** è stato riscritto; nessuna modifica a DTO/mapper/API contract,
licensing, authorization, multi-tenancy. +6 righe.

## 5. Tests Added

1 test di regressione in `AssetServiceTest` (classe `@Nested Patch`), coerente col pattern
esistente:

- **`patchWithoutStatus_doesNotFailAndKeepsStatus`**: DTO con `status` **null** (solo
  `name`) → `assertDoesNotThrow`; verifica `result.getStatus() == OPERATIONAL` (invariato),
  `dto.getStatus() == OPERATIONAL` (defaultato → nessuna transizione),
  `assetDowntimeService.create` **mai** invocato, `saveAndFlush` invocato.

I test esistenti coprono già il caso **con** status (Test B): `transitionToDown_triggersDowntime`,
`transitionFromDown_stopsDowntime`, `statusChange_dispatchesStatusChangeWebhook` — restano verdi.

## 6. Test Results

| Comando | Esito |
|---|---|
| `mvnw test -Dtest=AssetServiceTest` | **118 test, 0 fail, 0 err** — BUILD SUCCESS |
| `mvnw test` (suite completa) | **1446 test, 0 fail, 0 err, 0 skip** — BUILD SUCCESS |

Baseline 1445 → **1446** (+1 = nuovo test di regressione). Nessun test preesistente rotto.

## 7. Runtime Verification

Immagine ricostruita **con il fix** (`docker compose build api`, provenienza già nota) e
stack ufficiale avviato in locale (riuso volumi persistiti). Asset esistente id=1
(status OPERATIONAL).

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| `PATCH {name}` (F-01 repro, no status) | 200, status invariato | **200**, `status=OPERATIONAL` (invariato), name aggiornato | **PASS** |
| `PATCH {name,status:DOWN}` | 200, status aggiornato | 200, `status=DOWN` | PASS |
| `PATCH {name}` con asset DOWN | 200, status resta DOWN | 200, `status=DOWN` (invariato) | PASS |

F-01 **risolto e riprodotto a runtime**.

## 8. Regression Verification

- Suite completa 1446/0/0/0 (nessuna regressione).
- Aree critiche non toccate dal fix: licensing, authentication, company isolation, Asset
  create/read/search, persistence — la modifica è confinata al blocco di transizione
  status dentro `canBeEditedBy`.

## 9. Security Verification

Il fix è **dentro** il ramo `canBeEditedBy(user)` e non altera l'autorizzazione.
Verifica runtime: utente di **CoB** che tenta `PATCH /api/assets/1` (asset di **CoA**) →
**403**. Nessun bypass introdotto; test di isolamento esistenti verdi (suite completa).

## 10. Files Changed

```text
api/src/main/java/com/grash/service/AssetService.java          (+6)   fix F-01
api/src/test/java/com/grash/service/AssetServiceTest.java      (+1 test) regression
```

Nessun altro file applicativo modificato. (Doc: questo report + `CLAUDE.md`.)

## 11. Remaining Issues

- **F-04 (NEW, P3, pre-esistente, OUT OF SCOPE):** durante la verifica runtime è emerso
  che una PATCH parziale che **omette `name`** (es. `{status:"OPERATIONAL"}` da solo) →
  **500 `ConstraintViolationException: name must not be null`** (`AssetService.update:139`).
  Causa: il mapper `AssetMapper.updateAsset` usa la strategia MapStruct **SET_NULL**, quindi
  i campi non inviati vengono azzerati; se si omette un campo `@NotNull` (es. `name`), la
  validazione fallisce. È un problema **più ampio** della semantica di partial-patch, **non**
  è F-01, **non** è introdotto da questo fix (comportamento identico prima di MOD-011), e in
  uso normale non si manifesta perché la UI web/mobile invia il DTO completo. Un'eventuale
  correzione (es. `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)`) toccherebbe la
  semantica di TUTTI i campi del mapper → **fuori scope MOD-011, richiede decisione** (§11).
  **Documentato, non corretto.**
- **DA VERIFICARE** ereditati da MOD-010 (mobile runtime, push FCM, offline write-sync,
  restore distruttivo) — invariati.

## 12. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: F-01 marcato RESOLVED (rimosso dai problemi aperti); aggiunto MOD-011 a Current
Project State + Documentation Map/Workflow; registrato il nuovo finding F-04 (partial-patch
omitting @NotNull → 500, mapper SET_NULL, pre-esistente, out of scope) in Known Issues;
baseline test aggiornata a 1446.
```

## 13. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: AssetService.java (+6, null-safe status default), AssetServiceTest.java (+1 regression test)
F-01 status: RESOLVED
Tests added: 1
Tests passed: 1446 (full suite) / 118 (AssetServiceTest)
Tests failed: 0
Runtime verification: PASS
Security regression: PASS
Final verdict: PASS
```

F-01 è **RESOLVED**: PATCH parziale senza `status` non genera più 500, lo `status` resta
invariato, e il comportamento con `status` presente è preservato. Il fix è minimale (+6
righe), coperto da test unitario e verificato a runtime, senza regressioni né bypass di
sicurezza. È stato inoltre documentato (non corretto) un finding pre-esistente più ampio
(F-04) sulla semantica di partial-patch.

⏹️ **STOP** — non eseguo deployment live, non modifico `websrv01`/Caddy/DNS/certificati,
non correggo F-04 (fuori scope, richiede decisione), non avvio MOD-012. La decisione sul
prossimo passo spetta al responsabile tecnico dopo la revisione di questo report.
