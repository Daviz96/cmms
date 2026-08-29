# MOD-014B — Firebase Configuration & Android Build

Completato l'unico blocker di MOD-014A: il responsabile ha fornito il `google-services.json`
reale → **build, install, avvio e smoke test dell'app eseguiti sull'AVD `atlas_test`**.
**Nessun bug mobile corretto** (i bug osservati sono documentati per MOD-015). Nessuna modifica
ad app/backend/frontend/licensing/API/DB/production. Secret **non** stampati (nessun valore
Firebase; nessuna password/token nei log).

```text
Application behavior changes: NONE
Repository code changes: NONE   (google-services.json è gitignored; node_modules/local.properties gitignored)
```

> **Esito in una riga:** APK **buildato** (`npx expo run:android`, BUILD SUCCESSFUL 14m45s),
> **installato** e **avviato** sull'emulatore; **smoke test PASS** — Launch → Custom Server
> (`http://10.0.2.2:3000/api`) → Login (`atlastest@example.com`) → Work Orders → Assets →
> Sign out. GUI automation (input/uiautomator/screenshot/logcat) pienamente operativa. Trovato
> **1 bug non bloccante** (`instanceConfig.ts` → `endsWith of undefined`) da trattare in MOD-015.
> → **Mobile agent testing: AVAILABLE.**

---

## 1. Objective

Con la configurazione Firebase reale, buildare l'APK, installarla, avviarla, verificare
Custom Server + login e lo smoke test completo, documentando i bug (senza correggerli).

## 2. Firebase Configuration

`Firebase config: PRESENT.` Il responsabile ha depositato il file reale in
`mobile/android/app/google-services.json`. Verifica (sola esistenza/coerenza, **nessun valore**):

| Check | Esito |
|---|---|
| File presente | **YES** |
| JSON valido | **YES** |
| `package_name` contiene `com.atlas.cmms` | **YES** (match col target Android) |
| Client entries | 1 |

Nessun API key / project ID / app ID letto o riportato. Il progetto Firebase non è stato
creato/modificato.

## 3. Git/Security Check

`Tracked by Git: NO.`

```text
git ls-files mobile/android/app/google-services.json   → (vuoto: non tracciato)
git check-ignore -v …/google-services.json             → mobile/.gitignore:19  (ignore attivo)
git status --short mobile/                              → (vuoto: nessuna modifica tracciata)
```

Il file Firebase **non** è tracciato (regola `mobile/.gitignore:19`). La build **non** ha
introdotto modifiche tracciate (prebuild ha riusato l'`android/` esistente).

## 4. Build

`Android build: PASS.`

```text
Command : npx expo run:android            (dir: mobile/)
Esito   : BUILD SUCCESSFUL in 14m 45s     (603 task; 440 eseguiti, 130 da cache, 33 up-to-date)
Nota    : il primo tentativo con `--device emulator-5554` è fallito (CommandError: il flag
          --device vuole il NOME AVD, non il serial adb). Ri-eseguito senza flag → l'unico
          emulatore in esecuzione è auto-selezionato → SUCCESS.
Warning : molti D8 "Invalid stack map table" su play-services-auth (noti, innocui).
```

Il task `:app:processDebugGoogleServices` (che in MOD-014A falliva) ora **passa** grazie al
file Firebase presente.

## 5. APK

`APK: GENERATED.`

```text
mobile/android/app/build/outputs/apk/debug/app-debug.apk   (package com.atlas.cmms)
```

## 6. Installation

`APK install: PASS.` `expo run:android` ha installato l'APK sull'AVD; verifica:

```text
adb shell pm list packages | findstr com.atlas.cmms   → package:com.atlas.cmms
```

## 7. Runtime

`App launch: PASS.` L'app (dev-client) è stata avviata; il bundle JS è servito da **Metro**
(`adb reverse tcp:8081` → `localhost:8081`; "Android Bundled … index.js (2008 modules)").
`mCurrentFocus = com.atlas.cmms/com.atlas.cmms.MainActivity`. `Login: PASS` — schermata di
login renderizzata (Email, Password, Register here, Custom server).

## 8. Custom Server

`Custom Server: PASS.` Dalla schermata Login → **Custom server** → URL
**`http://10.0.2.2:3000/api`** → **Save**. Backend host verificato 200; emulatore→host `ping
10.0.2.2` ~1 ms. Login e schermate dati successive hanno usato correttamente questo server.

## 9. Smoke Test

`Smoke test: PASS.` Sequenza completa via ADB/UIAutomator (account **di test** creato via API
sul backend locale — password **non** riportata):

| Step | EXPECTED | ACTUAL | SCREENSHOT | RESULT |
|---|---|---|---|---|
| Launch | app + login screen | MainActivity in foreground, login visibile | 03 | **PASS** |
| Custom Server | salva URL | `http://10.0.2.2:3000/api` salvato | 04–05 | **PASS** |
| Login | dashboard | login OK → prompt notifiche (post-login) → dashboard "Atlas" (Open/On Hold/In Progress/Complete = 0) | 06–09 | **PASS** |
| Work Orders | lista | lista caricata: "No elements match this criteria" (account vuoto) | 12 | **PASS** |
| Assets | lista | schermata Assets caricata (Search) | 14 | **PASS** |
| Settings | account | Settings mostra `atlastest@example.com`, "Sign out", Version 1.0.47 | 17 | **PASS** |
| Logout | torna a Login | dialog "Are you sure you want to logout?" → Sign out → **Login screen** | 18–19 | **PASS** |

Il caricamento di Work Orders/Assets/dashboard conferma la **connettività end-to-end**
app→backend self-hosted (`10.0.2.2:3000`) con sessione autenticata.

## 10. GUI Automation

`GUI automation: PASS.` Confermati operativi durante l'intero smoke test:

- **`adb shell input`** (tap/text/keyevent) — navigazione, digitazione URL/credenziali;
- **UIAutomator** (`uiautomator dump`) — coordinate ricavate dall'UI reale (mai inventate);
- **Screenshot** (`screencap` + `adb pull`) — ~19 screenshot degli step;
- **Logcat** (`adb logcat`) — raccolta errori (nessun secret nei log).

Nessun framework pesante (Appium/Detox/Maestro) necessario.

## 11. Bugs Observed

`Bugs observed: LIST (1 non bloccante).` **Non corretti** (MOD-015).

| ID | Sev | Descrizione | Evidenza | Impatto |
|---|---|---|---|---|
| **M-BUG-1** | P2/P3 | `TypeError: Cannot read property 'endsWith' of undefined` in **`mobile/slices/instanceConfig.ts`** durante il fetch dell'"instance config". Compare come **toast di errore** persistente e, in dev, come **LogBox** rossa (console.error). Causa apparente: una stringa/URL `undefined` (nel dev build `API_URL` non è "bakato"; l'errore si presenta a startup e su navigazione). | toast in dashboard (screenshot 09); LogBox call-stack → `slices/instanceConfig.ts` | **Non bloccante**: login, dashboard, Work Orders, Assets e logout funzionano. In produzione (senza LogBox) resterebbe un `console.error` + fetch config fallito. Da correggere in MOD-015. |

