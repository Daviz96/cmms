# MOD-014A — Android Test Environment Setup

## Obiettivo

Predisporre un ambiente Android locale ripetibile affinché Claude Code possa buildare, installare, avviare e testare realmente l'app mobile Atlas.

MOD-014 ha stabilito che nell'ambiente attuale mancano Android SDK, ADB ed emulator/AVD. Sono presenti Node/npm, Git, Docker, il progetto Expo/React Native e un JDK 17 portatile.

**Questo MOD NON deve correggere bug mobile.**

## 1. Scope

NON modificare:
- backend;
- frontend web;
- licensing;
- API;
- database;
- Docker production;
- Caddy;
- DNS;
- certificati;
- production.

NON fare deployment.

NON iniziare MOD-015.

Sono consentiti esclusivamente setup e configurazione dell'ambiente locale di test Android, installazione delle dipendenze già definite dal progetto, build locale, installazione APK, smoke test e documentazione.

## 2. Fonti

Leggere prima:
1. `CLAUDE.md`;
2. `docs/self-hosted-audit/32-mod014-mobile-agent-test-environment.md`;
3. documentazione mobile esistente;
4. `mobile/package.json`;
5. `mobile/app.config.ts`;
6. `mobile/eas.json`;
7. documentazione build del repository.

Non analizzare l'intero repository.

## 3. Verifica iniziale

Prima di installare componenti verificare:

```powershell
Get-Command java -ErrorAction SilentlyContinue
Get-Command adb -ErrorAction SilentlyContinue
Get-Command emulator -ErrorAction SilentlyContinue
Get-Command sdkmanager -ErrorAction SilentlyContinue
Get-Command avdmanager -ErrorAction SilentlyContinue
node --version
npm --version
```

Verificare anche CPU architecture, RAM e spazio disco.

Documentare lo stato iniziale.

## 4. JDK 17

Il progetto richiede un JDK 17 stabile per il build Android.

Il MOD-014 ha rilevato un JDK 17 portatile in scratchpad. Non utilizzare un percorso temporaneo come soluzione definitiva.

Se necessario predisporre un JDK 17 stabile.

Verificare:

```powershell
java -version
```

Configurare `JAVA_HOME` solo localmente.

Non modificare production.

## 5. Android SDK

Installare/configurare Android SDK se autorizzato dal responsabile.

Preferire command-line tools, senza installare Android Studio se non necessario.

Componenti necessari da determinare e installare:

```text
platform-tools
platforms;android-36
build-tools;36.0.0
emulator
system image compatibile con l'host
```

Il progetto dichiara `compileSdkVersion/targetSdkVersion = 36`.

Verificare la compatibilità reale della system image prima di sceglierla.

Non installare componenti inutili.

## 6. Environment Variables

Configurare localmente, se necessario:

```text
ANDROID_HOME
ANDROID_SDK_ROOT
PATH
JAVA_HOME
```

Verificare:

```powershell
adb version
sdkmanager --version
avdmanager --version
emulator -version
java -version
```

Documentare percorsi e versioni.

## 7. SDK Components

Usare:

```powershell
sdkmanager --list
```

Installare soltanto i componenti necessari.

Non eseguire aggiornamenti indiscriminati.

Non usare `npm update` o `npm audit fix` per risolvere problemi di questo setup.

## 8. AVD

Creare un AVD dedicato Atlas, preferibilmente:

```text
atlas_test
```

se il nome non è già utilizzato.

Preferire una system image Google APIs x86_64 se compatibile con l'host.

Documentare:

```text
AVD name:
API level:
Image:
Architecture:
RAM:
GPU:
```

## 9. Emulator

Avviare l'AVD e verificare:

```powershell
adb devices
```

Attendere il boot completo.

Verificare che l'emulatore sia realmente nello stato:

```text
device
```

e non semplicemente che il processo emulator sia avviato.

Verificare `sys.boot_completed`.

## 10. Headless

Valutare se l'emulatore può essere utilizzato headless dall'agent.

Non sacrificare la stabilità.

Se `-no-window` non funziona in modo affidabile, usare una modalità con display virtuale/desktop e documentare la scelta.

## 11. Repository Dependencies

