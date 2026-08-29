# MOD-012 — Mobile Runtime Acceptance Test

Acceptance test **runtime** dell'app mobile ufficiale (React Native + Expo) contro
l'Atlas self-hosted locale. **Solo verifica: nessuna modifica** a mobile/frontend/
backend/licensing/API/Docker. F-04 non modificato. Secret mascherati (`REDACTED`).

`Code changes: NONE.`

> **Esito in una riga:** il test **GUI su device/emulatore NON è eseguibile in questo
> ambiente** (headless Windows CLI: nessun adb/Android SDK/emulatore/Expo; iOS richiede
> macOS) → Android/iOS interattivo = **NOT TESTED**. Tutto ciò che è verificabile senza
> device — contratto mobile↔backend e **F-04 mobile impact** — è stato verificato:
> **F-04 mobile impact = NONE OBSERVED**.

---

## 1. Objective

Dimostrare a runtime che l'app mobile ufficiale può usare il nostro backend self-hosted
(server URL custom, auth, dati, asset, work order, allegati, licensing non-Cloud) e
determinare l'**impatto mobile di F-04**.

## 2. Environment

```text
OS host:            Windows 11 (MINGW64 shell), headless CLI
Device/emulator:    NONE (no adb, no Android SDK, no emulator, no Expo CLI)
Android version:    N/A (no device/emulator available)
iOS version:        N/A (iOS simulator requires macOS + Xcode — impossible on Windows)
Atlas commit:       e1d24406 (backend built from source, incl. MOD-011 F-01 fix)
Backend endpoint:   http://localhost:3000/api  (official docker-compose stack, local)
Frontend endpoint:  http://localhost:3000
```

Probe eseguito: `adb`, `emulator`, Android SDK dir, `expo` → **tutti assenti**. Nessun
uso di `websrv01`/production.

## 3. Version Tested

Mobile app: Expo/React Native, `app.config.ts` version **1.0.47** (Android
`com.atlas.cmms`, iOS `com.cmms.atlas`), codebase JS condivisa. Backend: `atlas-cmms-backend:local`
(sorgenti, SELF_HOSTED).

## 4. Server Configuration

TEST: formato URL server supportato dal codice mobile · ACTUAL: `config.ts:getApiUrl()`
usa `customApiUrl` (AsyncStorage) impostato dalla schermata `CustomServerScreen`
(route `CustomServer`, raggiungibile da `LoginScreen`), placeholder
`https://your-server-url.com`; l'URL viene normalizzato con trailing `/` e usato come base
da `utils/api.ts`. Per un device reale sarebbe `http://<HOST_LAN_IP>:3000/api`.
RESULT: **verificato da codice (MOD-009)**; impostazione GUI su device **NOT TESTED**.

## 5. Connectivity

Il device/emulatore non è disponibile → la connessione GUI non è stata stabilita.
Verifica sostitutiva **a livello di protocollo** (non-GUI): le richieste con la stessa
base URL/endpoint dell'app raggiungono il backend locale (vedi §15). RESULT (GUI):
**NOT TESTED — prerequisito ambiente mancante**.

## 6. Authentication

L'app usa `/auth/signin` (stesso endpoint del backend). Verifica protocol-level:
`signin` (type=client) → **200** contro il backend locale. Login/Logout/re-login/credenziali
errate via GUI: **NOT TESTED** (no device). Il flusso backend equivalente è già PASS in
MOD-010 (login, logout→token invalidato, re-login). `Authorization: REDACTED`.

## 7. Organization / Company

Isolamento company enforced dal backend (`CompanyAudit.@PostLoad`), verificato a runtime
in MOD-005/010 (cross-company → 403). Verifica GUI mobile con due utenti: **NOT TESTED**
(no device). Nessun contesto company gestito lato client oltre a ciò che il backend
restituisce.

## 8. Assets

- Da codice (`EditAssetScreen.tsx` + `slices/asset.ts:editAsset`): l'edit inizializza il
  form con `{...asset}` (**intero** asset caricato dal backend) e su submit invia
  `formatAssetValues(values)` via `api.patch('assets/{id}', ...)`.
