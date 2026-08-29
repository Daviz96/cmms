# MOD-009 — Mobile Compatibility Audit

Audit dell'app mobile ufficiale (React Native + Expo) del fork Atlas per determinare se
può essere usata con il **backend self-hosted modificato senza modificare il codice
mobile**. **Solo analisi: nessuna modifica.** Secret mascherati (`********`).

`Code changes: NONE.`

Domanda centrale: *possiamo usare l'app mobile ufficiale col nostro Atlas self-hosted
modificato senza modificarla?* → **Sì, con configurazione (server URL custom).**

---

## 1. Objective

Stabilire compatibilità tecnica dell'app mobile con il backend self-hosted: server URL,
API, autenticazione, licensing (Keygen/Paddle), Cloud vs Self-hosted, feature gate,
allegati (incl. MOD-004B), offline. Distinguere compatibilità tecnica da
distribuzione/store. Non modificare il mobile.

---

## 2. Repository Mobile Structure

Percorso reale: **`mobile/`** — app **React Native + Expo** (managed workflow), codebase
JS **condivisa** con cartelle native generate `mobile/android/` e `mobile/ios/`.

```text
mobile/
├── app.config.ts        # Expo config (extra.API_URL, plugins, bundle ids)
├── config.ts            # getApiUrl() — server URL resolution
├── eas.json             # EAS build profiles
├── utils/api.ts         # API client (base URL = getApiUrl())
├── contexts/AuthContext.tsx      # auth, hasFeature, push registration
├── slices/license.ts             # license state (backend-driven)
├── hooks/useLicenseEntitlement.ts
├── screens/auth/{LoginScreen,CustomServerScreen}.tsx
├── screens/… (workOrders, assets, parts, meters, ScanAsset, …)
├── navigation/index.tsx          # routes (incl. CustomServer)
├── android/  ios/                # native projects (generated)
```

Android e iOS **condividono lo stesso codice JS**; differiscono solo per config nativa
(bundle id `com.atlas.cmms` / `com.cmms.atlas`, `google-services.json` /
`GoogleService-Info.plist`). Versione app `1.0.47`.

---

## 3. Android

TEST: Android usa la stessa logica JS? · ACTUAL: sì (Expo managed, `android/` generata;
`app.config.ts` android: `package: com.atlas.cmms`, Firebase `google-services.json`,
Hermes). Nessuna logica licensing/feature specifica Android. RESULT: **A — Compatible
(with configuration)** — identico allo shared code. EVIDENCE: `app.config.ts:49-60`.

---

## 4. iOS

TEST: iOS usa la stessa logica JS? · ACTUAL: sì (`ios/` generata; `bundleIdentifier:
com.cmms.atlas`, `GoogleService-Info.plist`, Hermes). Nessuna logica licensing/feature
specifica iOS. RESULT: **A — Compatible (with configuration)**. EVIDENCE:
`app.config.ts:38-48`.

---

## 5. Shared Mobile Code

Tutta la logica rilevante (server URL, API client, auth, licensing, plan gate,
allegati, offline) è **condivisa** in TypeScript. Le conclusioni seguenti valgono per
Android e iOS insieme.

---

## 6. Server Configuration

**Aspetto chiave — il server è configurabile a runtime, senza modificare il codice.**

`config.ts`:

```text
getApiUrl():
  customUrl = AsyncStorage.getItem('customApiUrl')
  return customUrl || Constants.expoConfig.extra.API_URL   (+ trailing '/')
```

- Il default `API_URL` è un **env di build** (`app.config.ts` → `process.env.API_URL`),
  **non** un dominio Atlas hardcoded nel codice.
- Esiste una **schermata utente dedicata** `screens/auth/CustomServerScreen.tsx`
  (route `CustomServer`, registrata in `navigation/index.tsx:509`, **raggiungibile dal
  `LoginScreen`** via `navigation.navigate('CustomServer')`, `LoginScreen.tsx:148`):
  input "server_url" (placeholder `https://your-server-url.com`), **Save** →
  `AsyncStorage.setItem('customApiUrl', url)`, **Reset to default** →
  `removeItem('customApiUrl')`.

