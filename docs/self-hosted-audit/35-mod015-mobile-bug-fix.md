# MOD-015 — Mobile Bug Fix & Regression Verification

Correzione del bug mobile **M-BUG-1** (emerso in MOD-014B): `TypeError: Cannot read property
'endsWith' of undefined` in `mobile/slices/instanceConfig.ts` (origine `mobile/config.ts`).
Ciclo completo REPRODUCE → ROOT CAUSE → FIX → BUILD → INSTALL → RETEST → REGRESSION.
**Solo codice mobile modificato**; nessuna modifica a backend/frontend/licensing/API/DB/
Docker production/Caddy/DNS. Secret non riportati (nessun `google-services.json`/token/password).

```text
Backend changes: NONE
Production changes: NONE
Application behavior changes: getApiUrl() null-safe; getInstanceConfig skips fetch when no server configured
```

> **Esito in una riga:** M-BUG-1 **riprodotto**, **root cause** identificata (URL `undefined`
> dereferenziato), **fix minimale** in 2 file (+15 righe), **release APK reale** buildato
> (JS bundled, no Metro), installato, e a runtime: fresh launch **senza errore** + **regression
> completa PASS** (Custom Server → Login → Dashboard → Work Orders → Assets → Settings → Logout,
> nessun toast di errore). → **M-BUG-1 FIXED + VERIFIED.**

---

## 1. Objective

Riprodurre, comprendere e correggere M-BUG-1 in modo minimale e coerente con l'architettura,
verificando con un build reale + installazione + runtime + regression che l'errore sia assente.

## 2. Scope

Consentite le modifiche al **codice mobile**. Vietato modificare backend/frontend/licensing/
API/DB/production/Caddy/DNS. Nessuna nuova dipendenza. Nessun refactoring generale.

## 3. M-BUG-1

### 3.1 Reproduction

| Campo | Valore |
|---|---|
| Ambiente | AVD `atlas_test` (Android API 35 x86_64, headless WHPX) |
| Build type | dev/debug (Metro) per la ripro; confermato anche in release |
| App version | 1.0.47 (`com.atlas.cmms`) |
| Server URL | **nessuno** (fresh install, `pm clear`, nessun Custom Server) |
| Passi | `adb shell pm clear com.atlas.cmms` → launch app → LoginScreen mount |
| Expected | schermata Login pulita |
| Actual | toast **"Failed to fetch instance config: TypeError: Cannot read property 'endsWith' of undefined"** |
| Evidenza | screenshot `REPRO-bug.png`; testo UIAutomator con l'errore |

### 3.2 Root Cause

```text
Root cause:  getApiUrl() dereferenzia un URL undefined con .endsWith()
Affected code: mobile/config.ts → getApiUrl(); consumato da getInstanceConfig() via api.get('instance-config')
Input/value: rawApiUrl = customUrl (null) || defaultApiUrl (undefined) = undefined
Why undefined: il dev build non "baka" API_URL (extra.API_URL = process.env.API_URL, non impostato)
               e, prima che l'utente configuri il Custom Server, customApiUrl (AsyncStorage) è null
Why current code fails: `undefined.endsWith('/')` → TypeError (crash sincrono dentro getApiUrl)
Why proposed fix is correct: si rende getApiUrl null-safe (ritorna '' se nessun URL, senza valori
               arbitrari) e si evita di chiamare l'endpoint quando non c'è server → nessun crash e
               nessun toast spurio; quando il server è configurato, getApiUrl ritorna l'URL e il
               flusso funziona (backend `/instance-config` risponde 200).
```

Dettaglio innesco: `getInstanceConfig` è dispatchato in `LoginScreen.tsx:32`
(`useEffect(... , [])`) **all'avvio**, prima che il server sia configurato → il crash avviene
a startup. Il `try/catch` di `getInstanceConfig` cattura l'eccezione e fa
`console.error('Failed to fetch instance config:', error)` → toast/LogBox.

