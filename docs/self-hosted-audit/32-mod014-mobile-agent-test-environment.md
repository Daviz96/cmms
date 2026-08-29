# MOD-014 — Mobile Agent Test Environment

Predisposizione di un ambiente **ripetibile** perché il coding agent possa buildare,
installare, avviare e testare realmente l'app mobile Atlas (priorità Android) e valutare
cosa è realisticamente possibile su iOS. Attività di **assessment/documentazione**:
**nessun bug mobile corretto**, nessuna modifica a backend/frontend/licensing/API/DB/Docker
production/Caddy/DNS/certificati. Secret **non** stampati (solo nome/posizione).

```text
Code changes: NONE
```

> **Esito in una riga:** in **questo** ambiente il testing GUI mobile via agent **NON è
> disponibile** — Node/git/docker e un JDK 17 portatile ci sono, ma **mancano interamente
> Android SDK, `adb`, emulatore, Android Studio, la CLI Expo/EAS, i `node_modules` e la
> config Firebase**. Il percorso realistico è **installare l'Android SDK + creare un AVD (o
> collegare un device) sul build host e pilotare via ADB** (primario), oppure **EAS cloud
> build + device farm cloud** (secondario). iOS non è buildabile/eseguibile su host Windows.
> → **Mobile agent testing: NOT AVAILABLE (diventa PARTIAL con il setup Android documentato).**

---

## 1. Objective

Determinare, senza inventare, quali capacità di build/install/run/GUI-automation mobile
esistono realmente in questo ambiente; documentare il percorso Android (priorità), i limiti
iOS, e raccomandare un ambiente ripetibile per il futuro debugging (MOD-015). Non correggere
i bug mobile. Non installare componenti pesanti automaticamente senza necessità/decisione.

## 2. Current Environment

Host: **Windows 11**, CLI headless. Probe eseguito (`Get-Command` / env / filesystem):

| Strumento | Stato | Fonte |
|---|---|---|
| Node.js | **PRESENTE** v24.19.0 | `C:\Program Files\nodejs\node.exe` |
| npm | **PRESENTE** 11.17.0 | `nodejs\npm.ps1` |
| npx | PRESENTE | `nodejs\npx.ps1` |
| git | PRESENTE | `Git\cmd\git.exe` |
| docker | PRESENTE | `Docker\resources\bin\docker.exe` |
| JDK (Java) | **NON su PATH**; **JDK 17 portatile disponibile** | scratchpad `…/jdk17/jdk-17.0.20.1+1/bin/java.exe` (verificato) |
| yarn / pnpm | NOT FOUND | — (npm sufficiente) |
| adb | **NOT FOUND** | — |
| emulator | **NOT FOUND** | — |
| sdkmanager / avdmanager | **NOT FOUND** | — |
| gradle (globale) | NOT FOUND | (wrapper `mobile/android/gradlew` presente) |
| expo / eas (globali) | NOT FOUND | (usabili via `npx`) |
| Android SDK | **ASSENTE** | `ANDROID_HOME`/`ANDROID_SDK_ROOT` vuoti; `%LOCALAPPDATA%\Android\Sdk` inesistente |
| Android Studio | **ASSENTE** | nessun percorso comune presente |
| iOS toolchain | **N/A** | host Windows (no macOS/Xcode/CocoaPods) |

Backend Atlas: stack Docker single-ingress su host **:3000** (MOD-013). Nessun uso di
`websrv01`/production.

## 3. Mobile Toolchain

Dalla configurazione del repo (`mobile/package.json`, `app.config.ts`, `eas.json`):