TEST: si può puntare l'app a `https://<nostro-server>/api` senza modificare codice? ·
EXPECTED: sì · ACTUAL: sì — l'utente apre "Custom server" dal login e salva l'URL; da
quel momento `getApiUrl()` restituisce il nostro server e **l'API client lo usa per
tutte le chiamate**. RESULT: **B — Compatible with configuration**. EVIDENCE:
`config.ts:13-26`, `CustomServerScreen.tsx:74`, `navigation/index.tsx:509`,
`LoginScreen.tsx:148`. Nessun blocco a domini Atlas; nessuna disabilitazione TLS.

---

## 7. API Compatibility

`utils/api.ts` costruisce la base URL da `getApiUrl()` su **ogni** metodo
(`api.ts:20/102/114/133/144`) → tutte le chiamate (GET/POST/upload/…) vanno al server
configurato. L'app è il **client ufficiale di questo stesso backend**; gli endpoint
usati (`/auth/*`, `/license/state`, work orders, assets, `/files/upload`, …) sono quelli
del repo. Le modifiche dei MOD precedenti **non hanno cambiato i contratti API**
(MOD-001 licensing interno; MOD-004B ha aggiunto solo un query-param firmato all'URL
presigned e la delete lato service; nessuna firma di endpoint modificata). RESULT:
**A — Compatible** — nessuna API mancante/incompatibile. EVIDENCE: `utils/api.ts`,
`slices/license.ts:32`.

---

## 8. Authentication

L'auth (`contexts/AuthContext.tsx`) usa `getApiUrl()` (582, 710) e gli endpoint auth
standard del backend (signin → token, refresh, whoami), token in AsyncStorage. Ruoli e
permessi vengono dal backend come nel web. Nessun SSO obbligatorio; LDAP resta lato
backend. RESULT: **A — Compatible** (stesso schema JWT del backend verificato in
MOD-005). EVIDENCE: `AuthContext.tsx` (getApiUrl + token handling).

---

## 9. Licensing

TEST: il mobile contatta Keygen o valida licenze localmente? · EXPECTED: no · ACTUAL:
ricerca `api.keygen|keygen.sh|paddle.com|checkout` nel codice → solo **stringhe i18n**
("Checkout"/"Check out our", testo UI), **nessuna chiamata**. L'unico percorso licensing
è `slices/license.ts` → `api.get('license/state')` (**backend**), `initialState
valid:false`. `hooks/useLicenseEntitlement.ts` = `license.valid && entitlements.includes(e)`
(identico al web). RESULT: **PASS / A** — nessuna dipendenza commerciale/Keygen/Paddle;
licensing 100% backend-driven. In self-hosted il backend concede l'intero enum (MOD-001)
→ gate aperti. EVIDENCE: `slices/license.ts`, `useLicenseEntitlement.ts`, grep Keygen/Paddle.

---

## 10. Cloud/Self-hosted

Non esiste una variabile `isCloudVersion`/`CLOUD_VERSION` nel mobile (a differenza del
web): i gate feature dipendono **solo** da backend license/plan/permessi. Dipendenze da
servizi esterni presenti (Class D, **opzionali**, non bloccano l'uso col backend
self-hosted):

| Servizio | Uso | Impatto self-hosted |
|---|---|---|
| Firebase (FCM) + `expo-notifications` | push notification (`registerForPushNotificationsAsync`, AuthContext:557/626) | **D** — la consegna push richiede FCM lato backend + config Firebase; l'app funziona comunque (dati via API). `DA VERIFICARE` per il backend self-hosted |
| Expo Updates (`u.expo.dev/…`) | OTA update del bundle JS | **D** — riguarda distribuzione/aggiornamenti, non la compatibilità col backend |
| Microsoft Clarity (`CLARITY_ID`) | analytics | **D** — opzionale, telemetria |
| Google Maps (`GOOGLE_KEY`) | mappe | **D** — opzionale, richiede key |

Nessuna di queste dipendenze impedisce l'uso delle funzionalità core contro il nostro
backend. RESULT: **D (opzionali)** — nessun blocco commerciale.

---

## 11. Feature Gates

