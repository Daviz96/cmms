# MOD-015 — Mobile Bug Fix & Regression Verification

## 1. Contesto

MOD-014B è completato con esito `PASS WITH FINDINGS`. L'ambiente mobile è ora realmente operativo: Android SDK, ADB, AVD `atlas_test`, Firebase reale gitignored, build APK, installazione, avvio, Custom Server, login, smoke test, ADB/UIAutomator, screenshot e logcat sono verificati.

Il primo test reale ha evidenziato:

```text
M-BUG-1
TypeError: Cannot read property 'endsWith' of undefined
mobile/slices/instanceConfig.ts
```

È non bloccante, ma deve essere riprodotto, compreso e corretto.

Questo MOD apre la fase di debugging mobile.


## 2. Obiettivo

Usare il ciclo:

```text
REPRODUCE → IDENTIFY → UNDERSTAND → FIX → BUILD → INSTALL → RETEST → REGRESSION → DOCUMENT
```

Il primo target obbligatorio è M-BUG-1. Altri bug possono essere trattati solo se realmente riproducibili o supportati da evidenze.


## 3. Scope

Le modifiche al codice mobile sono autorizzate.

NON modificare autonomamente backend, frontend web, licensing, database, API backend, Docker production, Caddy, DNS, certificati o production.

Se la soluzione richiede una modifica backend/API/architetturale: **STOP, documentare e chiedere approvazione**.

Non allargare lo scope senza necessità.


## 4. Fonti

Prima di modificare codice leggere:
1. `CLAUDE.md`
2. `docs/self-hosted-audit/34-mod014b-firebase-android-build.md`
3. documentazione mobile pertinente
4. `mobile/slices/instanceConfig.ts`
5. file direttamente importati/usati dal relativo flusso
6. `mobile/app.config.ts`

Non analizzare l'intero repository.

## 5. Analisi M-BUG-1

Prima del fix determinare:
- quale valore è `undefined`;
- quale chiamata usa `.endsWith()`;
- da dove proviene il valore;
- quale configurazione dovrebbe alimentarlo;
- differenza dev/store/EAS;
- eventuale relazione con `API_URL`, Custom Server e instance configuration.

Non sostituire semplicemente `undefined` con un valore arbitrario. Eliminare la causa.

Produrre:

```text
Root cause:
Affected code:
Input/value:
Why undefined:
Why current code fails:
Why proposed fix is correct:
```


## 6. Riproduzione

Riprodurre M-BUG-1 prima del fix e registrare ambiente, build type, AVD, versione, server URL, passi, expected e actual. Acquisire screenshot, logcat e stack trace senza secret.

## 7. Fix

Il fix deve essere minimo, locale, coerente con l'architettura, compatibile con Custom Server e privo di hardcoded environment-specific values.

Non introdurre nuove dipendenze senza necessità dimostrata. Non modificare API/backend per aggirare il problema.

## 8. Verifica

Dopo il fix:

```text
static checks → build → install → launch → scenario originale → verifica errore assente
```

Non dichiarare risolto il bug solo perché il codice compila.

## 9. Regression Test

Ripetere almeno:

```text
Launch → Login → Custom Server → Dashboard → Work Orders → Assets → Settings → Logout
```

Verificare autenticazione, sessione, navigazione, dati, Custom Server e logout.

## 10. Instance Configuration

Verificare request, response, parsing, validità delle stringhe/URL, assenza di console error e toast error.

Se il backend restituisce dati non conformi, non modificarlo autonomamente.


## 11. Altri bug mobile

Dopo M-BUG-1 esplorare brevemente solo Login, Custom Server, Dashboard, Work Orders, Assets, Settings e Logout.

Un nuovo bug entra nel MOD solo se è riproducibile, distinto, nello scope e supportato da evidenze.

Per ogni bug:

```text
ID / Severity / Screen / Steps / Expected / Actual / Evidence / Root cause / Fix / Regression
```

Severity:
```text
P0 = blocco completo/perdita dati critica
P1 = funzione critica inutilizzabile
P2 = funzione importante con workaround/degrado significativo
P3 = problema minore/UX/non bloccante
```

