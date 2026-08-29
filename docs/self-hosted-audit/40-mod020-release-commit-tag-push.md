# MOD-020 — Release Commit, Tag & Push

Preparazione della baseline versionata (commit/tag/push) della release self-hosted. Il MOD si è
**fermato al gate obbligatorio dei test backend (§9)**: la suite reale, rieseguita con Docker/
Testcontainers, **non è verde** (6 fallimenti). Per le regole del MOD (§9, §24, §25) **non è stato
creato alcun commit, tag o push**. Nessuna modifica di codice.

```text
Code changes in this MOD: NONE
Commit / Tag / Push: NOT PERFORMED (blocked at backend test gate)
```

> **Esito in una riga:** working tree e secret **OK**, ma il **gate test backend FALLISCE**:
> `Tests run: 1446, Failures: 6, Errors: 0` — 6 fallimenti in **`PasswordValidatorTest`** (feature
> "common passwords" **upstream**, committata a HEAD, **fuori dal diff self-hosted**). La baseline
> documentata `1446/1446` (MOD-011) è **STALE**. Per §9/§25 → **DO NOT COMMIT / STOP**. Serve una
> decisione del responsabile prima di poter versionare. → **RELEASE BLOCKED.**

---

## 1. Objective

Trasformare il working tree congelato (MOD-019, READY FOR CODE FREEZE) in una baseline versionata:
final test → secret check → stage selettivo → commit → tag → push → verifica. Decisioni approvate
dal responsabile: **ramo `self-hosted`**, contenuto **codice + test + docs**, tag **`self-hosted-v1.0.0`**.

## 2. Pre-Commit Repository State

```text
Branch (attuale) : main @ e1d24406  (invariato dal MOD-013/018/019)
Tracked modified : 21
Untracked        : 5 test (*ServiceTest.java) + docs/ (70 file)
git diff --check : PULITO (solo warning LF→CRLF)
```

Ogni modifica è attribuibile a un MOD (001/002/003A/004B-C/006/011/015/016/017 + doc 018/019) — vedi
doc 39 §2. **Nessun file non riconducibile al progetto.** `frontend/**` invariato; nessuna migration/
schema; diff licensing = design MOD-001 approvato (verificato in doc 39 §5).

## 3. Secret Audit

`PASS.` Nessun secret reale destinato al commit.

