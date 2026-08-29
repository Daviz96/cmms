# MOD-014B — Firebase Configuration & Android Build

## Obiettivo
Completare l'unico blocker di MOD-014A: la configurazione Firebase Android (`google-services.json`), quindi verificare build, installazione, avvio e smoke test dell'app sull'AVD `atlas_test`.

MOD-014A ha già verificato JDK 17, Android SDK, ADB, AVD, WHPX, npm, screenshot, logcat e UIAutomator. Non correggere bug mobile in questo MOD.

## Scope
Consentito:
- verificare la configurazione Firebase reale;
- usare un `google-services.json` reale fornito dal responsabile oppure `GOOGLE_SERVICES_BASE64` se già supportato;
- predisporre il file/variabile solo localmente;
- verificare che il file non sia tracciato da Git;
- buildare l'APK;
- installarlo sull'AVD;
- avviare l'app;
- verificare Custom Server e smoke test;
- documentare bug osservati;
- aggiornare documentazione e `CLAUDE.md`.

NON:
- fabbricare Firebase credentials;
- creare/modificare un progetto Firebase;
- modificare backend/frontend/licensing/API/DB;
- correggere bug mobile;
- modificare production/Caddy/DNS;
- fare deployment;
- iniziare MOD-015.

## Fonti
Leggere:
1. `CLAUDE.md`
2. `docs/self-hosted-audit/33-mod014a-android-test-environment-setup.md`
3. `mobile/app.config.ts`
4. `mobile/android/app/build.gradle`
5. `mobile/android/build.gradle`
6. documentazione Firebase/mobile pertinente.

Non analizzare l'intero repository.

## Firebase
Verificare senza stampare valori:
- `mobile/android/app/google-services.json`
- eventuali `.env*`
- `GOOGLE_SERVICES_BASE64`
- logica in `mobile/app.config.ts`.

Verificare Git:
```powershell
git status --short
git check-ignore -v mobile/android/app/google-services.json
git ls-files mobile/android/app/google-services.json
```

Il file Firebase reale NON deve essere committato. Non riportare nel report API key, project ID, app ID o altri valori del file.

Se manca la credenziale reale:
```text
BLOCKED — USER INPUT REQUIRED
```
e STOP.

## Build
Con la configurazione reale predisposta, usare il comando ufficiale già verificato:
```powershell
cd mobile
npx expo run:android
```
oppure altro comando già documentato nel progetto.

Documentare comando, esito, durata e percorso APK. Non modificare dipendenze arbitrariamente.

## APK
Package:
```text
com.atlas.cmms
```

Installare sull'AVD:
```powershell
adb install -r <apk>
adb shell pm list packages | findstr com.atlas.cmms
```

## Runtime
Avviare:
```powershell
adb shell monkey -p com.atlas.cmms -c android.intent.category.LAUNCHER 1
```

Verificare schermata login.

Per l'Android Emulator usare:
```text
http://10.0.2.2:3000/api
```
Non usare `localhost`/`127.0.0.1` dal device.

Usare esclusivamente account di test.

## Smoke test
Eseguire:
```text
Launch
→ Custom Server
→ Login
→ Assets
→ Work Orders
→ Logout
```

Per ogni problema:
```text
EXPECTED:
ACTUAL:
SCREENSHOT:
LOG:
RESULT:
```

NON correggere i problemi. Eventuali bug saranno gestiti da MOD-015.

## GUI
Confermare che rimangano funzionanti:
- ADB input;
- UIAutomator;
- screenshot;
- logcat.

Non installare Appium/Detox/Maestro se ADB + UIAutomator sono sufficienti.

## Sicurezza
NON committare o riportare:
- `google-services.json`;
- API key;
- JWT;
- password;
- token;
- keystore;
- altri secret.

## Documentazione
Produrre:
```text
docs/self-hosted-audit/34-mod014b-firebase-android-build.md
```

Sezione minima:
```text
# MOD-014B — Firebase Configuration & Android Build
## 1. Objective
## 2. Firebase Configuration
## 3. Git/Security Check
## 4. Build
## 5. APK
## 6. Installation
## 7. Runtime
## 8. Custom Server
## 9. Smoke Test
## 10. GUI Automation
## 11. Bugs Observed
## 12. Known Issues
## 13. CLAUDE.md Update
## 14. Final Verdict
```

## CLAUDE.md
Aggiornare sempre `CLAUDE.md` con stato Firebase, Android build e Mobile Agent Testing.

Se build + install + runtime sono operativi:
```text
Mobile agent testing: AVAILABLE
```
Altrimenti:
```text
Mobile agent testing: PARTIAL
```

## Git
Consentito:
```powershell
git status
git diff
git diff --check
git log
```

NON:
```text
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

## Definition of Done
- Firebase reale predisposto oppure blocker documentato;
- file non tracciato da Git;
- build eseguito;
- APK generato;
- APK installato;
- app avviata;
- login verificato;
- Custom Server verificato;
- smoke test eseguito;
- GUI baseline verificata;
- eventuali bug documentati;
- `CLAUDE.md` aggiornato;
- report prodotto.

## STOP
Al termine fermarsi. NON correggere bug, NON modificare production e NON iniziare MOD-015 automaticamente.

## Final Output
```text
CLAUDE.md updated: YES/NO
Firebase config: PRESENT / ABSENT
Tracked by Git: YES / NO
Android build: PASS / FAIL / BLOCKED
APK: GENERATED / NOT GENERATED
APK install: PASS / FAIL / BLOCKED
App launch: PASS / FAIL / BLOCKED
Login: PASS / FAIL / BLOCKED
Custom Server: PASS / FAIL / BLOCKED
GUI automation: PASS / PARTIAL / BLOCKED
Smoke test: PASS / PARTIAL / BLOCKED
Bugs observed: NONE / LIST
P0: X
P1: X
P2: X
P3: X
Mobile agent testing: AVAILABLE / PARTIAL / NOT AVAILABLE
Final verdict: PASS / PASS WITH FINDINGS / FAIL
Next step: MOD-015 / USER INPUT REQUIRED
```
