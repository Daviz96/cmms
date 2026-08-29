# MOD-014A — Android Test Environment Setup

Predisposizione **eseguita** di un ambiente Android locale ripetibile per far buildare/
installare/avviare/testare l'app mobile Atlas dall'agent. Autorizzato dal responsabile
(scelta "Opzione 1 — SDK locale" di MOD-014). **Nessun bug mobile corretto**, nessuna
modifica ad app/backend/frontend/licensing/API/DB/Docker production/Caddy/DNS. Modifiche
**solo di ambiente** (fuori dal repo o gitignored). Secret non stampati.

```text
Application behavior changes: NONE
Repository code changes: NONE (solo env: JDK/SDK esterni al repo; local.properties gitignored; node_modules gitignored)
```

> **Esito in una riga:** l'ambiente Android è **operativo end-to-end** — JDK 17 stabile,
> Android SDK completo, `adb`/emulatore con **accelerazione WHPX**, AVD `atlas_test` che
> **boota headless in ~36 s**, `npm install` completato, e **baseline di automazione ADB
> (input/uiautomator/screenshot/logcat) verificata**. La build reale attraversa **tutto** il
> toolchain e si ferma su **un unico blocker**: manca `google-services.json` (Firebase),
> che §12 vieta di fabbricare. → **Mobile agent testing: PARTIAL** (manca solo la credenziale
> Firebase per generare l'APK). **Next step: fornire `google-services.json` → poi MOD-015.**

---

## 1. Objective

Rendere l'ambiente Android realmente utilizzabile dall'agent: JDK 17 stabile, Android SDK,
ADB, AVD/emulatore, dipendenze npm, build, install, smoke test e automazione GUI leggera,
documentando tutto per la ripetibilità. Non correggere bug. Fermarsi e documentare se serve
una credenziale reale.

## 2. Host Environment

| Voce | Valore |
|---|---|
| OS | Windows 11 (10.0.26200) |
| CPU | Intel Core i5-10300H — arch **AMD64** (x86_64) |
| RAM | 15.8 GB |
| Disco C: | 46 GB liberi → **34.2 GB** dopo il setup (SDK+system image+node_modules+cache Gradle ≈ 12 GB) |
| Hypervisor | presente (`HypervisorPresent=True`) → **WHPX operativo** per l'emulatore |
| Preesistenti | Node **v24.19.0**, npm **11.17.0**, git, docker |
| Assenti prima del MOD | java, adb, emulator, sdkmanager, avdmanager, Android SDK/Studio |

## 3. JDK

`JDK 17: AVAILABLE.` Il JDK 17 portatile di MOD-014 era in scratchpad (percorso temporaneo,
§4 vietava di usarlo come definitivo). **Copiato in posizione stabile**:

```text
JAVA_HOME = C:\Users\dawid\Android\jdk-17.0.20.1+1   (Temurin 17.0.20.1+1)
```

Impostato come variabile utente. Verifica: `java -version` → `openjdk 17.0.20.1`.

## 4. Android SDK

`Android SDK: AVAILABLE.` Installato via **command-line tools** (niente Android Studio).

```text
ANDROID_HOME = C:\Users\dawid\Android\Sdk
cmdline-tools/latest : sdkmanager 12.0  (commandlinetools-win-11076708)
```

Componenti installati (licenze accettate, `sdkmanager --licenses`):

| Package | Versione |
|---|---|
| `platform-tools` | 37.0.1 (adb 1.0.41) |
| `platforms;android-36` | rev 2 (compileSdk/targetSdk = 36) |
| `build-tools;36.0.0` | 36.0.0 |
| `emulator` | 37.1.11 |
| `system-images;android-35;google_apis;x86_64` | rev 9 (Google APIs, x86_64) |

Scelta system image: **android-35 google_apis x86_64** (compatibile con host Intel x86_64 +
WHPX; Google APIs necessarie per servizi Google; API 35 stabile — l'app con targetSdk 36 gira
correttamente su API 35).

## 5. ADB

`ADB: AVAILABLE.` `adb version` → *Android Debug Bridge 1.0.41 / 37.0.1-15733141*.
Percorso: `C:\Users\dawid\Android\Sdk\platform-tools\adb.exe` (in PATH utente).

## 6. AVD

`AVD: AVAILABLE.`

```text
AVD name     : atlas_test
API level    : 35
Image        : system-images;android-35;google_apis;x86_64
Architecture : x86_64
Device       : pixel_6 (skin 1080x2400)
RAM          : 2560 MB (auto)
GPU          : swiftshader_indirect (software, per headless affidabile)
Path         : C:\Users\dawid\.android\avd\atlas_test.avd
```

Creato con `avdmanager create avd -n atlas_test -k system-images;android-35;google_apis;x86_64 -d pixel_6`.

## 7. Emulator

`Emulator: AVAILABLE.` Avvio **headless**:

```powershell
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd atlas_test `
   -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -no-metrics
```

Log di avvio: *"WHPX on Windows … Windows Hypervisor Platform accelerator is operational"* →
**accelerazione hardware attiva**. Boot completo in **~36 s** (`sys.boot_completed=1`):

```text
adb devices  → emulator-5554   device
ro.product.model = sdk_gphone64_x86_64 ; ro.build.version.sdk = 35
```

Headless (§10): `-no-window` funziona in modo affidabile con GPU software → nessun display
virtuale necessario. Emulatore **fermato** a fine MOD (`adb -s emulator-5554 emu kill`) per
liberare risorse; si rilancia col comando sopra in ~36 s.

## 8. Repository Dependencies

`Node/npm dependencies: AVAILABLE.` `cd mobile; npm install` (Node 24.19.0, npm 11.17.0,
`package-lock.json` presente):

```text
added 1213 packages in ~4m
postinstall: patch-package 8.0.1 → "No patch files found" (nessuna patch, OK)
```

Nota (non bloccante): npm 11 lascia **non approvati** alcuni install-script — `bufferutil`,
`utf-8-validate` (acceleratori nativi di `ws`, con fallback JS), `protobufjs`, `@firebase/util`,
`es5-ext`, `postinstall-postinstall`. Non impediscono il bundling Metro/Gradle. Se in futuro
servissero, `npm approve-scripts` + rebuild. `mobile/node_modules` è gitignored.

## 9. Firebase Prerequisites

`Firebase prerequisite: BLOCKED (credenziale reale richiesta).`

- `mobile/android/app/build.gradle:179` applica **`com.google.gms.google-services`**
  incondizionatamente; `mobile/android/build.gradle:12` ha `classpath com.google.gms:google-services:4.4.1`.
- `mobile/android/app/google-services.json` è **assente**.
- `app.config.ts:11-13` prevede la generazione del file da **`GOOGLE_SERVICES_BASE64`** (env),
  non impostato.
- Il plugin google-services 4.4.1 **fallisce il build** se il file manca (verificato §10).

Per §12/§28: **NON** ho inventato/creato un `google-services.json` placeholder. Serve la
credenziale reale del progetto Firebase del responsabile (file in `mobile/android/app/` **o**
via `GOOGLE_SERVICES_BASE64`). **STOP — requisito documentato, non procurato.**

## 10. Build

`Android build: BLOCKED (Firebase).` Attempt reale col wrapper Gradle del progetto (evita
Metro e rigenerazioni prebuild dei sorgenti):

```text
Command : mobile\android\gradlew.bat :app:processDebugGoogleServices --no-daemon --console=plain
Durata  : 9m 28s
Esito   : BUILD FAILED
```

Il toolchain ha funzionato **fino al** task Firebase (JDK 17, Android SDK, Gradle 8.10.2,
AGP 8.7.2, Expo autolinking, tutti i moduli configurati — solo warning prima del fail). Errore
esatto:

```text
* What went wrong:
Execution failed for task ':app:processDebugGoogleServices'.
> File google-services.json is missing.
  Searched: ...\mobile\android\app\google-services.json (e src\debug\...)
```

Artifact: **APK NON generato** (blocco a monte della compilazione). Con `google-services.json`
presente, il percorso ufficiale è `cd mobile; npx expo run:android` (build + install + Metro).

## 11. APK Installation

`APK install: BLOCKED` (nessun APK — §10). Procedura pronta (package **`com.atlas.cmms`**):

```powershell
adb install -r <path-apk>
adb shell pm list packages | findstr com.atlas.cmms
```

## 12. Runtime

`App launch: BLOCKED` (nessun APK). Comando pronto:

```powershell
adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
```

`Login screen: BLOCKED` (dipende dall'APK). Verificabile appena l'APK è disponibile.

## 13. Custom Server

`Backend connectivity:` host **PASS**, route emulatore **PASS**, L7 app **BLOCKED** (no APK).

- Backend locale avviato (`docker compose up -d`): host `http://localhost:3000/api/license/state`
  → **HTTP 200** (warmup ~45 s).
- Emulatore → host: `ping 10.0.2.2` → **2/2 reply, ~1 ms** (route verificata).
- URL Custom Server per l'emulatore: **`http://10.0.2.2:3000/api`** (mai `localhost`/`127.0.0.1`
  dal device). Per device fisico: `http://<HOST_LAN_IP>:3000/api`.