`hasFeature(f)` (AuthContext:1151) = `state.company.subscription.subscriptionPlan.features.includes(f)`
(identico al web). In self-hosted il piano è **BUSINESS con tutte le 17 `PlanFeatures`**
(verificato a runtime, MOD-005) → tutti i gate aperti. Es.: `AuthContext:1158` filtra i
campi file con `hasFeature(PlanFeature.FILE)` → attivo in self-hosted.

| Feature | Gate | Condizione | Effetto | Self-hosted |
|---|---|---|---|---|
| Work Orders / Assets / Parts / Meters | permessi ruolo + API backend | ruolo/permessi | schermate | ✅ usabili |
| Attachments (file/immagini) | `hasFeature(FILE)` + backend gate | piano ha FILE | upload/download | ✅ (BUSINESS ha FILE) |
| Additional Time/Cost | `useLicenseEntitlement`/plan | entitlement/plan | card WO | ✅ (entitlement pieni) |
| QR/Barcode (scan asset) | `expo-camera`/NFC + API | permesso camera | `ScanAssetScreen` | ✅ (nessun gate commerciale) |
| Notifications (push) | Firebase/FCM | token push + backend FCM | notifiche | ⚠️ D (DA VERIFICARE backend FCM) |
| Offline cache | redux-persist (locale) | — | stato persistito | ✅ locale, no cloud |

RESULT: gate = **B (authorization)** + **C soddisfatti** dal backend self-hosted; nessun
gate commerciale bloccante.

---

## 12. Attachments

- **Upload**: `handleFileUpload`/`uploadFiles` (`utils/overall.ts`,
  `contexts/CompanySettingsContext.tsx`) → API client (`getApiUrl`) → endpoint standard
  `/files/upload` del backend (gate `FILE_ATTACHMENTS` + `PlanFeatures.FILE` + perm
  `FILES`, tutti aperti in self-hosted).
- **Download/visualizzazione**: `utils/fileDownload.ts` → `FileSystem.downloadAsync(uri,
  fileUri)` sull'URL **presigned restituito dal backend**, poi apre il file locale;
  le immagini sono mostrate via URI.

TEST: MOD-004B (`Content-Disposition: attachment` sui non-immagine) rompe il mobile? ·
EXPECTED: no · ACTUAL: `downloadAsync` scarica i **byte** in un file locale
**indipendentemente** dall'header `Content-Disposition` (non è una navigazione browser);
le immagini restano inline (MOD-004B non aggiunge disposition alle immagini). RESULT:
**A — Compatible** — la mitigazione stored-XSS del backend è trasparente per il mobile.
EVIDENCE: `fileDownload.ts:14`. Il presigned URL punta a `<PUBLIC_SERVER_URL>/storage`:
col server custom impostato, punta al **nostro** nginx/MinIO.

---

## 13. Offline

- **Persistenza**: `redux-persist` (`store/index.ts`) su AsyncStorage → lo stato è
  cachato localmente tra i riavvii (locale, nessun servizio Cloud).
- **Connettività**: `@react-native-community/netinfo` (`useNetInfo`) usato per rilevare
  la connessione (es. `CreateEntitiesSheet.tsx`).
- **Sync offline delle mutazioni** (coda write offline): non evidente nel codice
  analizzato → **`DA VERIFICARE`**. In ogni caso non introduce dipendenze Cloud (la
  persistenza è locale). RESULT: caching locale **A**; sync completo **G/DA VERIFICARE**.

---

## 14. Runtime Verification

**Non eseguito** (§17): un test runtime mobile richiederebbe build EAS/Expo, signing,
emulatore/dispositivo e config Firebase/store — fuori dallo scope e non introducibile
senza modifiche. L'analisi è statica sul codice. Evidenza correlata: il **backend**
esposto agli stessi endpoint è stato verificato a runtime in MOD-005 (auth, license
state SELF_HOSTED, BUSINESS+FILE, allegati upload/download/delete), quindi il lato server
che l'app consuma è confermato funzionante. RESULT: **runtime mobile = NOT VERIFIED
(requisito documentato)**; compatibilità dedotta staticamente + backend verificato.

---

## 15. Findings