- `formatAssetValues` (`utils/fields.ts:349`) fa `{...values}` e trasforma **solo** i campi
  relazione (location/category/primaryUser/parentAsset/customers/vendors/assignedTo/teams/
  parts) in id; **non rimuove** `name` né `status`.

TEST: shape della PATCH asset dell'app · EXPECTED: DTO completo (name+status presenti) ·
ACTUAL: DTO completo (spread dell'asset, relazioni→id). Verifica protocol-level (replay
della stessa shape contro il backend live): `PATCH /assets/1` con `{name, status,
relazioni:null/[]}` → **200**. RESULT: **PASS (protocol-level)**; edit via GUI: NOT TESTED.

## 9. Work Orders

L'app consuma gli endpoint work-order del backend (stessa API). Flussi backend
(lista/dettaglio/creazione/patch/ricerca) già PASS in MOD-010. Verifica GUI mobile:
**NOT TESTED** (no device). Nessun 401/403/404/409/422/500 inatteso osservabile senza GUI.

## 10. Attachments

L'app: upload via API (`uploadFiles`, `/files/upload`), download via
`FileSystem.downloadAsync(uri)` (programmatico, disposition-agnostico), immagini inline.
TEST: contratto allegati mobile contro backend live · ACTUAL (protocol-level): upload
immagine → **200**; download programmatico dal presigned URL → **200**. La mitigazione
MOD-004B (`Content-Disposition: attachment` sui non-immagine) è **trasparente** al download
programmatico. RESULT: **PASS (protocol-level)**; view/download via GUI: NOT TESTED.

## 11. QR / Barcode

Richiede fotocamera del dispositivo (`expo-camera`/NFC). **NOT TESTED** (no device).

## 12. Offline

Codice (MOD-009): `redux-persist` (cache locale su AsyncStorage) + `netinfo` (rilevazione
rete); nessuna dipendenza Cloud per il caching. Read offline via GUI: **NOT TESTED** (no
device). Write-sync offline: **NOT VERIFIED** (non evidente nel codice; nessun device).

## 13. Push Notifications

Richiede Firebase/FCM + registrazione device. Nessuna configurazione Firebase presente in
locale. **NOT TESTED — environment prerequisite missing** (non un blocker automatico).

## 14. Licensing