### 3.3 Fix

Due modifiche minime e locali (nessuna dipendenza, nessun valore hardcoded):

1. **`mobile/config.ts` (+6)** — `getApiUrl()` null-safe in entrambi i rami (try e catch):

```ts
const rawApiUrl = customUrl || defaultApiUrl;
// MOD-015 (M-BUG-1): guard against an unset URL … Return '' instead of
// calling .endsWith() on undefined (which threw a TypeError at startup).
if (!rawApiUrl) return '';
return rawApiUrl.endsWith('/') ? rawApiUrl : rawApiUrl + '/';
```

2. **`mobile/slices/instanceConfig.ts` (+9)** — salta il fetch quando non c'è server
   configurato (evita un toast di errore di rete spurio a startup):

```ts
import { getApiUrl } from '../config';
...
const apiUrl = await getApiUrl();
if (!apiUrl) {
  return; // nessun server configurato ancora → non chiamare l'endpoint
}
const response = await api.get<{ ldapEnabled: boolean }>('instance-config', { headers: await authHeader(true) });
```

Totale: **2 file, +15 righe**. Nessuna modifica ad API/DTO/backend. Prettier: clean; `tsc
--noEmit`: **nessun errore** sui file modificati.

### 3.4 Verification

Build **reale release** (`gradlew assembleRelease`, JS **bundled** via Hermes → nessuna
dipendenza da Metro), installato pulito sull'AVD:

| TEST | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|
| Fresh launch (no server) | Login pulito, nessun errore | Login screen, **nessun** toast "Failed to fetch instance config"/"endsWith" (verificato via UIAutomator + screenshot `FIX-02.png`) | **PASS** |

Confronto: **prima** (ripro) toast TypeError → **dopo** (fix) schermata pulita.

### 3.5 Regression

Sequenza completa sull'APK release (account di test, password non riportata):

| Step | ACTUAL | Errore? | RESULT |
|---|---|---|---|
| Launch (fresh) | Login screen pulito | no | PASS |
| Custom Server | `http://10.0.2.2:3000/api` salvato → torna a Login pulito | no | PASS |
| Login | `atlastest@example.com` → dashboard "Atlas" (Open/On Hold/… = 0) | **no toast** (in MOD-014B c'era) | PASS |
| Work Orders | lista caricata ("No elements match this criteria") | no | PASS |
| Assets | schermata caricata (Search) | no | PASS |
| Settings | mostra `atlastest@example.com`, Sign out, Version 1.0.47 | no | PASS |
| Logout | dialog conferma → Sign out → **Login screen** | no | PASS |

Autenticazione, sessione, navigazione, dati, Custom Server e logout: **tutti funzionanti**;
**nessun `console error` / toast di errore** in alcuna schermata (screenshot `REG-01…06`).

## 4. Additional Bugs

`Additional bugs: NONE.` Esplorati Login, Custom Server, Dashboard, Work Orders, Assets,
Settings, Logout: nessun nuovo bug riproducibile/distinto osservato. Nota **non-bug** (atteso):
"Must use physical device for Push Notifications" — limite dell'emulatore (FCM richiede device
reale), non un difetto.

## 5. Tests

| Check | Esito |
|---|---|
| `tsc --noEmit` (file modificati) | **NO type errors** |
| `prettier --check config.ts slices/instanceConfig.ts` | **All matched files use Prettier code style** |
| Jest (`npm test`) | non eseguito (test unitari non correlati al fix; non richiesto) |
| Build reale (release) | **BUILD SUCCESSFUL** |
| Runtime + regression | **PASS** |

## 6. Build

```text
Command : mobile/android/gradlew.bat assembleRelease --no-daemon --console=plain
Esito   : BUILD SUCCESSFUL in 21m 24s (1173 task; 1137 executed)
Artifact: mobile/android/app/build/outputs/apk/release/app-release.apk  (95.5 MB, gitignored)
Signing : debug keystore (buildTypes.release usa signingConfigs.debug — nessun keystore custom richiesto)
```