```text
Tool:     Expo SDK          Version: ~53.0.27         Comando: npx expo …           Fonte: package.json:33
Tool:     React Native      Version: 0.79.6           (Hermes; newArchEnabled:false) Fonte: package.json:62, app.config.ts:24
Tool:     React             Version: 19.0.0                                         Fonte: package.json:59
Tool:     expo-dev-client   Version: ~5.2.4  → richiede DEV BUILD (Expo Go NON basta) Fonte: package.json:38, script start --dev-client
Tool:     EAS CLI           Version: ">= 7.0.0" (eas.json)  Comando: npx eas-cli …   Fonte: eas.json:3
Tool:     Metro/Jest        (jest-expo)               Comando: npm test             Fonte: package.json:15-17
Tool:     Gradle wrapper    (android/gradlew[.bat])   Comando: expo run:android     Fonte: mobile/android/gradlew
```

**Punti chiave:**
- L'app usa **moduli nativi custom** (`@react-native-firebase/*`, `react-native-nfc-manager`,
  `expo-camera`, `react-native-webview`, …) → **Expo Go non è sufficiente**: serve un
  **development build** (dev client) o un APK, quindi build nativa Android (SDK obbligatorio).
- `compileSdkVersion/targetSdkVersion = 36`, `package = com.atlas.cmms` (Android),
  `bundleIdentifier = com.cmms.atlas` (iOS), `scheme = atlascmms` (app.config.ts).
- Profili build EAS: `development` (dev client), `preview`, `production`, **`previewAndroid`
  (buildType apk)** — quest'ultimo è il percorso APK documentato nel README.
- **`mobile/node_modules` NON è installato** e **`mobile/android/app/google-services.json`
  (Firebase) è ASSENTE** → prerequisiti di qualunque build locale.

## 4. Android Environment

Android SDK **non installato** (nessun `adb`/`sdkmanager`/AVD; `ANDROID_HOME` vuoto). I
progetti nativi `mobile/android/` e `mobile/ios/` **esistono** (con `gradlew`), ma senza SDK
non si builda/installa/esegue nulla.

**Build & Run Matrix** (`SUPPORTED`/`POSSIBLE`/`NOT POSSIBLE`/`NOT TESTED`/`BLOCKED`):

| Target | Build | Install | Runtime | GUI Automation | Stato |
|---|---|---|---|---|---|
| Android Emulator | POSSIBLE¹ | POSSIBLE¹ | POSSIBLE¹ | POSSIBLE¹ (ADB) | **BLOCKED** ora (no SDK/AVD) |
| Android Device | POSSIBLE² | POSSIBLE² | POSSIBLE² | POSSIBLE² (ADB) | **BLOCKED** ora (no device+no adb) |
| iOS Simulator | NOT POSSIBLE | NOT POSSIBLE | NOT POSSIBLE | NOT POSSIBLE | **NOT POSSIBLE** (host Windows) |
| iOS Device | POSSIBLE³ (EAS) | POSSIBLE³ | NOT POSSIBLE (agent) | NOT POSSIBLE (agent) | **BLOCKED** per l'agent |

