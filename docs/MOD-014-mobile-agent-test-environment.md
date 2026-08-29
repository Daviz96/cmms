# MOD-014 — Mobile Agent Test Environment

## Obiettivo

Predisporre un ambiente ripetibile affinché Claude Code possa buildare, avviare, installare e testare realmente l'app mobile Atlas, con priorità Android, e determinare cosa è realisticamente possibile fare su iOS.

**Questo MOD NON deve correggere i bug mobile.**

Il responsabile del progetto ha già verificato personalmente:
- Atlas web desktop con le funzionalità precedentemente bloccate dalla licenza;
- connessione dell'app iOS al backend self-hosted;
- presenza di alcuni bug nell'app mobile.

Il problema da risolvere ora è rendere il testing mobile eseguibile e ripetibile da parte del coding agent.

---

## 1. Regole di scope

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
- codice mobile applicativo, salvo eventuali modifiche minime e documentate necessarie esclusivamente all'ambiente di test.

NON correggere i bug mobile.

Se emerge la necessità di una modifica applicativa:
1. documentare il motivo;
2. spiegare la modifica proposta;
3. NON implementarla automaticamente.

---

## 2. Documentazione da leggere

Prima di iniziare leggere:
1. `CLAUDE.md`;
2. `docs/self-hosted-audit/30-mod012-mobile-runtime-acceptance.md`;
3. `docs/self-hosted-audit/31-mod013-go-live-readiness.md`, se presente;
4. documentazione mobile esistente;
5. `package.json`;
6. eventuali `app.json`, `app.config.*`, `eas.json`;
7. configurazioni React Native/Expo;
8. documentazione build presente nel repository.

Non leggere l'intero repository senza necessità.

---

## 3. Identificare la toolchain reale

Verificare nel repository e nell'ambiente:
- React Native;
- Expo;
- Expo Router;
- EAS;
- Metro;
- Gradle;
- Android SDK;
- Xcode;
- CocoaPods;
- Node;
- npm/yarn/pnpm.

Identificare i comandi realmente disponibili.

Non assumere comandi non presenti.

Documentare:
```text
Tool:
Version:
Comando:
Fonte nel repository:
```

---

## 4. Build Matrix

Creare:

| Target | Build | Install | Runtime | GUI Automation | Stato |
|---|---|---|---|---|---|
| Android Emulator | | | | | |
| Android Device | | | | | |
| iOS Simulator | | | | | |
| iOS Device | | | | | |

Usare `SUPPORTED`, `POSSIBLE`, `NOT POSSIBLE`, `NOT TESTED`, `BLOCKED` con motivazione.

---

## 5. Android — priorità

Determinare se l'ambiente può supportare:

```text
Claude Code
  ↓
build
  ↓
APK/dev build
  ↓
Android Emulator
  ↓
install
  ↓
launch
  ↓
GUI test
```

Verificare, se disponibili:

```bash
adb version
adb devices
emulator -list-avds
```

Verificare Android SDK/AVD senza esporre secret.

Se manca l'SDK:
- documentare cosa manca;
- valutare il setup minimo;
- non installare componenti pesanti automaticamente senza necessità.

---

## 6. Android Emulator

Determinare:
- AVD disponibili;
- API level;
- architettura;
- possibilità di avvio headless;
- controllo tramite ADB.

Se esiste già un AVD utilizzabile, verificare realmente avvio, boot e connessione ADB.

Non creare configurazioni complesse se non necessarie.

---

## 7. Android Physical Device

Determinare se è disponibile un device Android utilizzabile dall'agent:
- USB debugging;
- ADB;
- installazione APK;
- accesso alla LAN;
- configurazione Custom Server.

Se non disponibile:

`NOT AVAILABLE`

Non assumere accesso permanente al device personale del responsabile.

---

## 8. Build dell'app

Identificare il metodo ufficiale realmente supportato:
- development build;
- debug APK;
- release APK;
- Expo Go;
- EAS;
- Gradle.

