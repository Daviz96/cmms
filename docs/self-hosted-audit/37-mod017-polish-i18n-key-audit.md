# MOD-017 — Polish i18n Key Integrity & Literal UI Audit

Audit sistematico delle **literal i18n key** del client mobile con impatto sulla **lingua
polacca**. Individuati con un detector dedicato tutti i `t('…')` la cui chiave è assente dalla
lingua base (`en.ts`) → i18next mostra la chiave grezza (fallback), che in polacco appare come
testo inglese non tradotto (o come identificatore snake_case). Corretti i casi con effettivo
impatto PL con **diff minimo**; nessuna modifica a backend/API/DB/licensing/production, nessuna
lingua diversa dal polacco toccata (l'inglese è usato solo come riferimento tecnico e ha ricevuto
le sole chiavi base indispensabili a far funzionare le nuove key).

```text
Backend changes: NONE
Production changes: NONE
Other-language changes: NONE (en base: +7 chiavi tecniche indispensabili; nessun de/fr/it/es/…)
Application behavior changes: 3 t('literal') → t('key') (riuso di chiavi esistenti); 7 nuove key en+pl
```

> **Esito in una riga:** 472 chiavi `t()` analizzate → **11 literal key** in fallback →
> **7 nuove chiavi** (en+pl) + **3 code fix** (riuso di chiavi esistenti) + **1 non-bug** (`NFC`,
> acronimo). Static check: en/pl **1338 = 1338**, missing 0, extra 0, duplicati 0, placeholder
> mismatch 0. **Release APK reale** buildato, installato su AVD `atlas_test`, e a runtime in
> **polacco**: `Version`→**Wersja**, `Sign out`→**Wyloguj się**, `Notifications`→**Powiadomienia**,
> profilo `informations`→**Informacje** / `notifications`→**Powiadomienia**; **regression completa
> PASS**. → **MOD-017 PASS.**

---

## 1. Objective

Individuare e correggere i problemi i18n che producono una UI **polacca** non tradotta a causa di
literal English key passate a `t(...)` e assenti dalla lingua base (fallback sulla chiave). Target
obbligatori: **`Sign out`** e **`Version`**. Estendere l'analisi ai casi analoghi con **effettivo
impatto PL**, con diff minimo e senza toccare le altre lingue.

## 2. Localization Architecture

Verificato nel codice (non assunto) — `mobile/i18n/i18n.ts`:

```text
translation library : i18next + react-i18next (initReactI18next), compatibilityJSON 'v3'
base locale         : en   (import ./translations/en → resources.en.translation)
Polish locale       : pl   (import ./translations/pl → resources.pl.translation)
fallback behavior   : lng:'en', fallbackLng:'en'  → chiave mancante ovunque ⇒ mostra la chiave grezza
keySeparator        : false  (chiavi piatte; ammessi spazi/punti nel nome chiave)
interpolation       : escapeValue:false ; placeholder {{double}}
language switching   : AuthContext.tsx:706 → i18n option lng = companySettings.generalPreferences.language.toLowerCase()
                       (enum backend Language.PL → 'pl'); changeLanguage() su cambio lingua azienda
```

Conseguenza chiave: se `t('X')` usa una `X` non presente in `en.ts`, con lingua `pl` i18next **non**
trova la chiave né in `pl` né nel fallback `en` → rende **`X` grezza**. Per una label inglese
letterale ciò significa UI inglese in mezzo al polacco; per una chiave snake_case significa mostrare
l'identificatore (es. `location_update_failure`).

## 3. Polish Scope

Solo polacco. `en.ts` = riferimento tecnico; ricevute **solo** le 7 chiavi base indispensabili a far
risolvere le nuove key (senza le quali sarebbero "extra key" in `pl` e la UI resterebbe in fallback).
**Nessuna** modifica a de/fr/it/es/altre lingue, backend, API, DB, licensing, production, Caddy, DNS.
Nessun refactoring, nessuna nuova dipendenza.

## 4. Literal Key Audit