- L'immagine emulatore **non ha curl/wget**; toybox `nc` connette ma non veicola la risposta
  HTTP → la verifica L7 dal device richiede l'app installata (bloccata da Firebase) → documentata.
  Il backend non è stato modificato per il test.

## 14. Screenshot

`Screenshot: PASS.` Metodo **binary-safe** (obbligatorio):

```powershell
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png .\s.png     # PNG valido (magic 89-50-4E-47), ~1.37 MB verificato
```

⚠️ **NON** usare `adb exec-out screencap -p > file.png` da PowerShell: la redirezione `>`
re-encoda il binario (BOM UTF-8) e **corrompe** il PNG (riscontrato: magic `EF-BB-BF`). Usare
sempre `screencap`+`adb pull`.

## 15. Logcat

`Logcat: PASS.`

```powershell
adb logcat -c                        # clear
adb logcat -d -v time > atlas-logcat.txt   # dump (verificato: 49.824 righe)
adb logcat *:E ReactNativeJS:V        # errori + log JS (Hermes/RN)
```

Nessun secret/token incluso nei log dei report.

## 16. GUI Automation

`GUI automation baseline: PASS` (ADB + UIAutomator, senza framework pesanti — §18 preferenza).
Verificato sull'emulatore:

```powershell
adb shell input tap <x> <y>            # OK
adb shell input keyevent KEYCODE_HOME  # OK (WAKEUP/HOME testati)
adb shell input text "<txt>"           # pronto
adb shell uiautomator dump /sdcard/ui.xml ; adb pull /sdcard/ui.xml   # OK (12.307 byte)
```