Preferire il percorso che consenta:
- installazione locale;
- debugging;
- log;
- iterazione rapida.

Documentare:
```text
Command:
Artifact:
Location:
Prerequisites:
```

---

## 9. Installazione e avvio

Determinare package name e procedura reale di installazione.

Se supportato:

```bash
adb install <apk>
```

Verificare:
- install;
- launch;
- reinstall/update;
- eventuale clear data solo quando necessario.

Obiettivo minimo:

```text
APP INSTALLED
↓
APP STARTED
↓
LOGIN
↓
CUSTOM SERVER
↓
ATLAS BACKEND
```

---

## 10. Rete

Determinare come il device/emulatore raggiunge l'host Atlas.

Non usare automaticamente `localhost` o `127.0.0.1` dal dispositivo.

Documentare:
- host IP;
- porta;
- URL;
- eventuali peculiarità dell'emulatore.

Non modificare firewall aziendali o production.

---

## 11. GUI Automation

Valutare gli strumenti realmente disponibili:
- ADB;
- `adb shell input`;
- UIAutomator;
- Appium;
- Maestro;
- Detox;
- Expo tooling;
- screenshot ADB.

Preferenza:

```text
strumento già disponibile
>
strumento leggero
>
framework complesso
```

L'obiettivo è ottenere il massimo valore con la minima infrastruttura.

Non introdurre un framework di test complesso se ADB/UIAutomator è sufficiente.

---

## 12. Screenshot

Verificare se è possibile acquisire screenshot, ad esempio:

```bash
adb exec-out screencap -p > screenshot.png
```

Gli screenshot devono poter essere usati per:
- riproduzione bug;
- before/after;
- report.

---

## 13. Log

Determinare come raccogliere:
- `adb logcat`;
- Metro;
- Expo;
- React Native errors;
- JavaScript errors;
- API/network errors.

Raccogliere solo i log necessari allo scenario.

Non inserire secret nei report.

---

## 14. Network/API debugging

Determinare come osservare:
- endpoint;
- HTTP status;
- request/response;
- errori API.

Usare strumenti già disponibili.

NON introdurre proxy MITM o certificati custom durante questo MOD.

Non catturare credenziali o token.

---

## 15. Smoke Test

Definire il minimo smoke test ripetibile:

```text
Launch
↓
Custom Server
↓
Login
↓
Assets
↓
Work Order
↓
Attachment
↓
Logout
```

Se l'automazione completa non è possibile, documentare precisamente quale parte può essere automatizzata.

---

## 16. Preparazione al futuro debugging

L'ambiente deve permettere idealmente:

```text
BUG
↓
REPRODUCE
↓
SCREENSHOT
↓
LOG
↓
FIX
↓
REBUILD
↓
REINSTALL
↓
RETEST
```

Questo diventerà il workflow del successivo MOD-015.

NON correggere i bug in MOD-014.

---

## 17. iOS

Analizzare separatamente:
- macOS;
- Xcode;
- iOS Simulator;
- Apple Developer account;
- signing/provisioning;
- build locale;
- installazione device;
- GUI automation;
- accesso al backend LAN.

Se l'ambiente corrente non è macOS, documentare i limiti.

Non configurare account Apple o servizi cloud senza approvazione.

---

## 18. Remote Mac / CI

Valutare, senza implementare automaticamente:
- Mac locale;
- Mac remoto;
- CI macOS;
- GitHub Actions macOS;
- Expo/EAS;
- altre soluzioni già presenti.

Confrontare:
- costo;
- complessità;
- ripetibilità;
- accessibilità per Claude Code;
- debugging;
- sicurezza.

Fornire una raccomandazione concreta.

---

## 19. Soluzione raccomandata

Alla fine proporre:
- ambiente primario;
- ambiente secondario;
- gestione iOS.

La soluzione deve derivare dall'ambiente reale, non da supposizioni.

---

## 20. Security

NON inserire:
- password;
- JWT;
- refresh token;
- API key;
- Apple credentials;
- Firebase secrets;
- signing keys;
- keystore password.