Nota tecnica: il **debug APK** carica il JS da Metro (non lo "baka"), quindi per una verifica
**indipendente da Metro** e con il fix realmente compilato nel pacchetto si è usato il **release**
build (che bundla il JS via Hermes). Ambiente vincolato (RAM 15.8 GB): il build è stato eseguito
con emulatore/Docker spenti per evitare thrashing, poi riavviati per il runtime.

## 7. Runtime Verification

```text
Install : adb install app-release.apk → Success (clean, package com.atlas.cmms)
Launch  : app in foreground (com.atlas.cmms/.MainActivity)
Fix     : fresh launch senza server → NESSUN errore (login pulito)
Backend : http://localhost:3000/api/license/state → 200 ; emulatore via http://10.0.2.2:3000/api
Regression: full flow PASS (vedi §3.5)
```

GUI automation: ADB (`input`, `screencap`, `uiautomator dump`, `logcat`), coordinate ricavate
dall'UI reale. Nessun framework pesante introdotto.

## 8. Security

Nessun secret nei report/log. `mobile/android/app/google-services.json` **resta non tracciato**
(gitignored `mobile/.gitignore:19`, verificato). L'APK release (95.5 MB) è in
`android/app/build/…` → **gitignored**. Nessuna credenziale/keystore committata.

## 9. Repository Changes

```text
mobile/config.ts                 (+6)   getApiUrl() null-safe
mobile/slices/instanceConfig.ts  (+9)   skip fetch quando nessun server configurato
```

`git status --short mobile/` = solo questi 2 file. Nessun altro file applicativo, nessun
artefatto di build tracciato, nessun backend/frontend/production toccato.

## 10. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: M-BUG-1 marcato RESOLVED (rimosso da Known Issues, aggiornato lo stato mobile);
aggiunto MOD-015 a Current focus + Current Project State + Documentation Workflow/Map; registrata
la modifica (config.ts + instanceConfig.ts) e la verifica release+regression. Mobile agent
testing resta AVAILABLE. Nessun dettaglio secret.
```

## 11. Known Issues

- **M-BUG-1: RESOLVED** (questo MOD).
- Nessun nuovo bug mobile.
- Ereditati/invariati: push/FCM verificabile solo su **device fisico**; offline write-sync
  `DA VERIFICARE`; F-04 (backend, pre-esistente, out-of-scope).

## 12. Final Verdict

```text
CLAUDE.md updated: YES
M-BUG-1: Reproduced YES | Root cause YES | Fix YES
Build: PASS (release, BUILD SUCCESSFUL 21m24s)
Install: PASS
Runtime: PASS (fresh launch senza errore)
Regression: PASS (Login→Custom Server→Dashboard→Work Orders→Assets→Settings→Logout)
Additional bugs: NONE
Typecheck: PASS (file modificati)   Lint/Prettier: PASS
GUI verification: PASS
Application behavior changes: getApiUrl() null-safe; getInstanceConfig skip-when-no-server
Backend changes: NONE   Production changes: NONE
Security issues: NONE
P0: 0  P1: 0  P2: 0  P3: 0
Final verdict: PASS
Next step: USER DECISION (eventuali altri bug mobile / MOD-016)
```

**M-BUG-1 è FIXED + VERIFIED.** Root cause eliminata (nessun dereference di URL undefined; il
fetch instance-config è saltato finché non c'è un server) con 15 righe in 2 file mobile,
compilate in un **release APK reale** e verificate a runtime: avvio pulito senza errore e
**regression completa PASS** contro il backend self-hosted, senza alcun toast/console error.

⏹️ **STOP** — non avvio MOD-016 né altre attività. Non modifico backend/production. Eventuali
ulteriori bug mobile o il passo successivo sono a decisione del responsabile.