Creato un detector statico (sez. 9) che estrae ogni `t('…')`/`t("…")` a stringa letterale dal
sorgente mobile (`components, constants, contexts, hooks, navigation, plugins, screens, slices,
store, utils`; esclusi `node_modules/android/ios/.expo/assets`) e segnala le chiavi **assenti da
`en.ts`** (= fallback su chiave grezza). Risultato: **472** chiavi `t()` distinte, **11** mancanti da
`en.ts` (0 presenti in `pl`).

| # | Key | File:Line | Tipo | Visibile UI | Impatto PL |
|---|---|---|---|---|---|
| 1 | `Sign out` | screens/SettingsScreen.tsx:55,114 | literal label | Sì (voce lista + dialog) | Mostra "Sign out" in PL |
| 2 | `Version` | screens/SettingsScreen.tsx:128 | literal label | Sì (voce lista) | Mostra "Version" in PL |
| 3 | `Dev Info` | screens/SettingsScreen.tsx:65 | literal label | Solo dev (6 tap) | Basso (dialog dev) |
| 4 | `Build ID` | screens/SettingsScreen.tsx:67 | literal label | Solo dev | Basso (dialog dev) |
| 5 | `informations` | screens/peopleTeams/Profile.tsx:344 | literal (titolo sezione) | Sì (profilo persona) | Mostra "informations" grezzo |
| 6 | `hour` | screens/peopleTeams/Profile.tsx:370 | literal (unità "rate / hour") | Solo se `rate>0` | Mostra "hour" in PL |
| 7 | `notifications` | screens/peopleTeams/Profile.tsx:381 | literal (titolo sezione) | Sì (profilo persona) | Mostra "notifications" grezzo |
| 8 | `Description` | utils/fields.ts:791 | **chiave errata di case** | Sì (label campo vendor) | Label "Description" mentre placeholder è "Opis" |
| 9 | `Notifications` | navigation/index.tsx:357 | **chiave errata di case** | Sì (titolo schermata) | Mostra "Notifications" in PL |
| 10 | `location_update_failure` | screens/locations/EditLocationScreen.tsx:60 | **chiave inesistente** | Sì (snackbar errore) | Mostra l'identificatore grezzo |
| 11 | `NFC` | screens/ScanAssetScreen.tsx:62 | acronimo | Sì (voce lista) | **NESSUNO** (identico in PL) |

Distinzione *intentional key* vs *literal UI text*: per 8/11 la correzione è additiva (nuove key o
riuso); per `NFC` il testo è identico in ogni lingua → **non** un difetto PL.

## 5. Findings

- **P1 (target obbligatori, visibili):** `Sign out`, `Version` — literal label senza chiave base
  → fallback inglese in UI polacca. **Confermati bug PL.**
- **P2 (visibili, non-target):** `informations`, `notifications` (titoli sezione del profilo
  persona), `Notifications` (titolo schermata), `Description` (label campo). Tutti fallback in PL.
- **P3 (bassa visibilità):** `Dev Info`, `Build ID` (dialog dev, 6 tap), `hour` (solo `rate>0`),
  `location_update_failure` (snackbar su fallimento edit lokalizacji).
- **Non-bug:** `NFC` (acronimo, reso identico in ogni lingua) → lasciato invariato, documentato.

Confermati con impatto PL: **10**. Non-bug: **1**.

## 6. Key Corrections

Due strategie coerenti con le convenzioni reali del progetto (in `en.ts` coesistono chiavi
snake_case e chiavi *literal-text*, es. `'no nfc support': 'No NFC Support'`,
`'Floor plan area in m²': …`):

**A. Nuove chiavi (7)** — per literal senza equivalente esistente. Aggiunte a `en.ts` (valore base)
e `pl.ts` (traduzione), raggruppate dopo `confirm_logout`. Le key mantengono il literal usato nel
codice ⇒ **nessuna modifica al componente** (diff minimo, minor rischio di regressione).

**B. Riuso di chiavi esistenti (3 code fix)** — dove una chiave semanticamente equivalente esiste
già: si sostituisce `t('literal')` con `t('key_esistente')` (nessuna nuova chiave).