¹ dopo: install Android SDK + system image + AVD, `npm install`, JDK 17, google-services.json.
² dopo: install Android SDK/platform-tools (adb), device fisico con USB-debugging **collegato al build host** (non disponibile all'agent in headless).
³ solo build cloud EAS (macOS m1) con account Expo + Apple Developer del responsabile; runtime/automazione richiedono comunque Mac o device.

## 5. Android Emulator

**BLOCKED (ora).** Nessun AVD, nessuna system image, nessun binario `emulator`, SDK assente.
Per abilitarlo (setup, non eseguito qui):

```text
1. Android cmdline-tools → sdkmanager
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
              "emulator" "system-images;android-35;google_apis;x86_64"
2. avdmanager create avd -n atlas_test -k "system-images;android-35;google_apis;x86_64"
3. emulator -avd atlas_test  (headless: -no-window -no-audio; richiede accelerazione
   hardware WHPX/Hyper-V — Docker Desktop già usa WSL2/Hyper-V su questo host)
4. adb devices  → "emulator-5554  device"
```

Peso: multi-GB (SDK + system image). Il prompt vieta install pesanti automatici senza
necessità/decisione → **richiede decisione del responsabile** (§21 Next Step / gate).

## 6. Android Physical Device

**NOT AVAILABLE all'agent.** Ambiente headless: nessun device collegato/pilotabile dall'agent
e nessun `adb`. Il device Android personale del responsabile non è assunto come accessibile
in modo permanente (il responsabile ha testato iOS, non è confermato un Android per l'agent).
Se in futuro un device viene collegato al build host: USB-debugging + `adb devices` +
`adb install` + Custom Server `http://<LAN-IP>:3000/api` lo renderebbero utilizzabile.

## 7. Build Procedure

Percorso **ufficiale** realmente supportato (due opzioni). **Prerequisiti comuni:** `npm
install` in `mobile/`, JDK 17, e `google-services.json` (reale per push, oppure placeholder/
`GOOGLE_SERVICES_BASE64` per build senza push).

**A) Locale — dev build (preferito per iterazione/debug):**
```text
Command:      cd mobile && npm install && npx expo run:android
Artifact:     APK debug + install diretto sull'emulatore/device via Gradle
Location:     mobile/android/app/build/outputs/apk/…
Prerequisites: Android SDK (platform 36, build-tools, platform-tools), JDK 17 (JAVA_HOME),
               ANDROID_HOME, emulatore/device attivo, node_modules, google-services.json
```

**B) Cloud — EAS (APK installabile, no SDK locale per buildare):**
```text
Command:      cd mobile && npx eas-cli@latest build --profile previewAndroid --platform android
Artifact:     APK (buildType apk) sui server Expo (projectId 803b5007-0c60-4030-ac3a-c7630b223b92)
Location:     URL di download EAS
Prerequisites: account Expo del responsabile (login/credenziali — NON disponibili all'agent),
               rete. Per installarlo/pilotarlo serve comunque un emulatore/device.
```

> L'agent, in questo ambiente, **non può completare né A (no SDK) né B (no credenziali Expo)**.

## 8. Install Procedure

Package: **`com.atlas.cmms`** (Android). Con emulatore/device + adb disponibili:

```text
adb install -r mobile/android/app/build/outputs/apk/debug/app-debug.apk   # (o l'APK EAS)
adb shell pm list packages | grep com.atlas.cmms                          # verifica install
# clear data solo se necessario:  adb shell pm clear com.atlas.cmms
```

Obiettivo minimo: `APP INSTALLED → APP STARTED → LOGIN → CUSTOM SERVER → ATLAS BACKEND`.

## 9. Runtime Procedure

```text
adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1   # avvio robusto
# (main activity esatta verificabile con: adb shell cmd package resolve-activity --brief com.atlas.cmms)
```
Poi impostare il server dalla schermata **Custom Server** (route `CustomServer`, da Login —
MOD-009): URL **`http://<host>:3000/api`** (vedi §13 Network).

## 10. GUI Automation

Preferenza (dal prompt): **strumento già disponibile > leggero > framework complesso**. Con
ADB attivo, l'automazione **leggera** copre lo smoke test senza framework:

```text
adb shell input tap <x> <y>          # tap coordinate
adb shell input text "user@mail"     # digitazione (campi testo)
adb shell input keyevent 66          # ENTER
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml   # ispezione albero UI (trova coordinate)
```
Framework più ricchi (**Maestro** — YAML leggero; **Appium**; **Detox**) sono opzionali e
**non installati**; non introdurli se ADB+uiautomator bastano. **Stato attuale: BLOCKED**
(richiede adb + device/emulatore, entrambi assenti).

## 11. Screenshot Capture

```text
adb exec-out screencap -p > screenshot.png
```
Utilizzabile per riproduzione bug e before/after nei report. **Stato: BLOCKED** ora (no
adb/device). Diventa `AVAILABLE` con il setup Android.

## 12. Log Collection