| Controllo | Esito |
|---|---|
| `.env` | gitignored (`.gitignore:1`), **non tracciato** |
| `mobile/android/app/google-services.json` | gitignored (`mobile/.gitignore:19`), **non tracciato** |
| `.env.example` (tracciato) | solo placeholder/commenti (`LDAP_MANAGER_PASSWORD=` vuoto) |
| Scan nuovi file `docs/` + test | 5 match = **falsi positivi** (riferimenti a nomi-variabile placeholder: `<SECRET>`, `<via-secret-manager>`, l'esempio upstream `minio123` citato *come* placeholder) |
| Hard-check JWT reali (`eyJ…`) / chiavi private (`-----BEGIN`) nei nuovi file | **NONE** |

Nessun `SECURITY FINDING`.

## 4. .gitignore Verification

`PASS.` Restano esclusi: `.env`, `mobile/android/app/google-services.json`, `**/build/`, APK
(`android/app/build/outputs/...`), `api/target/` (`git check-ignore api/target` → ignorato). Nessuna
modifica a `.gitignore` necessaria.

## 5. Backend Test Gate

`FAIL — STOP.` Suite reale rieseguita in MOD-020 (Docker Desktop avviato, engine 29.4.3;
Testcontainers `postgres:16-alpine` partito correttamente — **nessun problema d'ambiente/OOM**):

```text
[ERROR] Failures:
  PasswordValidatorTest.loadsPasswordsFromFile              expected: <false> but was: <true>
  PasswordValidatorTest.rejectsCommonKeyboardPattern        expected: <false> but was: <true>
  PasswordValidatorTest.rejectsCommonNumericPattern         expected: <false> but was: <true>
  PasswordValidatorTest.rejectsCommonPasswordCaseInsensitive expected: <false> but was: <true>
  PasswordValidatorTest.rejectsCommonPasswordOf12Chars      expected: <false> but was: <true>
  PasswordValidatorTest.rejectsPasswordFromCommonList       expected: <false> but was: <true>
Tests run: 1446, Failures: 6, Errors: 0, Skipped: 0
BUILD FAILURE
```

Riprodotto in **isolamento** (`mvnw -o test -Dtest=PasswordValidatorTest`): 21 test, **6 failures**
(unit test puro, **senza Docker** → deterministico).

**Root cause (diagnosi, NON corretta — §4/§16):** in `PasswordValidatorTest.loadsPasswordsFromFile`
l'assertion che salta è `assertFalse(loaded.isEmpty())` → **`PasswordValidator.loadCommonPasswords()`
ritorna un Set VUOTO a runtime**, quindi la verifica `checkCommonPasswords` non rifiuta nulla e ogni
password comune è **accettata** (`isValid=true`). Il file `common-passwords.txt` **è presente** in
`api/src/main/resources` **e** in `api/target/classes` (720.979 byte, 46.146 righe, CRLF, no BOM) e
**contiene** tutte le stringhe attese dal test (`grep` verificato: motherfucker, leavemealone,
1qaz2wsx3edc, 123456654321, qwertyqwerty, qwerasdfzxcv). Non è quindi una risorsa mancante: è il
caricamento `ClassPathResource("common-passwords.txt")` che fallisce sotto la JVM di test e l'eccezione
viene **inghiottita** da `catch (Exception ignored)` in `loadCommonPasswords()`.

**Attribuzione:** `PasswordValidator.java`, `PasswordValidatorTest.java`, `common-passwords.txt`,
`PasswordPolicyProperties.java` sono **committati a HEAD** (commit upstream `9fc1a8c8 feat: … minimum
12 characters`, `c84a4e02 test: … reject common passwords`, `08c6773e test: … "motherfucker"`) e
**NON compaiono nel diff self-hosted**. Quindi i 6 fallimenti sono **indipendenti dalle modifiche MOD**:
la baseline `1446/1446` di MOD-011 predata questa feature upstream ed è ora **STALE**; il gate reale di
MOD-020 ha rilevato che l'attuale HEAD **non è verde**.

## 6. Build Verification

- **Backend compile:** `mvnw -o test-compile` = exit 0 (doc 39 §6).
- **Android release APK:** presente (`app-release.apk`, 95,5 MB) — non rigenerato in questo MOD (è
  compito di MOD-021, dal codice committato). Non pertinente al blocco.

## 7. Staged Files

`NONE.` Nessuno staging eseguito — il gate §5 ha imposto STOP prima di `git add`.

## 8. Commit

`NOT CREATED.` (bloccato dal gate test)

## 9. Release Tag

`NOT CREATED.` Tag pianificato: `self-hosted-v1.0.0` (namespace distinto scelto dal responsabile;
esistono tag upstream `v1.0.0…v1.8.0` da cui ci si separa). Non creato.

## 10. Push

`NOT PERFORMED.` Remote previsto: `origin` = `git@github.com:Daviz96/cmms.git` (branch `self-hosted`
+ tag). Non eseguito.

## 11. Remote Verification

`N/A` (nessun push).

## 12. Release Baseline

`NON PRODOTTA.` Il versionamento è bloccato finché la suite backend non è verde (o finché il
responsabile non decide esplicitamente come trattare i 6 fallimenti upstream).

## 13. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: registrato MOD-020 = RELEASE BLOCKED at backend test gate (1446 test, 6 fail in
PasswordValidatorTest, upstream/HEAD, fuori dal diff self-hosted); baseline documentata 1446/1446
marcata STALE; Known Issues aggiornato (F20-1 PasswordValidator common-passwords loading); Current
focus/Next step = USER DECISION (fix upstream test gate → poi ri-eseguire MOD-020). Nessun commit/tag/
push. Nessuna modifica di codice.
```

## 14. Final Verdict

```text
CLAUDE.md updated: YES

Branch: main @ e1d24406 (nessun ramo self-hosted creato)
Pre-commit working tree: 21 tracked modified + 5 test untracked + docs/ (70)
Secret audit: PASS
.gitignore: PASS
Backend test: FAIL (1446 run, 6 failures — PasswordValidatorTest, upstream, fuori dal diff MOD)
Android build: NOT RUN (artefatto MOD-017 presente)
Frontend: UNCHANGED
Database: NO CHANGES
Staged files: 0
Commit: NONE
Release tag: NONE (pianificato self-hosted-v1.0.0)
Push branch: NOT PERFORMED
Push tag: NOT PERFORMED
Remote verification: N/A
Post-release working tree: N/A (nessuna operazione git di scrittura)
Release baseline: NONE
Next step: USER DECISION — risolvere il gate test (F20-1) e poi ri-eseguire MOD-020
Final verdict: RELEASE BLOCKED
```

**RELEASE BLOCKED.** Il working tree self-hosted è coerente, sicuro (secret) e attribuibile ai MOD, ma
il **gate obbligatorio dei test backend non è verde**: 6 fallimenti in `PasswordValidatorTest`, una
feature **upstream** (caricamento `common-passwords.txt`) **non introdotta né toccata dalle modifiche
self-hosted**. Per le regole di MOD-020 (§9 DO NOT COMMIT, §24 no dichiarazioni false, §25 STOP) non è
stato creato alcun commit/tag/push e non correggo il difetto upstream in autonomia (§4/§16).

**Opzioni per il responsabile (decisione richiesta):**
- **(A)** Correggere il difetto upstream `PasswordValidator.loadCommonPasswords()` (il set risulta vuoto
  a runtime nonostante il file presente) come **task separato** → suite verde → **ri-eseguire MOD-020**.
  È l'unico percorso che produce una baseline verde. *(Nota: sarebbe una modifica a codice **upstream**,
  fuori dal perimetro self-hosted; va autorizzata esplicitamente.)*
- **(B)** Accettare esplicitamente i 6 fallimenti come **pre-esistenti/upstream** (non causati dal lavoro
  self-hosted) e autorizzare il commit/tag/push **nonostante** i test rossi — deroga esplicita a §9,
  documentata come rischio noto.
- **(C)** Escludere temporaneamente `PasswordValidatorTest` dal gate (es. profilo/`@Disabled`
  motivato) — sconsigliato senza aver compreso perché la risorsa non si carica.

⏹️ **STOP** — non creo commit/tag/push, non genero l'APK di release, non modifico codice upstream o
self-hosted, non avvio MOD-021. Il passo successivo dipende da una decisione esplicita del responsabile.