Osservazioni **non-bug** (attese):
- **"Must use physical device for Push Notifications"**: limite dell'emulatore (FCM richiede
  device reale) — non un difetto.
- Il **dev build** ha richiesto di impostare Custom Server perché `API_URL` non è compilato nel
  bundle di sviluppo (i build store/EAS lo includono) — comportamento atteso, non un bug.

## 12. Known Issues

- **M-BUG-1** (`instanceConfig.ts` endsWith) → **MOD-015**.
- Push/FCM: consegna verificabile solo su **device fisico** (invariato).
- F-04 (backend, pre-esistente) e offline write-sync: invariati.

## 13. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: MOD-014B completato — Firebase config PRESENT (gitignored), build+install+launch+smoke
test PASS su AVD atlas_test; Mobile agent testing → AVAILABLE; registrato M-BUG-1
(instanceConfig endsWith, non bloccante) in Known Issues per MOD-015; Open Decision del
provisioning mobile chiusa. Nessuna modifica applicativa/tracciata.
```

## 14. Final Verdict

```text
CLAUDE.md updated: YES
Firebase config: PRESENT
Tracked by Git: NO (gitignored: mobile/.gitignore:19)
Android build: PASS (BUILD SUCCESSFUL 14m45s)
APK: GENERATED (app-debug.apk, com.atlas.cmms)
APK install: PASS
App launch: PASS
Login: PASS (atlastest@example.com)
Custom Server: PASS (http://10.0.2.2:3000/api)
GUI automation: PASS (input/uiautomator/screenshot/logcat)
Smoke test: PASS (Launch→Custom Server→Login→Work Orders→Assets→Logout)
Bugs observed: 1 (M-BUG-1 instanceConfig endsWith, non bloccante, → MOD-015)
P0: 0  P1: 0  P2: 1  P3: 0
Mobile agent testing: AVAILABLE
Final verdict: PASS WITH FINDINGS
Next step: MOD-015 (fix M-BUG-1 e altri bug mobile) — su autorizzazione del responsabile
```

**Mobile agent testing: AVAILABLE.** L'app Atlas è buildata dai sorgenti con Firebase reale,
installata e avviata sull'emulatore, e lo **smoke test completo passa** contro il backend
self-hosted; l'agent può ora riprodurre bug, catturare screenshot/log e iterare (build →
install → retest). È emerso **1 bug non bloccante** (`instanceConfig.ts`), documentato per
MOD-015.

⏹️ **STOP** — non correggo i bug mobile (incl. M-BUG-1), non modifico backend/frontend/
licensing/production/Caddy/DNS, non faccio deployment e **non avvio MOD-015**. L'avvio di
MOD-015 spetta al responsabile.