| ID | Classe | Descrizione | Blocca self-hosted? |
|---|---|---|---|
| M-01 | **B** | Server URL configurabile a runtime (`CustomServerScreen`) → l'app punta al nostro backend senza modifiche | No (abilita l'uso) |
| M-02 | **A** | API/auth standard dello stesso backend; contratti invariati dai MOD | No |
| M-03 | **A** | Licensing backend-driven; nessun Keygen/Paddle; entitlement/plan aperti in self-hosted | No |
| M-04 | **A** | Allegati upload/download compatibili; MOD-004B trasparente (download programmatico) | No |
| M-05 | **D** | Dipendenze cloud opzionali (Firebase/FCM push, Expo OTA, Clarity, Maps) | No (opzionali; push `DA VERIFICARE` lato backend FCM) |
| M-06 | **G** | Profondità del sync offline non determinata (caching locale sì; write-sync non evidente) | No |
| M-07 | (distribuzione) | App legata a Expo project/Firebase/bundle id Atlas → una **ri-distribuzione propria** richiederebbe config store proprie | Non è compatibilità tecnica (vedi §19) |

Nessun gate commerciale mobile che blocchi feature backend-disponibili. Nessuna API
incompatibile. Nessuna modifica al codice mobile richiesta per l'uso col self-hosted.

---

## 16. Compatibility Matrix

| Aspetto | Classe | Esito |
|---|---|---|
| Server URL | B | configurabile (CustomServer) |
| API | A | compatibile (stesso backend) |
| Auth | A | compatibile (JWT) |
| Licensing | A | backend-driven, no Keygen |
| Plan/feature gate | A/C-satisfied | aperti (BUSINESS) |
| Attachments (+MOD-004B) | A | compatibile |
| Cloud services (push/OTA/analytics/maps) | D | opzionali, non bloccanti |
| Offline (caching) | A | locale |
| Offline (write-sync) | G | DA VERIFICARE |
| Android | B | compatibile con config |
| iOS | B | compatibile con config |

---

## 17. Required Changes

**Nessuna modifica al codice mobile è richiesta** per usare l'app ufficiale col backend
self-hosted: basta impostare il server URL custom. Requisiti operativi (non modifiche
codice): (a) impostare `https://<nostro-server>/api` nella schermata Custom server; (b)
se si vogliono le **push**, configurare FCM lato backend/Firebase (`DA VERIFICARE`,
Class D). La **ri-distribuzione** con proprio branding/store è una questione separata
(§19), fuori scope.

---

## 18. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-009 (mobile COMPATIBLE WITH CONFIGURATION) a Current Project State,
Documentation Map/Workflow; registrata la decisione approvata "l'app mobile ufficiale è
usabile col self-hosted via server URL custom, senza modifiche"; registrate le open
question D (push/FCM) e G (offline write-sync) come DA VERIFICARE.
```

---

## 19. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
ANDROID: COMPATIBLE WITH CONFIGURATION
IOS: COMPATIBLE WITH CONFIGURATION
SHARED: COMPATIBLE WITH CONFIGURATION (server URL custom; licensing/plan backend-driven; attachments compatible)
Final verdict: PASS
```

**OFFICIAL MOBILE APP: COMPATIBLE WITH CONFIGURATION.** L'app mobile ufficiale (Expo/RN,
Android+iOS shared) può usare il nostro backend self-hosted modificato **senza
modifiche al codice**, impostando il server URL custom dalla schermata dedicata
(raggiungibile dal login). Licensing e feature gate sono backend-driven e aperti in
self-hosted (MOD-001 + BUSINESS); nessun Keygen/Paddle; gli allegati (incl. la
mitigazione MOD-004B) sono compatibili. Dipendenze cloud (push/OTA/analytics/maps) sono
opzionali e non bloccanti.

Distinzione richiesta: **compatibile tecnicamente** con il backend = sì (con
configurazione). **Pubblicabile/distribuibile via store con proprio branding** = non
analizzato in dettaglio qui (richiederebbe Expo project/Firebase/bundle id propri —
questione di distribuzione, non di compatibilità backend).

⏹️ **STOP** — non modifico il mobile, non avvio il Local Acceptance Test né il
deployment live, non modifico frontend/backend/Docker/nginx, non implemento CFG-02. La
decisione successiva spetta al responsabile tecnico dopo la revisione del report.