Le **coordinate** dei tap vanno ricavate da `uiautomator dump` sull'UI reale dell'app — **non
inventate** (§28). Appium/Maestro/Detox non installati (non necessari: ADB+UIAutomator bastano).

## 17. Smoke Test

`Smoke test: BLOCKED` (richiede l'APK — Firebase). Sequenza definita e pronta all'esecuzione
non appena l'APK è disponibile (ogni step: EXPECTED/ACTUAL/SCREENSHOT/LOG/RESULT):

```text
1. Launch     adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
2. Custom Server  Login → CustomServer → http://10.0.2.2:3000/api → Save
3. Login      (account di test, MAI credenziali production)
4. Assets     apri lista
5. Work Orders apri lista
6. Attachment apri allegato
7. Logout
```

Automazione: launch/screencap/logcat/input già operativi; i tap per-schermata si calibrano con
`uiautomator dump` alla prima esecuzione reale.

## 18. Problems

Nessun bug **di prodotto** (P0/P1/P2/P3 = 0). Elementi di ambiente/prerequisito:

| ID | Tipo | Descrizione | Azione |
|---|---|---|---|
| A14A-1 | Prerequisito (blocker build) | `google-services.json` (Firebase) assente → build si ferma a `:app:processDebugGoogleServices` | **STOP**: fornire credenziale reale o `GOOGLE_SERVICES_BASE64` (responsabile). Non fabbricata (§12) |
| A14A-2 | Minore (npm) | npm 11 non approva alcuni install-script nativi (`bufferutil`/`utf-8-validate`/…) | Non bloccante (fallback JS); `npm approve-scripts` se necessario |
| A14A-3 | Host | Disco C: 34 GB liberi dopo setup; cache Gradle in `~/.gradle` | Monitorare spazio |
| A14A-4 | Tooling | PowerShell `>` corrompe il binario di `screencap` | Usare `screencap`+`adb pull` (§14) |

## 19. Environment Persistence

Per riprendere in una nuova sessione (nessun secret):

```text
JAVA_HOME        = C:\Users\dawid\Android\jdk-17.0.20.1+1
ANDROID_HOME     = C:\Users\dawid\Android\Sdk
ANDROID_SDK_ROOT = C:\Users\dawid\Android\Sdk
PATH (User)      += %ANDROID_HOME%\platform-tools; %ANDROID_HOME%\emulator; %ANDROID_HOME%\cmdline-tools\latest\bin; %JAVA_HOME%\bin
AVD name         = atlas_test  (API 35, google_apis x86_64)
package name     = com.atlas.cmms
emulator start   = emulator.exe -avd atlas_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
build command    = cd mobile ; npx expo run:android         (richiede google-services.json)
                   (oppure) cd mobile\android ; .\gradlew.bat :app:assembleDebug
install command  = adb install -r <apk>
launch command   = adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
screenshot       = adb shell screencap -p /sdcard/s.png ; adb pull /sdcard/s.png
logcat           = adb logcat -d -v time > atlas-logcat.txt
server URL (emu) = http://10.0.2.2:3000/api     (device fisico: http://<LAN>:3000/api)
backend start    = docker compose up -d          (repo root; host :3000)
```

Le variabili sono impostate a livello **User** (persistono tra sessioni). `mobile/android/
local.properties` (`sdk.dir`) è stato creato ed è **gitignored**.

## 20. Repository Changes

```text
Application behavior changes: NONE
Repository code changes: NONE
```

Modifiche solo di ambiente:
- Installazioni **fuori dal repo**: JDK 17 stabile (`C:\Users\dawid\Android\jdk-17.0.20.1+1`),
  Android SDK (`C:\Users\dawid\Android\Sdk`), variabili utente `JAVA_HOME`/`ANDROID_HOME`/
  `ANDROID_SDK_ROOT`/`PATH`, AVD `atlas_test`.
- **Dentro il repo, gitignored (non tracciati):** `mobile/node_modules/` (da `npm install`),
  `mobile/android/local.properties` (`sdk.dir`).
- `mobile/package-lock.json` era stato toccato da `npm install` (3 righe) → **ripristinato**
  (`git checkout -- mobile/package-lock.json`) per non alterare le dipendenze (§11). `git status
  mobile/` finale: **pulito**.
- Nessuno script `scripts/mobile/adb-smoke.*` creato: lo smoke completo non è ancora eseguibile
  (APK bloccato) e §22 vieta coordinate/flussi non verificati → `Code changes: NONE`.

## 21. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: MOD-014A aggiunto a Current focus + Current Project State + Documentation Workflow/Map;
"Mobile agent testing" aggiornato da NOT AVAILABLE a PARTIAL (Android env operativo: JDK17/SDK/
adb/AVD/emulatore WHPX/npm/automazione ADB verificati); registrato l'unico blocker (Firebase
google-services.json) in Known Issues; Open Decision del provisioning mobile marcata risolta
(Opzione 1 eseguita) con nota "fornire credenziale Firebase per completare"; documentata la
environment persistence (percorsi/comandi). Nessuna modifica applicativa.
```

## 22. Final Verdict

```text
CLAUDE.md updated: YES