Entrare in `mobile`.

Verificare `package-lock.json` e versione Node.

Installare le dipendenze esistenti:

```powershell
cd mobile
npm install
```

Non aggiornare arbitrariamente le dipendenze.

## 12. Firebase

Il MOD-014 ha rilevato che:

```text
mobile/android/app/google-services.json
```

è assente.

NON inventare un file Firebase.

NON committare credenziali.

Verificare dal codice se il build locale richiede obbligatoriamente il file o se esiste una modalità documentata tramite `GOOGLE_SERVICES_BASE64` o configurazione equivalente.

Se è necessaria una credenziale reale:

```text
STOP
documentare il requisito
```

Non procurarla autonomamente.

## 13. Build Android

Dopo aver soddisfatto i prerequisiti utilizzare il percorso ufficiale del repository.

Preferenza, se confermata dal progetto:

```powershell
npx expo run:android
```

Non inventare comandi.

Obiettivo:

```text
BUILD SUCCESS
↓
APK GENERATED
```

Documentare comando, durata, artifact e percorso.

## 14. Installazione

Package Android:

```text
com.atlas.cmms
```

Installare l'APK sull'AVD.

Se appropriato:

```powershell
adb install -r <apk>
```

Verificare l'installazione.

## 15. Avvio

Avviare:

```powershell
adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
```

Verificare:

```text
APP STARTED
LOGIN SCREEN VISIBLE
```

Se fallisce:
- screenshot;
- logcat;
- classificazione;
- STOP se serve una modifica applicativa.

Non correggere il bug.

## 16. Custom Server

Per Android Emulator il backend locale host è normalmente raggiungibile tramite:

```text
http://10.0.2.2:3000/api
```

Non usare `localhost`/`127.0.0.1` dal dispositivo.

Verificare realmente la raggiungibilità.

Non modificare il backend per adattarlo al test.

## 17. Primo Runtime Test

Eseguire:

```text
Launch
↓
Custom Server
↓
http://10.0.2.2:3000/api
↓
Save
↓
Login
```

Usare account di test.

Non usare credenziali production.

## 18. GUI Automation Baseline

Prima di installare Appium, Detox o Maestro verificare ADB + UIAutomator.

Testare:

```powershell
adb shell input tap ...
adb shell input text ...
adb shell input keyevent ...
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml
```

Verificare screenshot:

```powershell
adb exec-out screencap -p > screenshot.png
```

e log:

```powershell
adb logcat -c
adb logcat -d -v time > atlas-logcat.txt
```

Le coordinate devono essere ricavate dall'interfaccia reale. NON inventarle.

## 19. Smoke Test

Quando il baseline funziona, eseguire almeno:

```text
1. Launch
2. Login
3. Open Assets
4. Open Work Orders
5. Open Attachment
6. Logout
```

Per ogni step registrare:

```text
EXPECTED:
ACTUAL:
SCREENSHOT:
LOG:
RESULT:
```

Se uno step richiede intervento manuale, indicarlo esplicitamente.

## 20. Test Failure Handling

Se emerge un errore:

```text
FAIL
↓
screenshot
↓
logcat
↓
identify layer
↓
document
↓
STOP if code change required
```

NON correggere il codice.

## 21. Persistence

Documentare affinché una nuova sessione possa riprendere il lavoro:

```text
SDK location
JAVA_HOME
ANDROID_HOME
AVD name
API level
package name
build command
install command
launch command
screenshot command
log command
server URL
```

Non documentare secret.

## 22. Optional Helper Script

Solo se il flusso è stato realmente verificato, valutare:

```text
scripts/mobile/adb-smoke.ps1
```

Lo script deve contenere solo operazioni deterministicamente verificate.

Non inserire coordinate arbitrarie.

Se non è necessario:

```text
Code changes: NONE
```

## 23. Repository Changes

Ogni modifica deve essere esclusivamente relativa all'ambiente di test.

Documentare file e motivo.

Il comportamento applicativo deve rimanere invariato:

```text
Application behavior changes: NONE
```

## 24. Security

NON inserire o committare:

```text
google-services.json reale
.keystore
.jks
.p12
.mobileprovision
.env con secret
credenziali Expo
credenziali Apple
credenziali Firebase
```