Non committare:
```text
.keystore
.p12
.mobileprovision
.env
credentials
```

Se servono secret, documentare solo nome e posizione della configurazione.

---

## 21. Repository Changes

Se servono modifiche esclusivamente per il test:
- mantenerle minime;
- documentare file, scopo e motivo;
- non alterare il comportamento production.

Se non servono:

```text
Code changes: NONE
```

---

## 22. Documentation

Produrre:

```text
docs/self-hosted-audit/32-mod014-mobile-agent-test-environment.md
```

Con:
```text
# MOD-014 — Mobile Agent Test Environment
## 1. Objective
## 2. Current Environment
## 3. Mobile Toolchain
## 4. Android Environment
## 5. Android Emulator
## 6. Android Physical Device
## 7. Build Procedure
## 8. Install Procedure
## 9. Runtime Procedure
## 10. GUI Automation
## 11. Screenshot Capture
## 12. Log Collection
## 13. Network Debugging
## 14. Smoke Test
## 15. iOS Environment
## 16. Remote Mac / CI Options
## 17. Recommended Setup
## 18. Repository Changes
## 19. Security
## 20. Known Limitations
## 21. Next Step
## 22. CLAUDE.md Update
## 23. Final Verdict
```

---

## 23. CLAUDE.md

Aggiornare sempre `CLAUDE.md` con:
- Current Project State;
- capacità di mobile testing;
- MOD-014;
- Known Issues;
- Open Decisions;
- Documentation Map.

Indicare chiaramente:

```text
Mobile agent testing:
AVAILABLE / PARTIAL / NOT AVAILABLE
```

---

## 24. Git

Consentito:

```bash
git status
git diff
git diff --check
git log
```

NON eseguire:

```bash
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

---

## 25. Anti-Hallucination

NON inventare:
- comandi;
- toolchain;
- AVD;
- package name;
- build commands;
- iOS capabilities;
- CI configuration.

Verificare prima nel repository e nell'ambiente.

Se qualcosa non è disponibile:

`NOT AVAILABLE`

---

## 26. Definition of Done

MOD-014 è completo quando:
- toolchain identificata;
- Android build path identificato;
- Android runtime path identificato;
- emulator/device status verificato;
- install procedure documentata;
- screenshot procedure documentata;
- log procedure documentata;
- GUI automation valutata;
- smoke test definito;
- iOS limitations documentate;
- eventuale Mac requirement documentato;
- soluzione raccomandata;
- modifiche repository documentate;
- `CLAUDE.md` aggiornato;
- report prodotto.

---

## 27. STOP CONDITION

Al termine:

**STOP.**

NON:
- correggere bug mobile;
- modificare backend;
- modificare frontend;
- modificare licensing;
- fare deployment;
- modificare production;
- configurare Caddy;
- modificare DNS;
- iniziare MOD-015.

Il successivo MOD-015 sarà dedicato ai bug mobile solo dopo aver verificato che l'ambiente di test sia sufficientemente utilizzabile.

---

## 28. Final Output

```text
CLAUDE.md updated: YES/NO
Code changes: NONE / LIST
Android build: AVAILABLE / BLOCKED
Android emulator: AVAILABLE / BLOCKED
Android device: AVAILABLE / BLOCKED
Android GUI automation: AVAILABLE / PARTIAL / BLOCKED
Screenshot capture: AVAILABLE / BLOCKED
Log capture: AVAILABLE / BLOCKED
iOS build: AVAILABLE / BLOCKED
iOS runtime: AVAILABLE / BLOCKED
iOS automation: AVAILABLE / PARTIAL / BLOCKED
Recommended environment: ...
Mobile agent testing: AVAILABLE / PARTIAL / NOT AVAILABLE
P0: X
P1: X
P2: X
P3: X
Final verdict: PASS / PASS WITH FINDINGS / FAIL
Next step: MOD-015 / ENVIRONMENT WORK REQUIRED
```