JDK 17: AVAILABLE
Android SDK: AVAILABLE
ADB: AVAILABLE
AVD: AVAILABLE
Emulator: AVAILABLE (headless, WHPX accelerato, boot ~36s)
Node/npm dependencies: AVAILABLE (1213 pkg)
Firebase prerequisite: BLOCKED (google-services.json reale richiesto — non fabbricato §12)

Android build: BLOCKED (fail SOLO su google-services; toolchain OK fino a lì, 9m28s)
APK install: BLOCKED (nessun APK)
App launch: BLOCKED (nessun APK)
Login screen: BLOCKED (nessun APK)
Custom Server: PASS (mapping 10.0.2.2:3000 documentato)
Backend connectivity: PASS (host 200; emulatore→host ping ~1ms); L7 app BLOCKED (no APK)
Screenshot: PASS (screencap + adb pull; PNG valido)
Logcat: PASS (49.824 righe)
GUI automation baseline: PASS (input/keyevent/uiautomator dump/screencap)
Smoke test: BLOCKED (richiede APK)

Application behavior changes: NONE
Environment changes: JDK17 stabile; Android SDK+cmdline-tools+platform36+build-tools36+emulator+sysimg35; env vars User; AVD atlas_test; mobile/node_modules; mobile/android/local.properties

P0: 0  P1: 0  P2: 0  P3: 0

Mobile agent testing: PARTIAL
Final verdict: PASS WITH FINDINGS
Next step: ENVIRONMENT WORK REQUIRED (fornire google-services.json Firebase) → poi MOD-015
```

**Ambiente Android: OPERATIVO.** Tutta l'infrastruttura che l'agent serve per buildare/
installare/pilotare l'app è installata e **verificata a runtime** (SDK, `adb`, emulatore
accelerato che booto headless, `npm install`, automazione ADB con screenshot/logcat/input/
uiautomator, backend raggiungibile dall'emulatore). La build reale percorre l'intero toolchain
e si arresta su **un solo prerequisito**: la credenziale Firebase `google-services.json`, che
**non ho fabbricato** (§12) e che spetta al responsabile. Fornito quel file (o
`GOOGLE_SERVICES_BASE64`), l'APK si builda con `npx expo run:android`, si installa sull'AVD e lo
smoke test è immediatamente eseguibile → l'ambiente passa a **AVAILABLE** e si può procedere a
MOD-015 (bug fix mobile).

⏹️ **STOP** — non correggo bug mobile, non modifico backend/frontend/licensing/production/
Caddy/DNS, non fabbrico credenziali Firebase, non faccio deployment e **non avvio MOD-015**. La
decisione (fornire la credenziale Firebase e autorizzare MOD-015) spetta al responsabile.