```text
adb logcat -v time                                   # log di sistema/app
adb logcat *:E ReactNativeJS:V                        # errori + log JS (Hermes/RN)
npx expo start --dev-client                            # Metro/JS log (dev build, richiede device)
```
Non inserire secret/token nei report. **Stato: BLOCKED** ora (no adb). `AVAILABLE` col setup.

## 13. Network Debugging

Il device/emulatore **non** usa `localhost`/`127.0.0.1` per raggiungere il backend host:

| Contesto | URL backend (Custom Server) |
|---|---|
| **Android Emulator** | `http://10.0.2.2:3000/api` (10.0.2.2 = host loopback dall'emulatore) |
| **Device fisico (stessa LAN)** | `http://<HOST_LAN_IP>:3000/api` (es. `192.168.100.110`, vedi istruzioni deploy MOD-013) |

Il backend è il **single-ingress nginx** su host `:3000` (frontend `/`, API `/api`, storage
`/storage`). Osservazione API senza MITM: `adb logcat` (errori rete RN) + i log del container
`docker compose logs -f api`. **Nessun proxy MITM/cert custom** introdotto (vietato dal prompt).

## 14. Smoke Test

Smoke test minimo ripetibile (una volta disponibili emulatore/device + adb), tutto via ADB:

```text
Launch        adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
Custom Server naviga da Login → CustomServer; input URL http://10.0.2.2:3000/api ; Save
Login         input email/password ; tap Login          (screencap per evidenza)
Assets        apri lista Assets                          (screencap)
Work Order    apri lista Work Orders                     (screencap)
Attachment    apri un allegato / immagine                (screencap)
Logout        menu → Logout                              (screencap)
Evidence      adb exec-out screencap ad ogni step ; adb logcat per errori
```

Automazione: gli **step di navigazione a coordinate** richiedono un primo pass manuale/
`uiautomator dump` per fissare le coordinate (le coordinate non sono deducibili a priori →
non le invento). Parte automatizzabile subito con ADB: launch, install, screencap, logcat,
input; parte da calibrare: tap/coordinate per schermata.

## 15. iOS Environment

**NOT POSSIBLE su questo host.** iOS richiede **macOS + Xcode + CocoaPods + iOS Simulator**;
l'host è Windows. Analisi:

| Elemento | Stato su questo host |
|---|---|
| macOS / Xcode / Simulator | ASSENTI (Windows) → build/run locale **NOT POSSIBLE** |
| CocoaPods | N/A |
| Build iOS via **EAS** (cloud macOS `m1-medium`, eas.json) | POSSIBLE ma richiede account Expo + Apple Developer del responsabile |
| Signing/provisioning | richiede Apple Developer account (credenziali non disponibili all'agent) |
| GUI automation iOS (agent) | **NOT POSSIBLE** senza Mac/device |
| Accesso al backend LAN | OK a livello di rete (già verificato dal responsabile su device iOS reale, MOD-013) |

Nessun account Apple/servizio cloud configurato (vietato senza approvazione).

## 16. Remote Mac / CI Options

Valutazione (senza implementare):

| Opzione | Build | Automazione GUI agent | Costo/Complessità | Note |
|---|---|---|---|---|
| **EAS Build** (Expo cloud) | Android **+ iOS** | No (solo artefatti APK/IPA) | Basso-medio (account Expo; iOS→Apple Dev) | Percorso build ufficiale del repo; non dà un device pilotabile |
| **GitHub Actions macOS** | iOS (e Android) | Difficile (no device interattivo) | Medio | Utile per CI build, non per GUI-automation agent |
| **Device farm cloud** (Firebase Test Lab / BrowserStack App Automate / AWS Device Farm / Sauce Labs) | — (carichi l'APK/IPA) | **Sì** (Espresso/XCUITest/Appium) con screenshot+log | Medio-alto (account, costo) | Unico percorso che dà **device reali automatizzabili** all'agent |
| **Mac locale/remoto** | iOS nativo | Sì (con Mac) | Alto (hardware) | Massimo controllo iOS |

## 17. Recommended Setup

Derivata dall'ambiente reale (non da supposizioni):

- **Ambiente primario (Android, priorità):** su un **build host con Android SDK** (Android
  Studio o solo cmdline-tools) — install SDK (platform 36, build-tools, platform-tools/adb,
  emulator + una system image Google APIs x86_64), JDK 17 stabile, `npm install` in `mobile/`,
  fornire `google-services.json`. Poi **dev build** (`npx expo run:android`) su un **AVD headless**
  e automazione **solo-ADB** (`input`/`uiautomator dump`/`screencap`/`logcat`). È il percorso
  ripetibile, a **minima infrastruttura**, coerente con la preferenza del prompt. *Requisito:
  installare l'Android SDK sul host — assente in questo ambiente (decisione del responsabile,
  install pesante).*
- **Ambiente secondario:** **EAS build** (`previewAndroid`) con l'account Expo del responsabile
  per produrre un APK installabile senza SDK locale, combinato con una **device farm cloud**
  (es. Firebase Test Lab) quando serve un device reale automatizzabile dall'agent.
- **iOS:** non su questo host → **EAS** (build cloud macOS) per gli artefatti + **Mac o device
  farm** per runtime/automazione; nel frattempo resta valido il **test manuale su device iOS
  reale del responsabile** (già eseguito, MOD-013).

## 18. Repository Changes

```text
Code changes: NONE
```
Nessuna modifica applicativa mobile effettuata (né bug fix, vietati). **Proposte opzionali,
solo-test, NON implementate** (da decidere dal responsabile, §21 del prompt):

1. `mobile/.env` (gitignored, **non committato**) con `API_URL=http://10.0.2.2:3000/api` per
   i dev build verso il backend locale su emulatore — evita di reinserire l'URL ad ogni avvio.
2. `mobile/android/app/google-services.json` **placeholder** per build senza push (o uso di
   `GOOGLE_SERVICES_BASE64`) — motivazione: la build Android fallisce senza il file Firebase.
3. Uno script helper `scripts/mobile/adb-smoke.*` con la sequenza ADB del §14 — **non creato
   ora** perché non testabile senza device (eviterei comandi non verificati / coordinate finte).

Motivo del non-implementare: mantenere il comportamento production invariato e non introdurre
artefatti non verificabili in questo ambiente. Le proposte 1–2 sono le uniche modifiche
minime che sbloccherebbero un dev build locale, se il responsabile approva il setup Android.

## 19. Security

Nessun secret inserito. **Non** committare `google-services.json` reale, keystore (`.keystore`/
`.jks`), `.p12`, `.mobileprovision`, `GoogleService-Info.plist`, `.env` mobile o credenziali
Expo/Apple/Firebase. `google-services.json` va fornito via secret/`GOOGLE_SERVICES_BASE64`
(come già previsto da `app.config.ts:11-13`). Nessuna cattura di token/credenziali nei log/
report. Nessun proxy MITM/cert custom introdotto.

## 20. Known Limitations

- Android SDK / `adb` / emulatore / Android Studio **assenti** → build/install/run/GUI-automation
  Android **BLOCKED** in questo ambiente finché non si installa l'SDK (install pesante, gate).
- `mobile/node_modules` non installato; `google-services.json` assente → prerequisiti build.
- Nessun device fisico Android pilotabile dall'agent (headless).
- **EAS build** richiede l'account Expo del responsabile (credenziali non disponibili all'agent).
- **iOS** non buildabile/eseguibile/automatizzabile su host Windows; EAS/Mac/device farm o test
  manuale del responsabile.
- JDK presente è **portatile in scratchpad** (percorso di sessione temporaneo) → per un ambiente
  durevole serve un JDK 17 stabile installato.
- Push notification (FCM) e write-sync offline restano `DA VERIFICARE` (necessitano Firebase/
  device) — invariati rispetto a MOD-009/012.

## 21. Next Step

`Next step: ENVIRONMENT WORK REQUIRED` (non ancora MOD-015). Per §27, i bug fix mobile
(MOD-015) partono **solo dopo** che l'ambiente di test è utilizzabile — attualmente non lo è.
**Decisione richiesta al responsabile** (gate): come predisporre l'ambiente Android —

- **Opzione 1 (locale):** autorizzare l'installazione dell'**Android SDK + AVD** sul host
  (multi-GB) + `npm install` + `google-services.json` → l'agent completa un dev build e
  l'automazione ADB. *(install pesante — richiede ok esplicito.)*
- **Opzione 2 (cloud):** fornire l'accesso **EAS** (account Expo) per generare l'APK e usare
  una **device farm** per l'automazione.
- **Opzione 3 (manuale):** collegare un **device Android reale** con USB-debugging al build
  host per l'uso via ADB.

## 22. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-014 (Mobile Agent Test Environment) a Current focus + Current Project
State + Documentation Workflow/Map; registrata la capacità "Mobile agent testing: NOT
AVAILABLE (PARTIAL col setup Android)"; documentati toolchain reale (Expo 53/RN 0.79.6,
dev-client), assenza Android SDK/adb/emulatore/Firebase/node_modules, percorsi build
(expo run:android / EAS previewAndroid), automazione ADB, limiti iOS (host Windows) e le
Open Decisions (setup ambiente Android: locale SDK vs EAS+device farm vs device fisico).
Nessuna modifica di codice.
```

## 23. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
Android build: BLOCKED (Android SDK assente; node_modules + google-services.json mancanti; JDK 17 portatile disponibile)
Android emulator: BLOCKED (no SDK/AVD/system image)
Android device: BLOCKED (nessun device+adb per l'agent)
Android GUI automation: BLOCKED (richiede adb + device/emulatore)
Screenshot capture: BLOCKED ora (adb assente) — AVAILABLE col setup Android
Log capture: BLOCKED ora (adb assente) — AVAILABLE col setup Android
iOS build: BLOCKED (host Windows; possibile solo via EAS cloud con account Expo+Apple)
iOS runtime: BLOCKED (richiede macOS/Mac/device)
iOS automation: BLOCKED (non su Windows)
Recommended environment: Android SDK + AVD/device + automazione solo-ADB (primario); EAS build + device farm cloud (secondario); iOS via EAS/Mac/device farm o test manuale del responsabile
Mobile agent testing: NOT AVAILABLE (diventa PARTIAL con il setup Android documentato)
P0: 0  P1: 0  P2: 0  P3: 0
Final verdict: PASS WITH FINDINGS
Next step: ENVIRONMENT WORK REQUIRED
```

**Mobile agent testing: NOT AVAILABLE** in questo ambiente. L'assessment è completo e la
toolchain reale è identificata: l'app (Expo 53 / RN 0.79.6, dev-client, moduli nativi) richiede
una **build nativa Android** che qui è impossibile per **assenza dell'Android SDK/adb/emulatore**
(oltre a `node_modules` e Firebase mancanti); iOS è fuori portata su host Windows. Sono
documentati i percorsi build (`expo run:android` locale / EAS `previewAndroid`), l'automazione
**leggera via ADB** (input/uiautomator/screencap/logcat), il networking (`10.0.2.2` emulatore /
LAN device) e lo smoke test ripetibile. Il passo successivo è **provisionare l'ambiente**
(decisione del responsabile fra SDK locale, EAS+device farm, o device fisico) — **non** MOD-015.

⏹️ **STOP** — non correggo bug mobile, non modifico backend/frontend/licensing/Docker/
production/Caddy/DNS, non faccio deployment, non installo l'Android SDK automaticamente e
**non avvio MOD-015**. La scelta dell'ambiente di test spetta al responsabile.
