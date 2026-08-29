# MOD-020 — Release Commit, Tag & Push

Preparazione della baseline versionata (commit/tag/push) della release self-hosted. Il gate
obbligatorio dei test backend (§9) è risultato **non verde** (6 fallimenti in `PasswordValidatorTest`,
feature "common passwords" **upstream**, fuori dal diff self-hosted). **Il responsabile ha scelto
l'opzione B** — accettare esplicitamente questi 6 fallimenti come pre-esistenti/upstream (fix futuro
se servirà) e autorizzare comunque il versionamento, dopo conferma che **non esistono altri
fallimenti/errori** nella suite. Commit, tag annotato e push **eseguiti**. Nessuna modifica di codice.

```text
Code changes in this MOD: NONE
Backend test gate: 1446 run / 6 failures (PasswordValidatorTest, upstream) / 0 errors — accepted (owner, option B)
Commit / Tag / Push: DONE (branch self-hosted, commit a03c35db, tag self-hosted-v1.0.0, pushed to origin)
```

> **Esito in una riga:** working tree e secret **OK**; il **gate test backend** ha esattamente **6
> fallimenti**, **tutti** in `PasswordValidatorTest` (common-passwords, **upstream**, fuori dal diff),
> **0 errori** e nient'altro rosso. Su decisione esplicita del responsabile (opzione B, deroga
> documentata a §9) la baseline è stata **committata, taggata e pushata**: ramo **`self-hosted`**,
> commit **`a03c35db`** (97 file), tag **`self-hosted-v1.0.0`**, su `origin` (`Daviz96/cmms`); `main`
> intatto (`e1d24406`). → **RELEASE VERSIONED (with accepted known findings).**

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

### 5b. Owner Decision (Option B)

Dopo la diagnosi, il responsabile ha verificato che la suite ha **esattamente 6 fallimenti, tutti in
`PasswordValidatorTest`, 0 errori** (nessun altro test rosso — auth, authz, tenant isolation,
licensing, allegati, assets, work orders, integration test: tutti verdi) e ha deciso: **opzione B —
accettare i 6 fallimenti upstream come non bloccanti** per l'obiettivo (fix in futuro se servirà) e
**autorizzare commit/tag/push**. Deroga esplicita a §9, tracciata qui come rischio noto (F20-1).

## 6b. Backend Test Gate — accepted breakdown

```text
Tests run: 1446   Failures: 6   Errors: 0   Skipped: 0
Le 6 failures: PasswordValidatorTest (CommonPasswords ×5 + CommonPasswordsLoading ×1). Nient'altro rosso.
```

## 7. Staged Files

`97 file` (staging selettivo `git add -A` — gitignored esclusi; verificato: nessun `.env`/
`google-services.json`/`*.key`/`target`/`build` in staged; `git diff --cached --check` = solo
trailing-whitespace cosmetico in alcuni `.md` di prompt, non toccato per §4). Gruppi: `.env.example` 1,
`api` 16 (11 mod + 5 test), `docker-compose.yml` 1, `docs` 71, `home` 1, `mobile` 7.

## 8. Commit

`CREATED.` `a03c35db` sul ramo **`self-hosted`** (creato dal HEAD `e1d24406`; `main` **intatto**).
`97 files changed, 28903 insertions(+), 91 deletions(-)`. Messaggio: *"release: finalize self-hosted
Atlas CMMS baseline (self-hosted-v1.0.0)"* (convenzione conventional-commit del repo; corpo che elenca
MOD-001..020 e dichiara i 6 fail upstream accettati; trailer `Co-Authored-By`).

## 9. Release Tag

`CREATED.` Tag **annotato** `self-hosted-v1.0.0` su `a03c35db` (namespace distinto scelto dal
responsabile; esistono tag upstream `v1.0.0…v1.8.0` da cui ci si separa).

## 10. Push

`DONE.` `git push -u origin self-hosted` → `* [new branch] self-hosted -> self-hosted` (exit 0);
`git push origin self-hosted-v1.0.0` → `* [new tag] self-hosted-v1.0.0 -> self-hosted-v1.0.0` (exit 0).
Remote: `origin` = `git@github.com:Daviz96/cmms.git`. Nessun force push. (GitHub ha proposto il link
di apertura PR per `self-hosted`.)

## 11. Remote Verification

`PASS.` `origin/self-hosted` = **`a03c35db`** (== HEAD locale; `git status -sb` in sync, 0 ahead/0
behind); tag `self-hosted-v1.0.0` pushato (confermato dall'output del push). Branch tracking
configurato (`origin`/`refs/heads/self-hosted`).

## 12. Release Baseline

```text
Branch:        self-hosted   (main intatto @ e1d24406)
Commit:        a03c35db      release: finalize self-hosted Atlas CMMS baseline (self-hosted-v1.0.0)
Tag:           self-hosted-v1.0.0 (annotated)
Remote:        origin  git@github.com:Daviz96/cmms.git
Working tree:  clean
Backend test:  1446 run / 6 fail (PasswordValidatorTest, upstream, accepted) / 0 errors
Android build: app-release.apk presente (MOD-017); da rigenerare dal codice taggato in MOD-021
```

Questa è la baseline per **MOD-021 (Android Release APK)** e **MOD-022 (Server Deployment Preparation)**.

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

Branch: self-hosted (creato da main @ e1d24406; main intatto)
Pre-commit working tree: 21 tracked modified + 5 test untracked + docs/ (71)
Secret audit: PASS
.gitignore: PASS
Backend test: 1446 run / 6 failures / 0 errors — 6 in PasswordValidatorTest (upstream, fuori dal diff) — ACCEPTED (owner, option B)
Android build: NOT RUN (artefatto MOD-017 presente; APK definitiva = MOD-021)
Frontend: UNCHANGED
Database: NO CHANGES
Staged files: 97
Commit: a03c35db
Commit message: release: finalize self-hosted Atlas CMMS baseline (self-hosted-v1.0.0)
Release tag: self-hosted-v1.0.0 (annotated)
Push branch: SUCCESS
Push tag: SUCCESS
Remote verification: PASS (origin/self-hosted = a03c35db, in sync)
Post-release working tree: CLEAN (poi + follow-up doc commit che registra questo esito)
Release baseline: a03c35db + self-hosted-v1.0.0 (origin/self-hosted)
Next step: MOD-021 — Android Release APK
Final verdict: RELEASE VERSIONED (with accepted known findings)
```

**RELEASE VERSIONED.** Il working tree self-hosted (coerente, sicuro sui secret, attribuibile ai MOD) è
stato versionato: ramo **`self-hosted`**, commit **`a03c35db`** (97 file), tag annotato
**`self-hosted-v1.0.0`**, pushati su `origin` (`Daviz96/cmms`); `main` intatto (`e1d24406`). Il gate dei
test backend aveva **6 fallimenti**, **tutti** in `PasswordValidatorTest` (feature "common passwords"
**upstream**, non introdotta né toccata dal lavoro self-hosted), **0 errori** e nient'altro rosso; su
**decisione esplicita del responsabile (opzione B)** sono stati accettati come pre-esistenti/upstream e
tracciati come **F20-1** (fix futuro se servirà, out-of-scope self-hosted). Non ho corretto il difetto
upstream in autonomia (§4/§16).

⏹️ **STOP** — la baseline è versionata. Non genero l'APK definitiva (MOD-021) né configuro/deployo il
server (MOD-022+). Il passo successivo è **MOD-021 (Android Release APK)** dal codice taggato, su
decisione del responsabile.