Da codice (MOD-009): `slices/license.ts` → `GET /api/license/state` (backend-driven);
nessun Keygen/Paddle/subscription/upgrade; `useLicenseEntitlement`/`hasFeature` aperti in
self-hosted (backend concede l'intero enum + BUSINESS). Nessuno schermo di upgrade/blocco
commerciale nel percorso. RESULT: **PASS** (nessun blocco commerciale mobile).

## 15. API Evidence (protocol-level, non-GUI)

Richieste con la stessa base URL/endpoint dell'app, contro il backend locale
(`Authorization: REDACTED`):

```text
POST /api/auth/signin (type=client)      → 200
GET  /api/assets/1                        → 200   (full AssetShowDTO: name, status)
PATCH /api/assets/1  {name,status,rel…}   → 200   (mobile full-DTO shape; F-04 not triggered)
POST /api/files/upload (IMAGE)            → 200
GET  <presigned storage URL>              → 200   (programmatic download)
```

Queste **non** sono un test della GUI mobile: sono la verifica che la **forma delle
richieste** costruita dal codice mobile è accettata dal nostro backend self-hosted.

## 16. F-04 Verification

F-04 = una PATCH parziale che omette un campo `@NotNull` (es. `name`) → 500 (mapper
MapStruct SET_NULL). Determinazione per l'app ufficiale:

- **Da codice:** `EditAssetScreen` invia `{...asset}` (asset completo) → `formatAssetValues`
  mantiene `name` e `status` → l'app invia un **DTO completo**, **non** omette `name`.
- **Empirica (protocol-level):** replay della shape mobile (full DTO) → `PATCH /assets/1`
  → **200** (nessun `ConstraintViolationException`).

**Caso A** → `F-04 mobile impact: NONE OBSERVED`. L'app ufficiale non genera PATCH che
omettono campi obbligatori. F-04 **non modificato** (come da §11/§20).

## 17. Android Results

**NOT TESTED — environment prerequisite missing.** Nessun adb/Android SDK/emulatore/Expo
CLI e nessun device fisico in questo ambiente headless; impossibile avviare/pilotare una
app Expo GUI. Il contratto mobile↔backend Android (JS condiviso) è verificato a livello di
codice (MOD-009) e di protocollo (§15).

## 18. iOS Results

**NOT TESTED — impossibile sull'host.** Il simulatore iOS richiede macOS + Xcode; l'host è
Windows. Codebase JS condivisa con Android → stesse conclusioni contract-level.

## 19. Findings

Nessun difetto di prodotto (P0/P1/P2/P3 = 0). Unico elemento:

| ID | Tipo | Descrizione |
|---|---|---|
| M12-ENV | Limitazione ambiente (non un bug) | Test GUI runtime su device/emulatore non eseguibile qui (no adb/SDK/emulatore/Expo; iOS richiede macOS). Raccomandato un pass su **device reale** prima del go-live production. |

F-04 mobile impact: **NONE OBSERVED** (nessuna azione).

## 20. Remaining Issues

- **Device runtime pass** (Android reale + iOS su macOS): da eseguire prima del deploy
  production per confermare GUI, QR/barcode, offline read, push (con Firebase).
- **DA VERIFICARE** ereditati: push FCM (Firebase backend), offline write-sync,
  restore distruttivo del backup. Invariati.
- **F-04**: pre-esistente, **nessun impatto mobile** → non urgente; eventuale fix resta una
  decisione separata (mapper null-strategy).

## 21. Runtime Matrix

| Test | Android | iOS | Result |
|---|---|---|---|
| Custom Server | NOT TESTED | NOT TESTED | code-verified (MOD-009) |
| Connection | NOT TESTED | NOT TESTED | protocol-level PASS (§15) |
| Login | NOT TESTED | NOT TESTED | protocol-level PASS (signin 200) |
| Logout | NOT TESTED | NOT TESTED | backend PASS (MOD-010) |
| Assets | NOT TESTED | NOT TESTED | protocol-level PASS (full-DTO PATCH 200) |
| Work Orders | NOT TESTED | NOT TESTED | backend PASS (MOD-010) |
| Attachments | NOT TESTED | NOT TESTED | protocol-level PASS (upload/download 200) |
| QR/Barcode | NOT TESTED | NOT TESTED | no camera/device |
| Offline read | NOT TESTED | NOT TESTED | code: redux-persist |
| Offline write-sync | NOT VERIFIED | NOT VERIFIED | not evident in code |
| Push | NOT TESTED | NOT TESTED | no Firebase |
| Licensing | PASS | PASS | backend-driven, no commercial gate |

## 22. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
Android: NOT TESTED (no device/emulator/SDK; environment prerequisite missing)
iOS: NOT TESTED (requires macOS + Xcode; impossible on Windows host)
Mobile licensing: PASS (no commercial gate; backend-driven)
F-04 mobile impact: NONE OBSERVED
Attachments: PASS (protocol-level; device GUI NOT TESTED)
Offline: NOT TESTED (no device)
Push: NOT TESTED (no Firebase config)
P0: 0   P1: 0   P2: 0   P3: 0
Mobile status: PASS WITH FINDINGS
Final verdict: PASS WITH FINDINGS
```

**Mobile status: PASS WITH FINDINGS.** Tutto ciò che è verificabile senza device è PASS
(contratto mobile↔backend a livello di codice e di protocollo; licensing non-Cloud;
allegati; **F-04 mobile impact = NONE OBSERVED**). L'unico "finding" è la **limitazione di
ambiente**: la GUI runtime su device/emulatore (e iOS su macOS) non è eseguibile qui →
si raccomanda un pass su device reale prima del deployment production.

⏹️ **STOP** — non modifico mobile/frontend/backend, non correggo F-04, non tocco licensing/
Docker/production, non faccio deployment live/Caddy/DNS, non avvio MOD-013. La decisione
successiva spetta al responsabile tecnico.