Non catturare password/JWT/API key nei log o report.

## 25. Documentation

Produrre:

```text
docs/self-hosted-audit/33-mod014a-android-test-environment-setup.md
```

Con:

```text
# MOD-014A — Android Test Environment Setup
## 1. Objective
## 2. Host Environment
## 3. JDK
## 4. Android SDK
## 5. ADB
## 6. AVD
## 7. Emulator
## 8. Repository Dependencies
## 9. Firebase Prerequisites
## 10. Build
## 11. APK Installation
## 12. Runtime
## 13. Custom Server
## 14. Screenshot
## 15. Logcat
## 16. GUI Automation
## 17. Smoke Test
## 18. Problems
## 19. Environment Persistence
## 20. Repository Changes
## 21. CLAUDE.md Update
## 22. Final Verdict
```

## 26. CLAUDE.md

Aggiornare sempre `CLAUDE.md` con:

- Current Project State;
- Mobile Agent Testing;
- MOD-014A;
- Android environment;
- SDK/ADB/AVD;
- build procedure;
- GUI automation;
- Known Issues;
- Open Decisions;
- Documentation Map.

Indicare con evidence:

```text
Mobile agent testing:
AVAILABLE / PARTIAL / NOT AVAILABLE
```

## 27. Git

Consentito:

```powershell
git status
git diff
git diff --check
git log
```

NON eseguire:

```text
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

## 28. Anti-Hallucination

NON inventare:

- SDK path;
- API level;
- AVD;
- package name;
- build commands;
- Firebase configuration;
- server URL;
- coordinate;
- capacità dei tool.

Verificare tutto nell'ambiente e nel repository.

## 29. Definition of Done

MOD-014A è completo quando:

- JDK 17 stabile disponibile oppure requisito documentato;
- Android SDK configurato;
- ADB funzionante;
- AVD creato o device Android disponibile;
- emulator/device raggiungibile;
- `npm install` completato;
- prerequisito Firebase classificato;
- APK buildato oppure blocker documentato;
- APK installato;
- app avviata;
- Login screen verificata;
- Custom Server verificato;
- backend locale raggiungibile;
- screenshot funzionante;
- logcat funzionante;
- GUI automation baseline verificata;
- smoke test eseguito almeno parzialmente;
- environment persistence documentata;
- `CLAUDE.md` aggiornato;
- report prodotto.

## 30. STOP CONDITION

Al termine:

**STOP.**

NON:
- correggere bug mobile;
- modificare backend;
- modificare frontend;
- modificare licensing;
- modificare production;
- modificare Caddy;
- modificare DNS;
- fare deployment;
- iniziare MOD-015.

Se l'ambiente è operativo, il responsabile analizzerà il report e autorizzerà successivamente MOD-015.

## 31. Final Output

```text
CLAUDE.md updated: YES/NO

JDK 17: AVAILABLE / BLOCKED
Android SDK: AVAILABLE / BLOCKED
ADB: AVAILABLE / BLOCKED
AVD: AVAILABLE / BLOCKED
Emulator: AVAILABLE / BLOCKED
Node/npm dependencies: AVAILABLE / BLOCKED
Firebase prerequisite: AVAILABLE / BLOCKED / NOT REQUIRED

Android build: PASS / FAIL / BLOCKED
APK install: PASS / FAIL / BLOCKED
App launch: PASS / FAIL / BLOCKED
Login screen: PASS / FAIL / BLOCKED
Custom Server: PASS / FAIL / BLOCKED
Backend connectivity: PASS / FAIL / BLOCKED
Screenshot: PASS / FAIL / BLOCKED
Logcat: PASS / FAIL / BLOCKED
GUI automation baseline: PASS / PARTIAL / BLOCKED
Smoke test: PASS / PARTIAL / BLOCKED

Application behavior changes: NONE / LIST
Environment changes: LIST

P0: X
P1: X
P2: X
P3: X

Mobile agent testing:
AVAILABLE / PARTIAL / NOT AVAILABLE

Final verdict:
PASS / PASS WITH FINDINGS / FAIL

Next step:
MOD-015 / ENVIRONMENT WORK REQUIRED
```