| Key | Strategia | Azione |
|---|---|---|
| `Sign out` | A | +`'Sign out'` in en+pl |
| `Version` | A | +`Version` in en+pl |
| `Dev Info` | A | +`'Dev Info'` in en+pl |
| `Build ID` | A | +`'Build ID'` in en+pl |
| `informations` | A | +`informations` in en+pl |
| `hour` | A | +`hour` in en+pl |
| `notifications` | A | +`notifications` in en+pl (serve anche al fix #9) |
| `Description` | B | `t('Description')` → `t('description')` (esiste en.ts:264 / pl.ts:273 = "Opis") |
| `Notifications` | B | `t('Notifications')` → `t('notifications')` (riusa la nuova key #7) |
| `location_update_failure` | B | `t('location_update_failure')` → `t('location_edit_failure')` (esiste en.ts:480 / pl.ts:489) |
| `NFC` | — | invariato (non-bug) |

## 7. Polish Translation Corrections

Traduzioni scelte in coerenza con il polacco già presente nel progetto (nessuna inventata):

| Key | en (base) | pl (nuovo) | Note contesto |
|---|---|---|---|
| `Sign out` | `Sign out` | **Wyloguj się** | coerente con `confirm_logout` = "…wylogować?" |
| `Version` | `Version` | **Wersja** | voce lista Settings |
| `Dev Info` | `Dev Info` | **Informacje deweloperskie** | dialog dev |
| `Build ID` | `Build ID` | **Identyfikator kompilacji** | dialog dev |
| `informations` | `Information` | **Informacje** | titolo sezione profilo (contatti) |
| `hour` | `Hour` | **godzina** | unità in "`rate` / godzina" (minuscolo dopo `/`) |
| `notifications` | `Notifications` | **Powiadomienia** | titolo sezione + titolo schermata |

I 3 code fix (sez. 6B) riusano traduzioni **già esistenti e verificate**: `description`="Opis",
`notifications`="Powiadomienia", `location_edit_failure`="Nie można edytować lokalizacji".

Nota base-lingua (documentata come da sez. 8 del prompt): per `informations`, `hour`,
`notifications` il valore inglese passa da chiave grezza a testo corretto ("Information"/"Hour"/
"Notifications"); per `location_edit_failure` l'inglese passa dall'identificatore alla frase
esistente. Sono miglioramenti collaterali dei **fallback** inglesi, non modifiche alle altre lingue
(de/fr/it/… non sono state toccate; erediteranno il fallback `en` come già avviene).

## 8. Key/Placeholder Integrity

Nessun placeholder introdotto/modificato dalle nuove key (stringhe semplici senza `{{…}}`). I code
fix riusano chiavi la cui coppia en/pl è già coerente. Nessun markup/escape/pluralizzazione toccati.

Integrità en↔pl (script, dopo le modifiche):

```text
EN keys: 1338   PL keys: 1338
Missing in PL: 0     Extra in PL: 0
Duplicate keys EN: 0     Duplicate keys PL: 0
Placeholder mismatches: 0
```

## 9. Static Checks

| Check | Comando | Esito |
|---|---|---|
| Literal-key detector (pre-fix) | `node literalkeys.js mobile` | 472 t() key; **11** mancanti da en.ts |
| Literal-key detector (post-fix) | idem | **1** residua = `NFC` (non-bug, documentato) |
| Integrità en↔pl | `node i18ncheck.js …/translations` | 1338=1338; missing 0; extra 0; dup 0; placeholder 0 |
| `git diff --check` (mobile) | — | pulito (solo warning LF→CRLF, informativi) |

Il detector (euristica affidabile, senza dipendenze/framework): estrae i literal `t('…')`/`t("…")`
e li confronta con le chiavi di `en.ts`; le chiavi assenti dalla base sono esattamente quelle che
producono fallback. Limite noto e documentato: chiavi **dinamiche** `t(variabile)` non sono
analizzabili staticamente (non rientrano nell'audit).

## 10. Build

```text
Command : mobile/android/gradlew.bat assembleRelease --no-daemon --console=plain
Esito   : BUILD SUCCESSFUL in 3m 54s (1173 task; 27 executed, 1146 up-to-date)
Artifact: mobile/android/app/build/outputs/apk/release/app-release.apk  (95.5 MB, gitignored, ricreato)
Signing : debug keystore (buildTypes.release → signingConfigs.debug)
JS bundle: Hermes ha ri-bundlato index.js (~808+ moduli) ⇒ le traduzioni modificate sono nel pacchetto
```

APK **release** (non debug): il JS è **bundled** via Hermes (nessuna dipendenza da Metro), quindi le
traduzioni sono realmente compilate nel pacchetto. Build eseguito con emulatore/Docker spenti
(vincolo RAM 15.8 GB), poi riavviati per il runtime.

## 11. Runtime Verification

Installazione pulita (`adb uninstall` + `adb install`) su AVD `atlas_test` (Android 35 x86_64,
headless WHPX). Account di test **fresco** creato via API con lingua PL — `POST /auth/signup`
`{…, language:"PL", timeZone:"Europe/Warsaw"}` → `generalPreferences.language = PL` (confermato via
`GET /company-settings/{id}` = `PL`, account `enabled=true`). Login mobile → l'app imposta
`lng='pl'`. Password **non** riportata (nessun secret). GUI automation: `adb input` +
`uiautomator dump` (coordinate ricavate dall'UI reale) + `screencap`/`pull`.

| Schermata | Key | EXPECTED (pl) | ACTUAL | Esito |
|---|---|---|---|---|
| Ustawienia (Settings) | `Version` | Wersja | **"Wersja"** + "1.0.47" | **PASS** |
| Ustawienia (voce lista) | `Sign out` | Wyloguj się | **"Wyloguj się"** (rosso, icona logout) | **PASS** |
| Dialog conferma logout (pulsante) | `Sign out` | Wyloguj się | titolo "Potwierdzenie", testo "Czy na pewno chcesz się wylogować?", pulsanti "Anuluj" / **"Wyloguj się"** | **PASS** |
| Powiadomienia (titolo schermata) | `Notifications`→`notifications` | Powiadomienia | **"Powiadomienia"** (+ "Brak powiadomienia") | **PASS** |
| Profil persona (sezione) | `informations` | Informacje | **"INFORMACJE"** (stile maiuscolo) | **PASS** |
| Profil persona (sezione) | `notifications` | Powiadomienia | **"POWIADOMIENIA"** | **PASS** |
| Profil persona ("rate / hour") | `hour` | godzina | **non renderizzato** (`user.rate = 0` ⇒ ramo `rate>0` falso) — coerente, verificato staticamente | N/A |

Nessun fallback inglese per le key corrette; nessun errore i18n/console; layout intatto; placeholder
n/d. `Dev Info`/`Build ID` (dialog dev, 6 tap) e `Description` (label form vendor) e
`location_edit_failure` (snackbar su fallimento) non forzati a runtime: corretti e verificati
staticamente (chiavi presenti e coerenti in en/pl). Evidenze: screenshot `s5` (Ustawienia:
Wersja/Wyloguj się), `s10` (Profil: INFORMACJE/POWIADOMIENIA), dump UIAutomator dei testi PL.

## 12. Regression

Flusso completo sull'APK release, account PL:

| Step | ACTUAL (pl) | Errore? | Esito |
|---|---|---|---|
| Launch | avvio pulito, LoginScreen | no | PASS |
| Login | dashboard "Atlas" (Otwarte/Wstrzymany/Dzisiaj/Wysoki priorytet = 0) in PL | no | PASS |
| Dashboard | KPI + "Przypisane tylko do mnie" | no | PASS |
| Zlecenia robocze (Work Orders) | lista: "Żaden element nie spełnia tego kryterium"; filtri "Moja praca/Priorytet/Status" | no | PASS |
| Aktywa (Assets) | schermata caricata ("Szukaj") | no | PASS |
| Ustawienia (Settings) | account, Wersja, Wyloguj się | no | PASS |
| Logout | dialog → Wyloguj się → **LoginScreen** ("Zaloguj się", "Adres e-mail", "Hasło") | no | PASS |

Nessuna regressione funzionale introdotta dalle modifiche i18n. Nota non-bug (attesa): il dialog di
sistema Android dei permessi notifiche è in inglese (OS, non tradotto dall'app); il successivo dialog
**dell'app** è in polacco ("Brak uprawnień do powiadomień").

## 13. Remaining Polish Issues

- **`NFC`** (ScanAssetScreen.tsx:62): literal `t('NFC')` senza chiave base, ma reso **identico** in
  ogni lingua (acronimo). **Non un difetto PL** → lasciato invariato per non introdurre rumore
  (coerente con "non correggere ogni literal", solo impatto effettivo).
- Chiavi **dinamiche** `t(variabile)`: fuori dalla portata dell'analisi statica (limite del detector,
  documentato). Nessun problema PL noto residuo tra le chiavi statiche.
- Push/FCM: verificabile solo su device fisico (invariato, non i18n).

## 14. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: MOD-017 aggiunto a Current focus + Current Project State + Documentation Workflow/Map;
Known Issues aggiornato (literal i18n key PL risolte; NFC documentato come non-bug); aggiunta la
regola sulle translation key (usare chiavi, non literal English; en=riferimento tecnico; verificare
integrità en↔pl). Nessun elenco di chiavi nel CLAUDE.md (dettaglio in questo report).
```

## 15. Final Verdict

```text
CLAUDE.md updated: YES

Localization system: IDENTIFIED (i18next, base en, pl, keySeparator:false, fallbackLng en)
Polish locale: IDENTIFIED (translations/pl.ts; lng da generalPreferences.language.toLowerCase())

Literal keys analyzed: 472 (distinct t() string keys)
Confirmed PL-impact issues: 10   Non-bug: 1 (NFC)

Sign out: FOUND (SettingsScreen.tsx:55,114) — FIXED (+key en/pl = "Wyloguj się"; runtime PASS)
Version:  FOUND (SettingsScreen.tsx:128)    — FIXED (+key en/pl = "Wersja"; runtime PASS)

Additional PL issues: LIST
  Dev Info, Build ID, informations, hour, notifications (new keys en+pl)
  Description, Notifications, location_update_failure (code fix → key esistente/nuova)

Keys created/reused:
  created (7): 'Sign out','Version','Dev Info','Build ID','informations','hour','notifications'
  reused  (3): 'description' (fields.ts), 'notifications' (navigation), 'location_edit_failure' (EditLocationScreen)

Polish corrections: 7 nuove traduzioni pl + 3 code fix (riuso)

Missing PL keys after fix: 0
Extra PL keys: 0
Duplicate keys: 0
Placeholder mismatches: 0

Typecheck: N/A (modifiche = stringhe/argomenti t(); nessun cambiamento di tipi)
Lint: N/A   Translation consistency: PASS (1338=1338)

Android build: PASS (release, BUILD SUCCESSFUL 3m54s, app-release.apk 95.5 MB)
Runtime verification in Polish: PASS (Version, Sign out, Notifications, Profil informations/notifications)
Regression: PASS (Launch→Login→Dashboard→Work Orders→Assets→Settings→Logout)

Other-language changes: NONE (en base: 7 chiavi tecniche indispensabili)
Backend changes: NONE   Production changes: NONE   New dependencies: NONE

Remaining Polish issues: NONE (NFC = non-bug documentato; chiavi dinamiche fuori scope)

Final verdict: PASS
Next step: USER DECISION (MOD-018 / altri audit su richiesta)
```

**Files modified (MOD-017):**

```text
mobile/i18n/translations/en.ts                  (+7)  7 chiavi base per le nuove key
mobile/i18n/translations/pl.ts                  (+7)  7 traduzioni polacche
mobile/navigation/index.tsx                     (±1)  t('Notifications') → t('notifications')
mobile/screens/locations/EditLocationScreen.tsx (±1)  t('location_update_failure') → t('location_edit_failure')
mobile/utils/fields.ts                          (±1)  t('Description') → t('description')
```

`git diff --check` pulito; `git status` mobile = solo questi 5 file (+ i 2 file MOD-015 pre-esistenti
non committati: config.ts, slices/instanceConfig.ts). Nessun commit creato. APK release gitignored.

⏹️ **STOP** — MOD-017 completo. Non avvio MOD-018 né altre attività; non modifico
backend/frontend/licensing/production/Caddy/DNS. Il passo successivo è a decisione del responsabile.