## 12. Build e qualità

Ogni fix richiede un build reale. Eseguire, quando disponibili, typecheck, lint, Android build, installazione e runtime test.

Usare solo script realmente presenti.

Controllare TypeScript, import inutilizzati, error handling, naming, duplicazioni, compatibilità Expo/React Native ed effetti collaterali.

Non fare refactoring generale.


## 13. GUI e framework

Usare l'infrastruttura già predisposta:

```text
ADB + UIAutomator + screenshot + logcat
```

Le coordinate devono essere ricavate dall'UI reale.

Non introdurre Appium/Detox/Maestro salvo necessità concreta e documentata.

## 14. Git

Consentito:

```powershell
git status
git diff
git diff --check
git log
```

NON usare `git reset --hard`, `git clean`, `git checkout .`, `git push` o `git force-push`. Non creare commit senza autorizzazione.

## 15. Security

NON committare o riportare `google-services.json`, password, JWT, refresh token, API key, keystore o altri secret. Verificare che Firebase continui a essere gitignored.


## 16. Documentation

Produrre:

```text
docs/self-hosted-audit/35-mod015-mobile-bug-fix.md
```

con:

```text
# MOD-015 — Mobile Bug Fix & Regression Verification
## 1. Objective
## 2. Scope
## 3. M-BUG-1
### 3.1 Reproduction
### 3.2 Root Cause
### 3.3 Fix
### 3.4 Verification
### 3.5 Regression
## 4. Additional Bugs
## 5. Tests
## 6. Build
## 7. Runtime Verification
## 8. Security
## 9. Repository Changes
## 10. CLAUDE.md Update
## 11. Known Issues
## 12. Final Verdict
```

Aggiornare sempre `CLAUDE.md` con stato mobile, MOD-015, bug risolti/rimanenti e Documentation Map. Non copiarvi il dettaglio completo del bug.


## 17. Definition of Done

M-BUG-1 è completo quando è stato riprodotto prima del fix, la root cause è identificata, il fix è implementato, il build reale passa, l'APK è installato, lo scenario originale passa dopo il fix, il regression test passa, i controlli disponibili sono eseguiti e la documentazione è aggiornata.

Gli altri bug devono essere `FIXED + VERIFIED` oppure `DOCUMENTED + DEFERRED` con motivazione.

## 18. STOP Conditions

Fermarsi e chiedere approvazione se il fix richiede backend/API, modifica architetturale, nuova dipendenza significativa, schema DB, modifica production o se il comportamento corretto non è determinabile.

Ordine di verifica anti-hallucination:

```text
documentazione → codice → test/evidenze runtime → chiarimento
```

Non inventare requisiti, API, response schema, configurazioni, fallback, comportamento atteso, cause o dipendenze.

## 19. Final Output

```text
CLAUDE.md updated: YES/NO
M-BUG-1: Reproduced YES/NO | Root cause YES/NO | Fix YES/NO
Build: PASS/FAIL/BLOCKED
Install: PASS/FAIL/BLOCKED
Runtime: PASS/FAIL/BLOCKED
Regression: PASS/FAIL/BLOCKED
Additional bugs: NONE/LIST
Typecheck: PASS/FAIL/N/A
Lint: PASS/FAIL/N/A
GUI verification: PASS/FAIL
Application behavior changes: LIST
Backend changes: NONE/LIST
Production changes: NONE/LIST
Security issues: NONE/LIST
P0: X  P1: X  P2: X  P3: X
Final verdict: PASS / PASS WITH FINDINGS / FAIL
Next step: MOD-016 / ADDITIONAL BUG FIX / USER DECISION
```

## 20. Regola finale

Un bug runtime è `FIXED` solo con:

```text
riproduzione prima
+ fix
+ build reale
+ installazione
+ runtime reale
+ scenario ripetuto
+ assenza del problema
+ regression test
```

Altrimenti resta `UNVERIFIED`.

Al termine: **STOP**. Non iniziare MOD-016 o altre attività automaticamente.
