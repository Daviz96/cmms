# CLAUDE.md — Atlas CMMS Self-Hosted Development

## Project Overview

Atlas CMMS is being adapted and verified for a self-hosted deployment, with the
primary project goal of making functionality available through the existing
self-hosted licensing path while preserving the application's existing
security, authorization and tenancy model.

The project is worked on incrementally through numbered `MOD-xxx` modules.
Each module is audited, implemented only when necessary, and then verified with
tests and persistent documentation.

**Current focus:** **LIVE DEPLOYMENT + post-release bug-fixing → `v1.0.2` in preparazione** (post MOD-020).
Il backend custom è **live in produzione** su `https://cmms.firmabratex.pl` (LAN-only, Caddy wildcard TLS) —
immagine `dablio96/self-hosted-cmms-backend:self-hosted-v1.0.1` (commit `decbc2cd`), `SELF_HOSTED` attivo,
DB originale preservato. Frontend live = immagine **upstream** `intelloop/atlas-cmms-frontend` (non buildata dal repo).
**3 bug** (tutti codice upstream emerso ora) — stato al 2026-09-01:
- **Bug 2 (invito non parte)** — root cause **confermata**: il frontend **upstream** manda `disableSendingEmail:true`;
  il backend `UserService.invite():379` è **corretto** (il *nostro* `InviteUserDialog.tsx:181` passa `false`). NB:
  `disableSendingEmail:true` è **legittimo** in auto-registrazione (`RegisterJWT.tsx:103`) → scartata l'Opzione A
  (backend "invia sempre") perché romperebbe quel caso. **Scelta: Opzione B** = buildare+deployare il **nostro**
  frontend (`docker build ./frontend`) e sostituire l'immagine upstream. Nessuna modifica codice necessaria.
- **Bug 3 (NPE ricerca Work Order)** — **FIXATO nel codice**: `WorkOrderService.getSearchCriteria` non naviga più la
  collezione LAZY `getSuperAccountRelations()` su `@CurrentUser` detached; usa una query JPQL session-safe
  `SuperAccountRelationRepository.findChildCompanyIdsBySuperUserId(userId)`. Da validare a runtime con dati di test.
- **Bug 1 (`conflict_error` auto-eliminazione)** — **causa confermata dal trace (v1.0.2)**: NON è `softDeleteUser`
  (che riesce), ma il **`logout` chiamato subito dopo** → `invalidateSessions(@CurrentUser user)` salva un'entità
  **detached/inesistente** (l'auto-eliminazione `DELETE /auth` → `AuthController.deleteAccount` fa **HARD delete**
  della riga user) → UPDATE 0 righe → `StaleObjectStateException` (User senza `@Version`) → 409. **FIXATO in v1.0.3**: nuovo
  `UserService.invalidateSessionsById(id)` (ricarica fresco per id, no-op se assente); `AuthController.logout` lo usa;
  rimosso il log temporaneo. `invalidateSessions(User)` invariato (serve ai caller che mutano l'entità).

**Stato (aggiornato 2026-09-01, sera):** `v1.0.2`+`v1.0.3` (fix Bug 1/2/3) deployati sul live; rete Caddy
**permanente**. **Sync upstream ADOTTATO in `self-hosted`** (ff-merge `7920c0d3`): integrati 32 commit upstream
(rate-limiting login, PDF RTL/CJK, **flusso eliminazione account a 2 passi con conferma email** che sostituisce il
pericoloso `DELETE /auth`, signed-URL caching, webhook, ecc.). Conflitti risolti (4: `MinioService` + 3
`mailMessages*`). Nostri fix e config self-hosted **preservati e verificati**. **Validazione locale completa**:
backend+frontend+test compilano; smoke-test full-stack su DB fresco → **Liquibase applica tutte le migration
(incl. `fix_part_version_null` upstream, safe: backfill+NOT NULL), `SELF_HOSTED`, `Started ApiApplication`**.
Immagini **`self-hosted-v1.1.0`** (backend+frontend) **DEPLOYATE sul live (2026-09-01 sera)**: backup DB fatto
(`pg_dump`), migration upstream **`fix_part_version_null` applicata sui DATI REALI in 89ms**, `Started ApiApplication`
in 25.5s, `SELF_HOSTED`. Solo i noti WARN Liquibase (changelog con spazio iniziale), nessun errore. Dati preservati
(postgres/minio non ricreati). Branch `sync-upstream` pushato; `self-hosted` a `310e25a4`. Server compose:
`/srv/docker/atlas/docker-compose.yml`, volumi named-con-bind su `/srv/data/databases/atlas/{postgres,minio}`
(**mai `down -v`**). Runbook usato: `dev-docs/deploy-v1.1.0-runbook.md`. NB: SSH pilotato dall'assistente **non
possibile** (chiave con passphrase, nessun agent, + password sudo) → deploy via runbook eseguito dall'utente.
Smoke-test v1.1.0 **OK** (login, eliminazione 2-passi, invito, upload; **ricerca WO / Bug 3 CONFERMATO** via seed
`dev-docs/seed_test_data.py`, `totalElements=6` senza NPE). **`v1.2.0` COSTRUITA e PUSHATA su GitHub (commit
`5ea45b81`), NON ancora deployata:** (a) feature admin "Crea utente" con **link imposta-password**; (b) QR/dialog
"scarica app" → **APK self-hosted** (non app ufficiale). Immagini `self-hosted-v1.2.0` (backend+frontend) taggate
in locale. Deploy = stesso runbook (swap `api`+`frontend` → `:self-hosted-v1.2.0`, `pull`+`up -d`+`restart nginx`,
backup DB). Poi decidere se aggiungere la restrizione "elimina solo admin"
([docs/restrict-user-deletion-to-admins-plan.md](restrict-user-deletion-to-admins-plan.md)) sopra al nuovo flusso.
**Dettaglio completo, file:line, gotcha operativi in
[docs/live-deployment-bugs-handoff.md](live-deployment-bugs-handoff.md)** e piano
[docs/upstream-sync-plan.md](upstream-sync-plan.md). (Runbook deploy: `dev-docs/upgrade-to-self-hosted.md`.)

**Backlog / piani (2026-09-01/02):**
- ✅ **Scelta admin "Invita ⇄ Crea utente" — IMPLEMENTATA in `v1.2.0`** (commit `5ea45b81`): toggle nel dialog;
  "Crea utente" crea l'account (enabled, password random inutilizzabile) e invia mail di benvenuto con **link
  imposta-password** — l'utente sceglie la propria password, **nessuna password in chiaro via mail** (variante scelta
  rispetto al piano originale). `POST /users/create-by-admin` + `POST /auth/set-password`
  (`VerificationTokenService.confirmSetPassword`), template `account-created.html`, pagina `/account/set-password`,
  DTO `CreateUserByAdminDTO`/`SetPasswordRequest`. Piano: [docs/admin-invite-vs-create-user-plan.md](admin-invite-vs-create-user-plan.md).
- **Eliminazione utenti solo agli admin** (blocco auto-eliminazione). ⚠️ Scoperto che l'auto-eliminazione usa
  **`DELETE /auth`** (`AuthController.deleteAccount`, `@PreAuthorize permitAll`) = **HARD delete**; se l'utente
  possiede la company → `companyService.delete()` **cancella l'intera org**. Fix proposta: rimuovere `DELETE /auth`
  + togliere il ramo self in `softDeleteUser`. Invasività bassa. Piano:
  [docs/restrict-user-deletion-to-admins-plan.md](restrict-user-deletion-to-admins-plan.md).
  **NB:** upstream ha già rifatto l'eliminazione account (flusso request+conferma, commit `d7e7ec00`/`714a99dc`) →
  coordinare con il sync (sotto) prima di implementare questo piano.
- **Sync col fork upstream** (`Grashjs/cmms`). Remote `upstream` configurato + fetchato. Divergenza dal fork
  `e1d24406`: upstream **+32 commit**, noi **+4**. Dry-run merge fatto: **solo 4 conflitti testuali (1 hunk)** —
  `MinioService.java` + 3 `mailMessages*`; **129 file auto-mergiati** (rileggere i sensibili: `AuthController`,
  `UserService`, `WorkOrderService`, `GCPService`, `application.yml`). Attenzione a migrazione DB `Part.version`
  (`bdd94408`). Piano completo: [docs/upstream-sync-plan.md](upstream-sync-plan.md). Da eseguire in sessione dedicata.

Preceding — MOD-020 (Release Commit, Tag & Push) — **completed, doc 40, RELEASE VERSIONED**
(no code change). La baseline self-hosted è stata **versionata**: ramo **`self-hosted`**, commit
**`a03c35db`** (*"release: finalize self-hosted Atlas CMMS baseline (self-hosted-v1.0.0)"*, 97 file,
+28903/−91), tag annotato **`self-hosted-v1.0.0`**, **pushati su `origin`** (`Daviz96/cmms`); `main`
intatto (`e1d24406`). Gate: secret audit **PASS** (anche 71 `docs/` + 5 test; nessun JWT/chiave reale;
`.env`/`google-services.json` gitignored e non committati), `.gitignore` OK, no migration, frontend
invariato, licensing coerente. **Backend test gate rieseguito con Docker/Testcontainers:** `Tests run:
1446, Failures: 6, Errors: 0` — i **6 fallimenti sono tutti in `PasswordValidatorTest`** (feature
"common passwords" **upstream**: `loadCommonPasswords()` ritorna un Set vuoto a runtime; file presente,
codice committato a HEAD **fuori dal diff self-hosted**), **0 errori**, nient'altro rosso. **Decisione del
responsabile: opzione B** — accettati come pre-esistenti/upstream (F20-1, fix futuro se servirà) e
autorizzato il versionamento; il difetto upstream **non** è stato corretto in autonomia (§4/§16). La
baseline documentata 1446/1446 (MOD-011) era **STALE**. **Next step: MOD-021 (Android Release APK)** dal
codice taggato. Dettaglio in doc 40.

Preceding — MOD-019 (Final Code Audit & Release Readiness) — **completed, doc 39,
READY FOR CODE FREEZE** (verification-only; no code change). Audit finale del working tree per il freeze:
**secret audit PASS** (`.env` e `google-services.json` gitignored/non tracciati; `.env.example` solo
placeholder — `LDAP_MANAGER_PASSWORD=` vuoto); **diff coerente e attribuibile ai MOD** (21 file tracciati
+ 5 test + `docs/`; `git diff --check` pulito; **nessuna modifica frontend** → coerente con MOD-008 CLEAN);
**licensing integro** (`LicenseService` = self-hosted centralizzato, no `return true`, no bypass, authz/
tenant non toccati); **backend compila pulito** (`mvnw -o test-compile` exit 0, inclusi i 5 nuovi test);
**nessuna migration/schema change**; **deployment coerente** (compose build-from-source MOD-006,
`application.yml` MOD-001/006); **APK release presente/riproducibile** (95.5 MB). Suite backend **1446/1446**
= baseline documentata (MOD-011): **non ri-eseguita** (Docker off; codice backend invariato dalla suite
verde, solo un typo properties in MOD-016) → re-run **REQUIRED al gate di commit MOD-020** con Docker attivo.
**Nessun fix REQUIRED prima del freeze.** Finding registrati: F19-1 (re-run suite @ MOD-020), F19-4 (bug
notifiche **solo italiano** in `messages_it_IT.properties` — apostrofo non raddoppiato che rompe
`MessageFormat` → `{0}/{1}` non sostituiti + `�`/mojibake; **pre-esistente/upstream, il polacco è
integro** → DEFERRED). **CODE FREEZE APPROVED. Next step: MOD-020 (Commit, Tag & Push).** Dettaglio in doc 39.

Preceding — MOD-018 (Comprehensive Project State Recap & Roadmap Audit) — **completed, doc 38,
PASS WITH FINDINGS** (documentation-only checkpoint; no code change). Ricostruito dalla documentazione
`docs/` (fonte primaria) lo stato reale dell'intero progetto Atlas self-hosted. **Sintesi:** l'obiettivo
*licensing-unlock* è **COMPLETO e verificato live** (MOD-001/002/005: `SELF_HOSTED`, 34 entitlement,
BUSINESS+17 PlanFeatures, no Keygen, no bypass); backend+web **READY WITH CONDITIONS** (MOD-013, suite
**1446/1446**, frontend audit CLEAN); allegati sicuri + tenant isolation `@PostLoad` verificati; mobile
Android **PARTIAL** (build/install/smoke/regression/i18n PASS su emulatore, M-BUG-1 fixed, PL pulito),
iOS **NOT VERIFIED** dall'agent (owner ha confermato solo connettività). **Gap MUST-HAVE unico:**
esecuzione operativa del **go-live** — dominio/DNS/TLS/Caddy non nel repo, secret ancora d'esempio (GL-1),
**modifiche MOD non committate** (HEAD fermo a `e1d24406`, MOD-013; GL-2), image tag `:local` (GL-3).
Altri aperti: bug mobile segnalati dall'owner (oltre M-BUG-1, non dettagliati), F-04 (P3), LDAP live non
esercitato, i18n altre lingue. **Contraddizioni di stato: 0** (4 note documentali: FS storage doc 04→18,
baseline 1445→1446, il file `22-…-gap-analysis` è un PROMPT non un report, prefissi 19/21 ripetuti).
**Next step raccomandato: MOD-019 — USER DECISION** tra (A) Repository & Deployment Readiness [per il
go-live], (B) Mobile Bug Audit & Fix, (C) LDAP Live Integration Test. Dettaglio completo in doc 38.

Preceding — MOD-017 (Polish i18n Key Integrity & Literal UI Audit) — **completed, doc 37,
PASS** (mobile client). Audit sistematico delle *literal i18n key* con impatto sulla lingua
**polacca**: un detector statico ha estratto tutte le `t('…')` (**472**) e segnalato le **11** con
chiave assente da `en.ts` → i18next mostra la chiave grezza (fallback), che in polacco appare come
testo inglese o come identificatore snake_case (`lng` pl da `generalPreferences.language.toLowerCase()`,
`fallbackLng:'en'`, `keySeparator:false`). Corretti i **10** casi con impatto PL: **7 nuove chiavi**
en+pl (`Sign out`→**Wyloguj się**, `Version`→**Wersja**, `Dev Info`, `Build ID`, `informations`→
**Informacje**, `hour`→**godzina**, `notifications`→**Powiadomienia**) + **3 code fix** che riusano
chiavi esistenti (`t('Description')`→`t('description')`; `t('Notifications')`→`t('notifications')`;
`t('location_update_failure')`→`t('location_edit_failure')`); `NFC` lasciato invariato (acronimo
identico in PL → **non-bug**). Integrità en/pl **1338=1338** (0 missing/extra/dup/placeholder). Le
sole chiavi base indispensabili sono state aggiunte a `en.ts`; **nessuna** altra lingua toccata.
**Release APK reale** (`gradlew assembleRelease`, JS bundled via Hermes) buildato e verificato a
**runtime in polacco** su AVD `atlas_test` (account company PL creato via `signup` `language:"PL"`):
Settings **Wersja/Wyloguj się** + dialog logout, schermata **Powiadomienia**, profilo persona
**INFORMACJE/POWIADOMIENIA**; regression Launch→Login→Dashboard→Work Orders→Assets→Settings→Logout
**PASS**. Diff minimo (**5 file mobile**: en.ts+7, pl.ts+7, navigation/index.tsx, EditLocationScreen.tsx,
utils/fields.ts). Nessuna modifica a backend/altre-lingue/production. Next step: decisione del responsabile.

Preceding — MOD-016 (Polish Translation Audit) — **completed, doc 36, PASS** (solo file
locale). Audit mirato del locale polacco mobile (`mobile/i18n/translations/pl.ts`, i18next, base
`en`): **67 correzioni** di errori reali — 4 **placeholder rotti** (nome variabile `{{...}}`
tradotto → interpolazione non funzionante: `{{daty}}`→`{{date}}` ecc.), 2 **chiavi mancanti**
aggiunte, e ~61 traduzioni sbagliate/incoerenti (es. `save`="Ratować"→**Zapisz**,
`due_date`="Dwie daty"→**Termin**, `parts`="Strony"→**Części**, `meters`="Metry"→**Liczniki**,
`links`="Spinki do mankietów"→**Powiązania**). Integrità ripristinata (**1331=1331** chiavi, 0
placeholder rotti); tsc/prettier/consistency clean. Verificato a **runtime in polacco** su un
**release build reale** (account company PL → `changeLanguage('pl')`): correzioni renderizzate
(Start/Liczniki/Zgłoszenia/Anuluj), layout integro, regression Launch→Login→Dashboard→Work Orders→
Assets→Settings→Logout **PASS**. Su richiesta del responsabile verificato anche il locale email
backend `mailMessages_pl_PL.properties` → corretto 1 typo (`BLOCKS=bloki`→`blokuje`, solo risorsa
i18n); documentati REVIEW (`asset`=Maszyna; chiavi-letterali `Sign out`/`Version` assenti da tutte
le locale → non tradotte in nessuna lingua). Nessuna modifica a backend-logica/production.
**Follow-up su richiesta** — verificato anche il locale polacco della **home app** (Next.js,
`home/src/i18n/translations/pl.ts`, qualità già alta): 4 correzioni (`timers`→Timery, plan
`Starter` "Rozrusznik"→Startowy, placeholder `{shortBrandName}`→`{brandName}`, "uprawy roślin"→
"zakłady") + **17 chiavi mancanti aggiunte**; integrità EN 1489/PL 1509, 0 missing/0 placeholder,
solo verifica statica (no runtime, come richiesto). Next step: decisione del responsabile.

Preceding — MOD-015 (Mobile Bug Fix & Regression) — **completed, doc 35, PASS — M-BUG-1
RESOLVED** (mobile code fix). Corretto M-BUG-1 (`TypeError: Cannot read property 'endsWith' of
undefined`): **root cause** in `mobile/config.ts` `getApiUrl()` che dereferenziava un URL
`undefined` (dev build senza `API_URL` bakato + nessun Custom Server), innescato all'avvio da
`LoginScreen` via `getInstanceConfig`. **Fix minimale (+15 righe, 2 file):** `config.ts`
`getApiUrl()` null-safe (`if (!rawApiUrl) return '';`) + `slices/instanceConfig.ts` salta il fetch
quando non c'è server. Verificato con **release APK reale** (`gradlew assembleRelease`, JS bundled,
no Metro): fresh launch **senza errore** + **regression completa PASS** (Custom Server → Login →
Dashboard → Work Orders → Assets → Settings → Logout, nessun toast/console error). `tsc` e Prettier
clean. Nessuna modifica backend/production; `google-services.json` e APK release gitignored. Mobile
agent testing resta AVAILABLE. Next step: decisione del responsabile (altri bug mobile / MOD-016).

Preceding — MOD-014B (Firebase Configuration & Android Build) — **completed, doc 34,
PASS WITH FINDINGS — Mobile agent testing: AVAILABLE** (no app/code change). Il responsabile ha
fornito il `google-services.json` reale (in `mobile/android/app/`, **gitignored** — non committato):
build **eseguita** con `npx expo run:android` → **BUILD SUCCESSFUL 14m45s** → APK `app-debug.apk`
(`com.atlas.cmms`) **installato** e **avviato** sull'AVD `atlas_test`; **smoke test PASS** — Launch →
Custom Server (`http://10.0.2.2:3000/api`) → Login (`atlastest@example.com`) → dashboard → Work Orders
(lista) → Assets → Settings → **Sign out** → Login. GUI automation (input/uiautomator/screenshot/
logcat) pienamente operativa; connettività app→backend self-hosted confermata end-to-end. **1 bug
non bloccante** trovato e **documentato (non corretto)**: `mobile/slices/instanceConfig.ts` →
`TypeError: Cannot read property 'endsWith' of undefined` (toast/LogBox; non blocca login/WO/assets/
logout) → **MOD-015**. Nessuna modifica applicativa/tracciata (prebuild ha riusato l'`android/`
esistente). Next step: MOD-015 (fix bug mobile) su autorizzazione.

Preceding — MOD-014A (Android Test Environment Setup) — **completed, doc 33, PASS WITH
FINDINGS — Mobile agent testing: PARTIAL** (environment setup only, no app/code change). Ambiente
Android **installato e verificato a runtime**: JDK 17 stabile (`C:\Users\dawid\Android\jdk-17.0.20.1+1`),
Android SDK completo (`C:\Users\dawid\Android\Sdk`: cmdline-tools 12, platform-tools/adb 37.0.1,
platform-36, build-tools 36, emulator 37.1.11, system-image android-35 x86_64), AVD `atlas_test`
che **boota headless con accelerazione WHPX in ~36 s** (`emulator-5554 device`), `npm install`
(1213 pkg), e **baseline di automazione ADB verificata** (`input`, `uiautomator dump`, **screenshot**
via `screencap`+`adb pull`, `logcat`). Backend locale raggiungibile: host `/api/license/state`
→ 200, emulatore→host `ping 10.0.2.2` ~1 ms; Custom Server URL emulatore `http://10.0.2.2:3000/api`.
La build reale (`gradlew :app:processDebugGoogleServices`, 9m28s) attraversa **tutto** il toolchain
e fallisce su **un solo blocker**: manca `google-services.json` (Firebase) — **non fabbricato**
(§12), da fornire dal responsabile (file in `mobile/android/app/` o `GOOGLE_SERVICES_BASE64`).
Fornita quella credenziale → APK con `npx expo run:android` e smoke test eseguibile (ambiente →
AVAILABLE). **Nessuna modifica applicativa** (solo env; `mobile/package-lock.json` toccato da npm
e ripristinato). Next step: fornire credenziale Firebase → poi MOD-015.

Preceding — MOD-014 (Mobile Agent Test Environment) — **completed, doc 32, PASS WITH
FINDINGS — Mobile agent testing: NOT AVAILABLE (PARTIAL col setup Android)** (assessment only,
no code change). Identificata la toolchain reale (Expo SDK 53 / RN 0.79.6, Hermes, **dev-client**
→ Expo Go non basta; build via `npx expo run:android` locale o EAS `previewAndroid`). In questo
ambiente il testing GUI mobile via agent **non è eseguibile**: presenti Node/git/docker + un JDK 17
portatile, ma **mancano Android SDK, `adb`, emulatore, Android Studio, CLI Expo/EAS, `node_modules`
e `google-services.json` (Firebase)**; i progetti nativi `android/`/`ios/` esistono. Percorso
raccomandato: install Android SDK + AVD/device e automazione **solo-ADB** (`input`/`uiautomator`/
`screencap`/`logcat`; emulatore → `10.0.2.2:3000`, device → `<LAN>:3000`) come primario; EAS build +
device farm cloud come secondario; iOS non buildabile/eseguibile su host Windows (EAS/Mac/device
farm o test manuale del responsabile). **Nessun bug mobile corretto.** Next step: ENVIRONMENT WORK
REQUIRED (decisione del responsabile sul provisioning) — **non** MOD-015.

Preceding — MOD-013 (Final Pre-Production / Go-Live Readiness) — **completed, doc 31,
PASS WITH FINDINGS — GO-LIVE STATUS: READY WITH CONDITIONS** (verification/documentation only,
no code change). Ultimo controllo tecnico prima del deployment: il prodotto self-hosted è
tecnicamente pronto — backup (script `atlas-backup` + `pg_dump` validato in MOD-010), rollback
(restore con copia di sicurezza `atlas_old`), persistenza (volumi Docker con nome), autorizzazione
e isolamento multi-tenant, immagine backend buildata dai sorgenti — **nessun blocker P0/P1**. Le
condizioni residue sono attività operative già definite: provisioning dominio/DNS/TLS (meccanismo
in `dev-docs/Set up TLS.md`, Caddy consigliato; il dominio Atlas e `wiki.firmabratex.pl` **non**
sono nel repo → UNKNOWN/OPEN), rotazione dei secret di produzione (ora valori d'esempio) e
disponibilità dei sorgenti MOD (non committati) sul build host. Mobile deferito a MOD-014/015 (il
responsabile ha già verificato su device reale la connessione dell'app iOS al backend e la presenza
di alcuni bug). F-04 resta pre-esistente/out-of-scope.

Preceding — MOD-012 (Mobile Runtime Acceptance) — **completed, doc 30, PASS WITH
FINDINGS** (verification only, no code change). The interactive mobile GUI could **not**
be runtime-tested in this headless environment (no adb / Android SDK / emulator / Expo
CLI, and iOS requires macOS) → **Android/iOS interactive = NOT TESTED** (environment
prerequisite; recommend a real-device pass before production). Everything verifiable
without a device passed: the mobile↔backend contract was confirmed at code level
(MOD-009) and at protocol level against the live self-hosted stack (signin 200, asset
read + full-DTO PATCH 200, attachment upload/download 200), and mobile licensing has no
commercial gate. Critically, **F-04 mobile impact = NONE OBSERVED**: the official app's
asset edit (`EditAssetScreen` + `formatAssetValues`) sends a **full DTO** (keeps `name`
and `status`), so it never triggers F-04 (nor F-01) — confirmed by code and by a live
full-DTO PATCH → 200. No product defects (P0–P3 = 0).

Preceding — MOD-011 (F-01 fix) — **completed, doc 29, PASS — F-01 RESOLVED.**
The MOD-010 finding F-01 (`AssetService.patch` → HTTP 500 NPE on a partial PATCH that
omits `status`) is fixed with a minimal null-safe default (`if (asset.getStatus() ==
null) asset.setStatus(savedAsset.getStatus())`): a partial PATCH without `status` now
returns 200 with the status left unchanged, and PATCH with `status` is unchanged. Added
1 regression test (`AssetServiceTest.patchWithoutStatus_doesNotFailAndKeepsStatus`);
full suite **1446/1446**; runtime-verified; company isolation intact (CoB→CoA asset PATCH
still 403). Code changes: `AssetService.java` (+6), `AssetServiceTest.java` (+1 test).
A broader pre-existing partial-patch issue was discovered and **documented, not fixed**
(F-04, below).

Preceding — MOD-010 (Local Acceptance Test) — **completed, doc 28, PASS WITH
FINDINGS — production readiness READY WITH FINDINGS** (verification only, no code
change). The full official stack (`docker-compose.yml`, backend **built from source**,
+ PostgreSQL + MinIO + frontend + nginx on `localhost:3000`) was run locally and
exercised end-to-end: infra OK; licensing runtime `SELF_HOSTED`/`valid=true`/34
entitlements; BUSINESS plan + 17 `PlanFeatures`; auth (signup/login/logout→token
invalidation/re-login); company isolation (`@PostLoad` 403 cross-company); core CMMS
CRUD (assets/work-orders/parts/meters); attachments image/PDF/other (disposition per
type + delete lifecycle — MOD-004B confirmed live); persistence across `restart` and
`down`/`up` (no `-v`); valid `pg_dump` backup; per-service restart/recovery. **37/38
checks PASS**; the one FAIL is **F-01** (P3, pre-existing, out-of-scope): `AssetService.patch`
NPEs (500) on a partial PATCH omitting `status` — not MOD-related, workaround = send
`status` (the UI does). No P0/P1, no security bypass, no data loss. Mobile runtime /
push (FCM) / offline write-sync remain **DA VERIFICARE** (need a device / Firebase).

Preceding — MOD-009 (mobile Android/iOS compatibility audit) — **completed,
doc 27, PASS — mobile COMPATIBLE WITH CONFIGURATION** (analysis only, no code change).
The official React Native + Expo app (Android + iOS share the JS codebase) can use our
self-hosted modified backend **without any mobile code change**: it resolves the server
URL via `getApiUrl()` → a runtime-settable `customApiUrl` (AsyncStorage) exposed by a
dedicated **Custom-server screen reachable from Login**, so the user points the app at
`https://<our-server>/api`. Licensing/plan gates are backend-driven (open in self-hosted,
BUSINESS + full entitlements); **no Keygen/Paddle** calls; attachments (incl. the
MOD-004B `Content-Disposition: attachment`) are compatible because the app downloads via
`FileSystem.downloadAsync` (disposition-agnostic) and shows images inline. Cloud
dependencies (Firebase/FCM push, Expo OTA, Clarity, Maps) are optional/non-blocking;
push delivery vs a self-hosted backend is `DA VERIFICARE` (backend FCM config) and full
offline write-sync depth is `DA VERIFICARE`. Store re-distribution with own branding is
a separate distribution matter, out of scope.

Preceding — MOD-008 (frontend licensing & feature-gate audit) — **completed,
doc 26, PASS — frontend status CLEAN** (analysis only, no code change). The web
frontend has **no blocking commercial gate** for self-hosted: it never contacts Keygen
and does no client-side license validation; entitlement gates (`useLicenseEntitlement`)
and plan gates (`hasFeature`) read the **backend** license/plan state, which self-hosted
opens (MOD-001 full entitlements + BUSINESS plan with all `PlanFeatures`); `isCloudVersion`
only toggles cloud billing/marketing/support (off in self-hosted, hides no feature — no
`!isCloudVersion` feature-hiding exists); permission checks are legitimate authorization.
No frontend modification is required. (Mobile deferred to a separate MOD.)

Preceding — MOD-006 (deployment alignment) — **implemented and verified
(doc 24, PASS)**. The official `docker-compose.yml` now **builds the backend from
source** (`build: ./api`, tag `atlas-cmms-backend:local`) instead of the upstream
prebuilt image, so a self-hosted deploy runs the modified code (MOD-001/004B).
CFG-01 resolved (`application.yml` `mail.recipients: ${MAIL_RECIPIENTS:}` — the API
now boots without `MAIL_RECIPIENTS`); CFG-02 evaluated and left as an optional nginx
hardening (does not manifest in the official compose, where the frontend env is
defaulted). Regression 1445/1445; security unchanged. Only two files changed
(`docker-compose.yml`, `application.yml`).

Preceding, now-completed work:
- **MOD-004B/004C** — attachment stored-XSS mitigation + MinIO delete lifecycle;
  independently verified (doc 20, PASS WITH FINDINGS) and runtime-verified against a
  real MinIO+nginx (doc 21, PASS). Non-blocking items: O-01 (duplicated `nosniff`),
  VF-01 (Low, orphaned binary on real storage error), VF-02 (Info, no bypass).
- **MOD-005** — self-hosted runtime integration verification (doc 23, **PASS WITH
  FINDINGS**): a backend image **built from source** (provenance proven via bytecode)
  run in an isolated stack confirmed **live**: self-hosted licensing active (no
  Keygen), **BUSINESS plan assigned with `PlanFeatures.FILE`** (closes audit-22's
  open question), real upload/download/delete through `FileController` (disposition
  per type + `nosniff`/`X-Frame-Options`, delete removes binary+metadata), and
  **tenant isolation via `CompanyAudit.@PostLoad` (403 cross-company)**. Findings
  CFG-01/CFG-02 (deployment) were addressed by MOD-006.
- **MOD-001/002/003A** and the **MOD-004** audit remain completed.

Awaiting the technical owner's decision gate before any next module.

A consolidation / gap analysis (doc 22, `22-audit-consolidation.md`) consolidates all
MOD status, findings, and the licensing audit. Its headline: the licensing-unlock
objective is substantially complete and now verified at runtime (MOD-005) — no
priority feature remains licensing-blocked; the deployment is now aligned to the
modified sources (MOD-006). What remains is external-service configuration, a few
genuinely-absent features (e.g. filesystem storage), and open LDAP product decisions.

The authoritative project history is the repository documentation, not the
chat history.

---

## Core Architecture

The verified backend architecture is Java/Spring based.

Relevant components confirmed by the LDAP audit include:

```text
HTTP API
  ↓
Spring Security
  ├─ local authentication
  ├─ LDAP / Active Directory authentication
  └─ OAuth2/OIDC (separate path)
  ↓
application services
  ↓
PostgreSQL persistence
```

LDAP authentication currently follows:

```text
POST /auth/signin-ldap
  ↓
AuthController
  ↓
LdapService.signinLdap()
  ↓
LdapAuthenticationProvider
  ↓
BindAuthenticator / FilterBasedLdapUserSearch
  ↓
LDAP / Active Directory
  ↓
JIT provisioning / existing-user update
  ↓
OU → Atlas role mapping
  ↓
JWT token pair
```

LDAP synchronization uses Quartz through the existing LDAP sync job/scheduler.

Docker deployment contains at least the following relevant services:

```text
api
frontend
postgres
minio
nginx
```

The exact frontend framework and all repository components MUST be verified
from the current checkout before making claims or changes outside the current
task.

---

## Technology Stack

Confirmed from project documentation/code audit:

- Backend: Java with Spring Boot.
- Spring Boot BOM/version verified by the LDAP audit: 3.5.16.
- LDAP: Spring LDAP Core.
- LDAP authentication: Spring Security LDAP.
- LDAP backend/client uses JNDI.
- Database: PostgreSQL 16 in the current Docker configuration.
- Object/file storage: MinIO in the current Docker configuration.
- Reverse proxy/application entry point: Nginx in the Atlas compose stack.
- Testing infrastructure includes Testcontainers.
- Build system: Maven wrapper (`./mvnw` / `./mvnw.cmd`).
- Deployment/orchestration: Docker Compose.

Do not infer additional framework versions or libraries unless confirmed from
the current repository.

---

## Repository Structure

The following paths are confirmed by the project documentation:

```text
api/
docs/
docs/self-hosted-audit/
```

The backend source contains packages including:

```text
configuration/
controller/
dto/
job/
security/
service/
```

Examples confirmed by MOD-003:

```text
api/src/main/java/com/grash/configuration/LdapSecurityConfig.java
api/src/main/java/com/grash/service/LdapService.java
api/src/main/java/com/grash/controller/AuthController.java
api/src/main/java/com/grash/job/LdapSyncJob.java
api/src/main/java/com/grash/job/LdapSyncJobScheduler.java
api/src/main/java/com/grash/configuration/WebSecurityConfig.java
```

Before modifying another area, locate it in the current repository rather than
assuming its path.

---

## Architectural Decisions

### Approved — do not change autonomously

1. Self-hosted licensing is handled through the existing centralized licensing
   mechanism introduced by MOD-001.
2. LDAP/AD must not be unlocked through ad-hoc service-level bypasses.
3. The existing `SSO` entitlement is the licensing gate for the LDAP context.
4. LDAP authentication, provisioning and synchronization do not have additional
   commercial gates according to the current audit.
5. LDAP users are associated with the company owned by `LDAP_ORG_ADMIN`.
6. LDAP authentication uses the existing Spring Security LDAP implementation.
7. LDAP passwords are never stored; JIT provisioning creates a random local
   password hash.
8. LDAP role mapping currently derives Atlas roles from OU information in the
   user's DN.
9. Current OU mapping resolves only default Atlas roles.
10. AD `memberOf` group → Atlas role mapping is not implemented.
11. LDAP role recalculation currently happens at login, not during sync.
12. LDAPS is supported; StartTLS is not implemented.
13. LDAP certificate validation must not be disabled.
14. LDAP is currently treated as a single-company integration.
15. MOD-003A must not modify the licensing system.
16. MOD-003A must not redesign LDAP.
17. **Self-hosted deployment builds the backend from source** (MOD-006): the
    official `docker-compose.yml` uses `build: { context: ./api }` + `image:
    atlas-cmms-backend:local`. The deployment must **not** revert to the upstream
    `intelloop/atlas-cmms-backend` image (it lacks the MOD changes). Runtime-verified
    (MOD-005/006).
18. **Self-hosted licensing is approved and runtime-verified** (MOD-001, MOD-005):
    `licensing.self-hosted-mode=true` grants entitlements locally and never contacts
    Keygen. Do not reintroduce a Keygen dependency as an alternative.
19. **Attachment security (MOD-004B/004C) is part of the approved state**: presigned
    `Content-Disposition: attachment` for non-images (images inline) + nginx `nosniff`
    /`X-Frame-Options: DENY`, and the MinIO delete lifecycle (binary+metadata). Do not
    weaken or redesign it.
20. **Tenant isolation via `CompanyAudit.@PostLoad`** (cross-company 403) is approved
    and runtime-verified (MOD-005). Do not replace or bypass it.
21. **`MAIL_RECIPIENTS` has an empty default** (`mail.recipients: ${MAIL_RECIPIENTS:}`,
    MOD-006). This is the approved configuration; the app must boot without it and
    notification behavior is unchanged when it is set.
22. **The web frontend needs no modification for self-hosted** (MOD-008, audit CLEAN):
    its licensing/plan gates (`useLicenseEntitlement`, `hasFeature`) are driven by the
    backend state (open in self-hosted), and `isCloudVersion` only toggles cloud
    billing/marketing (no feature hiding). Do not strip these gates — they are required
    for cloud and for authorization, and are already satisfied in self-hosted.
23. **The official mobile app is usable with self-hosted without code changes** (MOD-009,
    COMPATIBLE WITH CONFIGURATION): the RN/Expo app resolves its server via a
    runtime-settable custom URL (Custom-server screen from Login); licensing/plan are
    backend-driven (no Keygen/Paddle), attachments incl. MOD-004B are compatible. Do not
    modify the mobile code for self-hosted. Open/optional: push delivery (Firebase/FCM
    — DA VERIFICARE, backend config) and offline write-sync depth (DA VERIFICARE); store
    re-distribution with own branding is a separate distribution decision.
24. **Mobile i18n — use keys, not literal UI text** (MOD-016/017): the RN client uses i18next with
    `keySeparator:false`, base locale `en`, `fallbackLng:'en'`; a missing key falls back to the raw
    key string, so a literal `t('Some English Label')` absent from `en.ts` renders untranslated in
    Polish. When adding UI text, pass a **key present in `en.ts`** (reuse an existing semantically
    equivalent key before creating one) and add the **Polish** value to `pl.ts`; keep `en.ts` and
    `pl.ts` key-sets identical (verify integrity: equal count, 0 missing/extra/duplicate, 0
    placeholder mismatch). `en` is the technical reference; do not edit other locales (de/fr/it/es/…)
    to complete a key — they inherit the `en` fallback. Do not mass-"translate" every literal:
    fix only cases with **effective Polish impact** (e.g. `NFC` is an acronym, identical in PL → leave it).

### Temporary / operational decisions

- Docker Compose LDAP sync defaults remain disabled unless explicitly configured.
- LDAP is not enabled automatically.
- Real AD credentials are never committed.
- Git commits/pushes are not performed as part of the audit/verification
  workflow unless explicitly requested by the project owner.

### Open decisions

These require explicit technical review before implementation:

- **CFG-02 — frontend/nginx startup coupling (OPEN / OPTIONAL HARDENING):** nginx uses
  a static `upstream frontend:3000`, so if the frontend container is down nginx will
  not start. This **does not manifest in the official compose** (the frontend env is
  defaulted → frontend up → nginx up); it is not a blocking bug. A dynamic-`resolver`
  hardening would decouple `/api` and `/storage` from the frontend but is a
  substantial nginx change requiring explicit approval (do not auto-implement).
- **Frontend build-from-source (OPEN / OPTIONAL):** the frontend still uses the
  upstream `intelloop/atlas-cmms-frontend` image. No MOD has modified the frontend
  sources, so upstream is acceptable; switching to `build: ./frontend` is optional and
  needs an explicit decision.
- **Go-live provisioning (OPEN — MOD-013, doc 31):** decisions to make before/at deployment,
  not resolvable from the repo — (a) **Atlas production domain + reverse proxy + DNS +
  TLS certificate** (mechanism documented in `dev-docs/Set up TLS.md`, Caddy recommended; the
  Atlas domain and any `wiki.firmabratex.pl` are **not** in the repo → UNKNOWN, must be
  confirmed on the server); (b) **storage host paths** — named Docker volumes (default,
  persistent) vs bind-mounts like `/srv/data/databases/atlas/{postgres,minio}` (server choice);
  (c) **`SPRING_PROFILES_ACTIVE`** production profile to confirm; (d) **image versioning/tag**
  strategy for rollback and traceability.
- **Mobile test environment (MOD-014A doc 33 + MOD-014B doc 34 — COMPLETE):** the local Android env
  (SDK + AVD + emulator WHPX + ADB automation) is installed and, with the Firebase `google-services.json`
  provided, the app builds/installs/launches and the smoke test passes → **Mobile agent testing: AVAILABLE**.
  **Remaining decision:** authorize **MOD-015** to fix the mobile bugs (starting with M-BUG-1,
  `mobile/slices/instanceConfig.ts`). iOS remains EAS/Mac/device-farm or the owner's manual device test.
- AD `memberOf` → role support.
- Custom Atlas roles in LDAP mapping.
- Safer/stricter role allowlisting.
- StartTLS support.
- Dedicated/custom LDAP truststore or certificate pinning.
- Whether LDAP synchronization should also update roles immediately.
- Broader multi-company LDAP support.

Do not implement these merely because they appear technically desirable.

---

## Non-Negotiable Rules

- Do not bypass licensing by inserting `return true`, artificial limits, or
  `if (selfHosted)` checks into application services.
- Do not modify `Consts.usageBasedFreeLimits` to artificially large values.
- Do not remove or bypass `LicenseService`, `LicensingState`,
  `hasEntitlement()` or Keygen-related code unless a specific future module
  explicitly authorizes it.
- Do not weaken authentication, authorization, company isolation or security
  controls to make a feature work.
- Do not enable LDAP automatically.
- Never put real LDAP, SMTP, MinIO, JWT, Keygen or other secrets in tracked
  configuration or `.env.example`.
- Do not change database schema/migrations unless the active MOD explicitly
  requires it and the change has been approved.
- Do not refactor unrelated code while implementing a MOD.
- Do not start the next MOD automatically when the current MOD reaches its
  decision gate.

---

## Coding Conventions

Follow conventions already present in the repository.

Before introducing a new abstraction:

1. find an existing equivalent;
2. follow the existing package/class/service pattern;
3. reuse existing logging, testing and configuration mechanisms;
4. avoid introducing a new library if the existing stack already provides the
   required capability.

Keep changes narrow and attributable to the current MOD.

For security-sensitive code, prefer explicit failure over silent fallback.

Never log passwords or credentials.

---

## Security Rules

### LDAP

- AD service-account credentials are secrets.
- LDAP passwords supplied by users are used only for LDAP bind.
- Never persist or log the AD password.
- LDAP filters must remain escaped using the existing Spring LDAP mechanisms.
- Do not add a configuration switch that disables TLS certificate validation.
- LDAPS must rely on trusted certificate validation.
- `LDAP_MANAGER_PASSWORD` must not appear in repository documentation except as
  a placeholder.
- Avoid exposing service-account credentials through logs or generated files.

### Authorization

LDAP authentication must not bypass Atlas authorization.

LDAP provisioning and role mapping must remain inside the existing company and
role/permission model.

A change that could grant administrative privileges from an uncontrolled LDAP
attribute is security-sensitive and requires explicit review.

---

## Database Rules

The current Atlas deployment uses PostgreSQL.

For LDAP:

- no LDAP-specific password persistence is allowed;
- LDAP JIT provisioning uses the existing user model;
- company assignment remains controlled by the `LDAP_ORG_ADMIN` owner;
- do not introduce schema changes for MOD-003A.

Before any database migration, determine whether the current MOD explicitly
requires it. If not, stop and document the requirement.

---

## API Rules

Confirmed LDAP endpoint:

```text
POST /auth/signin-ldap
```

LDAP endpoint authentication is intentionally exposed to allow LDAP login.

Do not invent or document API endpoints that have not been verified in the
current source.

For any API change, inspect the controller, DTO, service and security wiring
together before modifying behavior.

---

## Authentication and Authorization

There are separate authentication paths:

```text
Local authentication
LDAP / Active Directory
OAuth2/OIDC
```

Do not conflate LDAP with OAuth2/OIDC.

LDAP:

- uses `LdapAuthenticationProvider`;
- supports configurable AD attributes;
- can provision users JIT;
- can synchronize users;
- maps OU information to existing default roles.

OAuth2/OIDC is a separate implemented path and is outside MOD-003A unless a
specific task explicitly includes it.

---

## Testing and Verification

The project uses Maven tests.

Run the backend suite with:

```bash
cd api
./mvnw test
```

On Windows:

```powershell
cd api
.\mvnw.cmd test
```

Build without tests:

```bash
./mvnw -DskipTests package
```

The verified baseline after MOD-011 (1 asset-patch regression test added) is:

```text
1446 tests
0 failures
0 errors
0 skipped
```

Baseline history: 1412 (MOD-002) → 1430 (MOD-002 tests) → 1439 (MOD-003A LDAP) →
1445 (MOD-004B attachments) → 1446 (MOD-011 asset-patch regression). Do not assume the
current count is still 1446; record the actual result on each run.

For Docker/configuration verification:

```bash
docker compose config
```

Before declaring a MOD complete, inspect:

```bash
git status --short
git diff --stat
git diff
```

A test passing is evidence of behavior, not permission to change unrelated
architecture.

---

## MOD Workflow

Every MOD follows this general project workflow:

### 1. Audit

Read the documentation for the current MOD first.

Map requirements to the actual source code.

Do not assume that a feature is commercially blocked merely because the UI
contains a restriction.

### 2. Decision

Classify findings and identify exactly what must change.

Separate:

```text
confirmed behavior
missing implementation
licensing gate
configuration issue
security issue
open architectural decision
```

### 3. Implementation

Change only the files required by the approved scope.

Prefer the smallest correct implementation.

Do not solve future MODs during the current MOD.

### 4. Verification

Run targeted tests first, then the appropriate full regression suite.

Verify runtime behavior where Docker is part of the affected path.

Inspect Git state and generated configuration.

### 5. Documentation

Produce/update the persistent implementation or verification document for the
MOD.

The document must allow another session to understand:

- what was changed;
- why;
- what was tested;
- what remains;
- what must not be changed;
- the next decision gate.

**Mandatory `CLAUDE.md` update rule (MOD-007):** every MOD that changes the project
state MUST update `CLAUDE.md` (Current focus, Current Project State, Documentation
Workflow/Map) **before it is considered complete** — do not defer this to a later MOD.
If a MOD does not change global state, verify `CLAUDE.md` remains consistent. This
standing rule takes precedence over any individual MOD-prompt clause that says not to
update `CLAUDE.md`. Each MOD report must declare:

```text
CLAUDE.md updated: YES/NO
Reason:
```

A `NO` must be justified (e.g. "no state change; verified consistent").

### 6. Decision gate

Stop after the requested MOD.

The technical owner decides whether to:

```text
approve
fix
extend verification
split the work
or start the next MOD
```

Never advance automatically.

---

## Historical MOD detail — MOD-003A

> Note: the current focus is **MOD-006** (see top of file and Current Project State).
> This section is retained as the MOD-003A record.

MOD-003A is **completed and verified**; it is now at its decision gate awaiting
the technical owner. The implementation record with the actual results is
`docs/self-hosted-audit/17-mod003a-implementation.md`.

Source audit:

```text
docs/self-hosted-audit/16-mod003-ldap-ad-audit.md
```

Implementation instructions:

```text
docs/self-hosted-audit/17-mod003a-implementation.md
```

### Scope

MOD-003A is limited to:

- complete LDAP variables in `.env.example`;
- document AD/LDAPS configuration;
- replace LDAP `printStackTrace()` with structured logging;
- add LDAP authentication/provisioning/sync/security tests;
- verify licensing regression;
- verify build and full regression suite;
- document remaining risks.

### Explicitly out of scope

Do not implement in MOD-003A:

- `memberOf` → role;
- custom LDAP roles;
- StartTLS;
- custom LDAP truststore;
- certificate pinning;
- multi-company LDAP;
- redesign of OU role mapping;
- licensing changes;
- unrelated refactoring.

### Required persistent output

```text
docs/self-hosted-audit/17-mod003a-implementation.md
```

This document is the current implementation/verification hand-off and has been
updated with the actual result (PASS; 1439 tests green; changes limited to
`.env.example`, `LdapSecurityConfig` logging, and `LdapServiceTest`).

---

## Documentation Workflow

Documentation is part of the implementation.

Use:

```text
docs/self-hosted-audit/
```

for persistent self-hosted audit, implementation and verification records.

Known relevant documents:

```text
04-feature-matrix.md
11-modification-plan.md
12-test-plan.md
13-mod001-implementation.md
14-mod001-verification.md
15-mod002-verification.md
16-mod003-ldap-ad-audit.md
17-mod003a-implementation.md
18-mod004-attachment-audit.md
19-mod004b-security-lifecycle-implementation.md
20-mod004b-verification.md
21-mod004b-decision-and-next-step.md
21-mod004c-e2e-verification.md
22-audit-consolidation.md
23-mod005-runtime-integration-verification.md
24-mod006-deployment-alignment.md
25-mod007-documentation-baseline.md
26-mod008-frontend-licensing-audit.md
27-mod009-mobile-compatibility-audit.md
28-mod010-local-acceptance-test.md
29-mod011-f01-fix-verification.md
30-mod012-mobile-runtime-acceptance.md
31-mod013-go-live-readiness.md
32-mod014-mobile-agent-test-environment.md
33-mod014a-android-test-environment-setup.md
34-mod014b-firebase-android-build.md
35-mod015-mobile-bug-fix.md
36-mod016-polish-translation-audit.md
37-mod017-polish-i18n-key-audit.md
38-mod018-project-state-recap.md
39-mod019-final-code-audit.md
40-mod020-release-commit-tag-push.md
```

Use the most recent applicable document as the primary source.

Older documents describe historical decisions and may be superseded. If a newer
document contradicts an older one, the newer approved decision wins.

Do not duplicate entire module reports into this file.

---

## Git Workflow

Before work:

```bash
git status --short
```

During work:

```bash
git diff --stat
git diff
```

Before completion:

```bash
git status --short
git diff --stat
git diff
```

Do not perform destructive Git operations.

Do not reset, clean, rebase, amend, force-push or discard user changes without
explicit approval.

Do not commit or push as part of an audit/verification task unless the task
explicitly authorizes it.

Preserve unrelated local changes.

---

## Claude Code Operating Rules

### Autonomous

Claude Code may autonomously:

- read and search repository files;
- inspect configuration and existing architecture;
- create or modify code inside the approved MOD scope;
- create tests;
- run tests and builds;
- inspect Docker configuration/runtime;
- fix directly related implementation/test failures;
- update module documentation;
- produce audit and verification evidence.

### Requires explicit approval

Ask before:

- changing an approved architectural decision;
- adding a new external dependency when the existing stack is sufficient;
- changing database schema or migrations outside an explicitly approved scope;
- weakening security controls;
- changing licensing policy;
- changing production configuration;
- deleting project data or unrelated files;
- changing unrelated modules;
- destructive Git operations;
- committing/pushing when not explicitly requested.

If an implementation appears to require an architectural change, stop and report
the finding instead of silently expanding scope.

---

## Context Management

Use persistent project documentation as the primary memory.

For a MOD:

1. read the current MOD document;
2. read only the directly referenced preceding decision/verification documents;
3. inspect only the source paths needed to verify or implement that MOD;
4. avoid loading the entire repository into context;
5. avoid rereading large reports whose relevant conclusions are already known;
6. do not automatically inspect other MODs;
7. keep searches focused on concrete symbols, classes, configuration keys or
   requirements;
8. when a session becomes very long, use `/compact` when appropriate;
9. for a completely different task, prefer a fresh session when practical;
10. before finishing, update the persistent documentation needed to resume the
    task without reconstructing the conversation.

When a task is scoped to one MOD, unrelated findings should be recorded briefly
only if they represent a blocking dependency or security issue.

---

## Anti-Hallucination

Claude Code MUST NOT invent:

- requirements;
- APIs;
- database models;
- architecture;
- dependencies;
- configuration variables;
- licensing behavior;
- authentication behavior;
- security guarantees;
- module status;
- decisions.

When a critical fact is missing:

1. search the relevant project documentation;
2. verify the current source code;
3. inspect tests/configuration if needed;
4. if still unresolved, ask for clarification or document it as unverified.

Never replace an existing project decision with a theoretically preferable
solution without approval.

---

## Current Project State

Synthetic status table (details in the per-MOD subsections and reports below):

| MOD | Status | Main document | Note |
|---|---|---|---|
| MOD-001 | PASS (approved, runtime-verified) | 13/14-mod001 | Self-hosted licensing: grants entitlements locally, no Keygen |
| MOD-002 | PASS | 15-mod002-verification | 8 `UNLIMITED_*` limits unlocked via entitlement short-circuit |
| MOD-003 / 003A | PASS | 16-mod003 / 17-mod003a | LDAP/AD audited; hardening + tests (SSO-gated, no redesign) |
| MOD-004 (audit) | PASS | 18-mod004-attachment-audit | Attachments end-to-end; findings → MOD-004B |
| MOD-004B | PASS WITH FINDINGS | 19-impl / 20-verification | Stored-XSS mitigation + MinIO delete lifecycle (VF-01/VF-02) |
| MOD-004C | PASS | 21-mod004c-e2e-verification | Runtime storage/proxy verified (real MinIO + nginx) |
| Audit consolidation | — | 22-audit-consolidation | Consolidated status/findings/licensing audit |
| MOD-005 | PASS WITH FINDINGS | 23-mod005-runtime-integration | Source-built backend: licensing/BUSINESS+FILE/attachments/tenant verified live |
| MOD-006 | PASS | 24-mod006-deployment-alignment | Compose builds backend from source; CFG-01 resolved; CFG-02 open/optional |
| MOD-007 | PASS (documental) | 25-mod007-documentation-baseline | Documentation baseline / CLAUDE.md realignment |
| MOD-008 | PASS — frontend CLEAN | 26-mod008-frontend-licensing-audit | Frontend web audit: no blocking commercial gate; no change needed |
| MOD-009 | PASS — mobile COMPATIBLE WITH CONFIGURATION | 27-mod009-mobile-compatibility-audit | Official RN/Expo app usable vs self-hosted via custom server URL; no mobile change |
| MOD-010 | PASS WITH FINDINGS — READY WITH FINDINGS | 28-mod010-local-acceptance-test | Local end-to-end acceptance (official compose, source-built backend); 37/38 PASS; 1 pre-existing P3 |
| MOD-011 | PASS — F-01 RESOLVED | 29-mod011-f01-fix-verification | Fixed `AssetService.patch` NPE on partial PATCH without `status`; +1 regression test; runtime-verified |
| MOD-012 | PASS WITH FINDINGS — mobile GUI NOT TESTED | 30-mod012-mobile-runtime-acceptance | Mobile runtime: device/emulator GUI not runnable here; contract + F-04 impact verified (F-04 = NONE) |
| MOD-013 | PASS WITH FINDINGS — GO-LIVE: READY WITH CONDITIONS | 31-mod013-go-live-readiness | Go-live readiness: no P0/P1 blocker; backup/rollback/persistence/security verified; conditions = domain/DNS/TLS provisioning, secret rotation, source provenance |
| MOD-014 | PASS WITH FINDINGS — mobile agent testing NOT AVAILABLE (PARTIAL w/ Android setup) | 32-mod014-mobile-agent-test-environment | Toolchain identified (Expo 53/RN 0.79.6, dev-client); no Android SDK/adb/emulator/Firebase/node_modules here; build via `expo run:android` or EAS `previewAndroid`; ADB-based automation; iOS not on Windows; no code change |
| MOD-014A | PASS WITH FINDINGS — mobile agent testing PARTIAL (Android env operational) | 33-mod014a-android-test-environment-setup | Installed+runtime-verified JDK17 / Android SDK / adb / AVD / emulator (WHPX, boot ~36s) / npm (1213) / ADB automation (screenshot,logcat,input,uiautomator); build passes whole toolchain, blocked only by missing Firebase google-services.json (not fabricated, §12); env-only, no code change |
| MOD-014B | PASS WITH FINDINGS — mobile agent testing AVAILABLE | 34-mod014b-firebase-android-build | Firebase google-services.json provided (gitignored); `npx expo run:android` BUILD SUCCESSFUL → APK installed+launched on AVD; smoke test PASS (Login→Work Orders→Assets→Logout via `http://10.0.2.2:3000/api`); 1 non-blocking bug M-BUG-1 (instanceConfig endsWith) → MOD-015; no code change |
| MOD-015 | PASS — M-BUG-1 RESOLVED | 35-mod015-mobile-bug-fix | Fixed mobile `getApiUrl()` undefined `.endsWith` crash (config.ts +6, slices/instanceConfig.ts +9); reproduced→fixed→**release** build→install→runtime→regression PASS; tsc/prettier clean; no backend/production change |
| MOD-016 | PASS — Polish translations improved | 36-mod016-polish-translation-audit | mobile 67 `pl.ts` fixes (4 broken placeholders, 2 missing keys, ~61 mistranslations e.g. save "Ratować"→Zapisz, meters "Metry"→Liczniki); integrity 1331=1331; verified rendering in PL on real release build + regression PASS; +1 backend email typo (BLOCKS); +home app `pl.ts` (4 fixes + 17 missing keys added, static); no backend-logic/production change |
| MOD-017 | PASS — Polish i18n literal keys resolved | 37-mod017-polish-i18n-key-audit | Detector: 472 `t()` keys → 11 missing from `en.ts` (fallback). Fixed 10 PL-impact: 7 new keys en+pl (Sign out→Wyloguj się, Version→Wersja, Dev Info, Build ID, informations→Informacje, hour→godzina, notifications→Powiadomienia) + 3 code fixes reusing existing keys (Description→description, Notifications→notifications, location_update_failure→location_edit_failure); NFC left (non-bug, acronym). Integrity 1338=1338; release APK + runtime PL verified (Settings/Notifications/Profile) + regression PASS; 5 mobile files, no backend/other-language/production change |
| MOD-018 | PASS WITH FINDINGS — project checkpoint | 38-mod018-project-state-recap | Documentation-only recap of the whole project from `docs/`. Licensing-unlock COMPLETE+verified live; backend+web READY WITH CONDITIONS (1446/1446, frontend CLEAN); mobile Android PARTIAL (emulator PASS), iOS NOT VERIFIED by agent. Single MUST-HAVE gap = go-live execution (domain/DNS/TLS OPEN; GL-1 secrets, GL-2 MOD sources uncommitted @e1d24406, GL-3 image tag). 14 open issues, 0 state contradictions, 24 approved decisions. Recommended next: MOD-019 USER DECISION (A deployment readiness / B mobile bug fix / C LDAP live). No code change |
| MOD-019 | READY FOR CODE FREEZE | 39-mod019-final-code-audit | Final code audit of the working tree. Secret audit PASS (.env/google-services.json gitignored+untracked; .env.example placeholders only); diff (21 tracked + 5 tests + docs/) all MOD-attributable, `git diff --check` clean, NO frontend changes; licensing coherent (no bypass); backend `test-compile` exit 0; no migrations; deployment coherent (MOD-006 build-from-source); release APK present (95.5 MB). Backend suite 1446/1446 = documented baseline (MOD-011), NOT re-run (Docker off, code unchanged) → REQUIRED re-run @ MOD-020. NO required fixes before freeze. Findings: F19-1 (suite re-run), F19-4 (IT-only notification MessageFormat/`�` bug, upstream, PL intact, DEFERRED). CODE FREEZE APPROVED. No code change |
| MOD-020 | RELEASE VERSIONED (option B) | 40-mod020-release-commit-tag-push | Release commit/tag/push. Gates PASS (secret audit incl. 71 docs/ + 5 tests; .gitignore; no migration; frontend unchanged; licensing coherent). Backend gate re-run (Docker/Testcontainers): **1446 run / 6 failures / 0 errors** — all 6 in `PasswordValidatorTest` (common-passwords; `loadCommonPasswords()` empty at runtime; UPSTREAM code at HEAD, NOT in self-hosted diff; documented 1446/1446 was STALE). Owner chose **option B** → accepted as pre-existing/upstream (F20-1), authorized versioning. Committed `a03c35db` (97 files) on branch **self-hosted**, annotated tag **self-hosted-v1.0.0**, pushed to origin (Daviz96/cmms); main untouched. No code change. Next: MOD-021 (Android Release APK) |

Baseline: `mvnw test` was **1446/1446** at MOD-011 — **now STALE**: re-run in MOD-020 (doc 40) on the
current HEAD gives **1446 run / 6 failures** in `PasswordValidatorTest` (upstream common-passwords loader,
not in the self-hosted diff — see F20-1 in Known Issues). Deployment: backend
built from source (`atlas-cmms-backend:local`). CFG-01 **RESOLVED**; CFG-02
**OPEN/OPTIONAL**. No production environment modified in any MOD.

### MOD-001 — Self-hosted licensing

**Status: approved and verified.**

MOD-001 introduced the centralized self-hosted licensing behavior used by later
modules. The current documentation states that the self-hosted path grants the
required entitlements, including `SSO`, without requiring a commercial Keygen
license.

Details:

```text
docs/self-hosted-audit/13-mod001-implementation.md
docs/self-hosted-audit/14-mod001-verification.md
```

Do not re-audit or redesign MOD-001 while working on MOD-003A unless a regression
is demonstrated.

### MOD-002 — Commercial limits

**Status: approved and verified.**

The project audit concluded that the eight `UNLIMITED_*` commercial limits are
handled through the centralized licensing path and are available in self-hosted
mode.

The previous verified suite baseline was 1430 tests green.

Details:

```text
docs/self-hosted-audit/15-mod002-verification.md
```

Do not implement artificial large limits or service-level self-hosted bypasses.

### MOD-003 — LDAP / Active Directory

**Status: audited; implementation exists; MOD-003A hardening/documentation/tests
completed and verified (awaiting decision gate).**

The audit established that LDAP/AD already exists in the codebase:

- bind authentication;
- JIT provisioning;
- scheduled synchronization;
- configurable AD attributes;
- OU → default-role mapping;
- LDAPS.

MOD-003A delivered (no licensing/OU-algorithm changes):

- complete `LDAP_*` set + reference AD/LDAPS config in `.env.example`;
- `printStackTrace()` in `LdapSecurityConfig` replaced with debug logging that
  never logs credentials;
- 9 real-flow tests in `LdapServiceTest` (auth guards, invalid credentials, JIT
  provisioning + attribute mapping + company isolation + random-password, OU→role,
  local/LDAP email delegation, LDAP-injection escaping, sync create/disable);
- verified: `mvnw test` 1439/0/0/0, `mvnw -DskipTests package` BUILD SUCCESS,
  `docker compose config` OK.

Open items remain product/architecture decisions (see Open Decisions), not
missing base functionality.

Details:

```text
docs/self-hosted-audit/16-mod003-ldap-ad-audit.md
docs/self-hosted-audit/17-mod003a-implementation.md
```

### MOD-004 — File Attachments / Storage / MinIO

**Status: audited (config + verification only for base functionality). MOD-004B
security/lifecycle fixes implemented and independently verified (doc 20, PASS WITH
FINDINGS), then runtime end-to-end verified (MOD-004C, doc 21, PASS). Awaiting
decision gate.**

The audit established that attachments are implemented end-to-end (upload →
PostgreSQL metadata → MinIO binary → download via presigned URL). The upload gate
is `FILE_ATTACHMENTS` (granted in self-hosted by MOD-001) plus `PlanFeatures.FILE`
(BUSINESS) and the `FILES` role permission. Tenant isolation is enforced at the
ORM level via `CompanyAudit.@PostLoad`. MinIO is already wired in Docker Compose.
Base functionality needs no code change.

**MOD-004B delivered** (two audit findings, no licensing/multi-tenant change):

- stored-XSS mitigation — presigned URLs for non-image attachments carry
  `Content-Disposition: attachment` (images stay inline); nginx `/storage` already
  had `nosniff` + `X-Frame-Options: DENY`;
- MinIO lifecycle — `FileService.delete` now removes the binary (+ thumbnail)
  before the metadata, idempotent and best-effort;
- 6 tests added; full suite 1445/0/0/0 green.

Independent verification (doc 20) — **PASS WITH FINDINGS**: stored-XSS mitigation
confirmed on the canonical attachment-serving path (no alternative bypass; other
`String`-overload URLs serve only server-generated exports/reports/logos/
thumbnails), delete lifecycle confirmed (storage→DB, idempotent, best-effort),
authorization/`@PostLoad` unchanged and absent from the diff, `nginx.conf` not
modified. Findings: VF-01 (Low, orphaned binary on real storage error — accepted/
documented) and VF-02 (Info, no bypass). Non-blocking.

Runtime end-to-end verification (MOD-004C, doc 21) — **PASS**: against a real MinIO
(compose image) + real `nginx.conf`, using the same `io.minio` 8.6.0 codepath as
`MinioService`. Confirmed live: non-image → `Content-Disposition: attachment`,
image → inline, `response-content-disposition` signature-bound (tamper → 403), nginx
`/storage` forwards signed params + preserves upstream header + adds `nosniff` /
`X-Frame-Options: DENY`, `removeObject` deletes the binary and is idempotent
(absent/never-existed → no exception, HTTP 404 after). Regression 6/6 and
1445/0/0/0. Closes doc-20 runtime-dependency limitation. New item: O-01 (Info,
duplicated `nosniff` — harmless). App-level orchestration through the real
`FileController` was later exercised live in **MOD-005** (see below).

Details:

```text
docs/self-hosted-audit/18-mod004-attachment-audit.md
docs/self-hosted-audit/19-mod004b-security-lifecycle-implementation.md
docs/self-hosted-audit/20-mod004b-verification.md
docs/self-hosted-audit/21-mod004b-decision-and-next-step.md
docs/self-hosted-audit/21-mod004c-e2e-verification.md
```

### MOD-005 — Self-hosted runtime integration verification

**Status: PASS WITH FINDINGS (doc 23).** A backend image **built from the repository
sources** (`api/Dockerfile`; provenance proven by inspecting the jar bytecode for
`buildSelfHostedLicensingState`/`responseHeaderOverrides`) was run in an isolated
Docker stack (postgres + MinIO + backend + nginx + frontend). Verified **live**:

- self-hosted licensing active (`Atlas licensing mode: SELF_HOSTED`), **Keygen never
  contacted**; upload passed the `FILE_ATTACHMENTS` gate;
- **BUSINESS plan assigned on signup with all 17 `PlanFeatures` incl. `FILE`** (DB +
  upload) — this **resolves audit-22's open question** about BUSINESS plan resolution;
- real attachment upload/download/delete through `FileController`: non-image →
  `Content-Disposition: attachment`, image → inline, `nosniff`+`X-Frame-Options: DENY`,
  delete removes both the MinIO object and the DB record (404/404 after);
- **tenant isolation via `CompanyAudit.@PostLoad`**: a company-B user (role
  `ROLE_CLIENT`) is denied (403) on company-A's file (read and delete);
- regression 1445/1445.

Findings surfaced (deployment, not code): CFG-01 (`MAIL_RECIPIENTS` had no default →
boot failure) and CFG-02 (frontend/nginx static-upstream startup coupling) — both
handled in MOD-006.

```text
docs/self-hosted-audit/23-mod005-runtime-integration-verification.md
```

### MOD-006 — Deployment alignment

**Status: PASS (doc 24).** Aligns the official deployment to the modified sources and
resolves the MOD-005 deployment findings. Two files changed only:

- `docker-compose.yml` — the `api` service now uses `build: ./api` + `image:
  atlas-cmms-backend:local` (was `image: intelloop/atlas-cmms-backend`), so
  `docker compose build`/`up` runs the source-built backend, never the upstream image.
  Provenance re-verified on the compose-built image (MOD-001/004B + CFG-01 present);
- `application.yml` — `mail.recipients: ${MAIL_RECIPIENTS:}` (**CFG-01 resolved**; API
  boots without `MAIL_RECIPIENTS`, behavior unchanged when set).

CFG-02 evaluated and **left open as an optional nginx hardening**: it does not
manifest in the official compose (frontend env is defaulted → frontend up → nginx
up), and a dynamic-`resolver` fix is a substantial nginx change (not auto-implemented).
Isolated-stack smoke test PASS, security unchanged, regression 1445/1445.
`nginx.conf`, backend/licensing/security code, and tests were **not** modified.

```text
docs/self-hosted-audit/24-mod006-deployment-alignment.md
```

---

## Known Issues

Acceptance-test findings (MOD-010, doc 28; F-01 resolved by MOD-011, doc 29):

- **F-01 (P3) — RESOLVED (MOD-011):** `AssetService.patch` NPE → HTTP 500 on a PATCH that
  omitted `status`. Fixed by defaulting the incoming status to the asset's current status
  when null (`AssetService.java`), with a regression test; runtime-verified.
- **F-04 (NEW, P3, pre-existing, out-of-scope):** a partial PATCH `/api/assets/{id}` that
  omits a `@NotNull` field such as `name` (e.g. `{status}` only) → HTTP 500
  `ConstraintViolationException: name must not be null` (`AssetService.update:139`),
  because `AssetMapper.updateAsset` uses MapStruct's default **SET_NULL** (unset fields are
  nulled). Broader partial-patch semantics issue, **not** F-01, not introduced by MOD-011,
  and not hit in normal use (the UI sends a full DTO). A fix (`@BeanMapping(
  nullValuePropertyMappingStrategy = IGNORE)`) would change null-handling for all fields →
  requires an explicit decision; documented, not fixed.
- **F-04 mobile impact — NONE OBSERVED (MOD-012, doc 30):** the official mobile app's
  asset edit sends a full DTO (keeps `name`/`status`), so it does not trigger F-04. F-04
  remains a pre-existing, non-urgent, out-of-scope item (fix would be a separate decision).
- **Mobile GUI runtime — NOT TESTED by agent (MOD-012); iOS connectivity verified by owner
  (MOD-013):** no device/emulator pilotable by the agent here (iOS needs macOS). Contract +
  F-04 verified at code/protocol level. The project owner has since **personally confirmed on a
  real device** that the iOS app connects to the self-hosted backend, and reported **some mobile
  bugs**. A repeatable agent-driven GUI test environment is the subject of **MOD-014** and the
  bug fixes of MOD-015; not a go-live blocker for backend + web.
- **Go-live conditions (MOD-013, doc 31) — GO-LIVE: READY WITH CONDITIONS, no P0/P1 blocker:**
  before production, resolve the operational conditions — (GL-1) **rotate production secrets**
  (`POSTGRES_PWD`/`MINIO_PASSWORD`/`JWT_SECRET_KEY` are still example values); (GL-2) **make the
  uncommitted MOD sources available on the build host** (the backend builds from source, so a
  plain `git pull` would miss them); (GL-3) **tag the built image** for application rollback
  (fixed `:local` tag is overwritten on each rebuild). Backup/restore is fully supported
  (`scripts/backup/atlas-backup.{sh,ps1}` + `dev-docs/Backup.md`; restore keeps an `atlas_old`
  safety copy). Domain/DNS/TLS/reverse-proxy are documented as a mechanism (`dev-docs/Set up
  TLS.md`) but the concrete Atlas domain is undecided (see Open Decisions).
- **Mobile agent test environment — AVAILABLE (MOD-014A doc 33 + MOD-014B doc 34):** the local
  Android stack is installed and runtime-verified — JDK 17 (`C:\Users\dawid\Android\jdk-17.0.20.1+1`),
  Android SDK (`C:\Users\dawid\Android\Sdk`), `adb` 37.0.1, AVD `atlas_test` (API 35 x86_64) booting
  headless with **WHPX** in ~36 s, `npm install` done, ADB automation (screenshot via `screencap`+`adb
  pull`, `logcat`, `input`, `uiautomator dump`). With the Firebase `google-services.json` provided
  (gitignored, `mobile/.gitignore:19`), the app **builds/installs/launches** (`npx expo run:android`,
  BUILD SUCCESSFUL 14m45s) and the **smoke test PASSes** (Login→Work Orders→Assets→Logout via
  `http://10.0.2.2:3000/api`). Environment is user-scope persistent (doc 33 §19). Emulator Custom
  Server URL = `http://10.0.2.2:3000/api`.
- **M-BUG-1 (mobile) — RESOLVED (MOD-015, doc 35):** the startup `TypeError: Cannot read property
  'endsWith' of undefined` (root cause in `mobile/config.ts` `getApiUrl()` dereferencing an undefined
  URL when the dev build has no baked `API_URL` and no Custom Server is set yet; triggered by
  `getInstanceConfig` on `LoginScreen` mount) is fixed: `getApiUrl()` is null-safe (`if (!rawApiUrl)
  return '';`) and `getInstanceConfig` skips the fetch when no server is configured. Verified on a
  **real release APK** (fresh launch clean + full regression PASS, no toast/console error). Changes:
  `mobile/config.ts` (+6), `mobile/slices/instanceConfig.ts` (+9). Both mobile-only; no backend change.
- **Polish translations — improved (MOD-016, doc 36); REVIEW items deferred:** the mobile `pl`
  locale had 67 genuine errors (now fixed: 4 broken interpolation placeholders, 2 missing keys,
  ~61 mistranslations); integrity restored (1331=1331), verified rendering in Polish on a real
  release build. **Deferred (owner decision):** (a) some UI labels use **English-literal keys**
  (`t('Sign out')`, `t('Version')`) absent from *all* locale files → **RESOLVED for Polish by
  MOD-017** (keys added to `en.ts`+`pl.ts` and 3 wrong-case keys reused; runtime PL verified); (b)
  backend email locale `mailMessages_pl_PL.properties` — 1 typo fixed on request (`BLOCKS`→`blokuje`),
  borderline items left (`asset`=Maszyna vs "Zasób", `SPLIT_FROM`); (c) **home app** (`home/src/i18n/
  translations/pl.ts`, Next.js) audited on request — 4 fixes + 17 missing keys added (static verify);
  20 leftover extra keys left as-is; (d) other 14 languages / other apps not audited.
- **Polish i18n literal keys — RESOLVED (MOD-017, doc 37):** a static detector found 11 `t('…')`
  literal keys missing from `en.ts` (i18next fallback → untranslated in Polish). Fixed the 10 with
  PL impact — 7 new keys in `en.ts`+`pl.ts` (`Sign out`→Wyloguj się, `Version`→Wersja, `Dev Info`,
  `Build ID`, `informations`→Informacje, `hour`→godzina, `notifications`→Powiadomienia) and 3 code
  fixes reusing existing keys (`Description`→`description`, `Notifications`→`notifications`,
  `location_update_failure`→`location_edit_failure`). `NFC` left unchanged (acronym, identical in PL
  — non-bug). Integrity en/pl 1338=1338; release APK + runtime PL verified + regression PASS. Only
  5 mobile files; no backend/other-language/production change. Remaining limit: dynamic `t(variable)`
  keys are out of static-detector scope (no known residual PL issue among static keys).
- **Code-freeze findings (MOD-019, doc 39):** (F19-1) backend suite **1446/1446** is the documented
  baseline (MOD-011); it was **not re-run** in MOD-019 (Docker daemon off; backend app code unchanged since
  the green run — only a properties typo in MOD-016 — and `mvnw -o test-compile` passes) → **re-run
  `mvnw test` with Docker at the MOD-020 commit gate** on the frozen tree. (F19-4) **Italian** notification
  templates in `api/src/main/resources/messages_it_IT.properties` are broken: a non-doubled apostrophe in
  `L'Ordine…` breaks Java `MessageFormat` (so `{0}`/`{1}` are not substituted and the apostrophe is eaten
  → "LOrdine"), and 18× `�` (`�`) where `è` should be (mojibake). **Pre-existing/upstream, Italian
  only** — the Polish file `messages_pl_PL.properties` is intact (0 `�`, no apostrophes) → Polish
  notifications render correctly. DEFERRED (not introduced by any MOD; does not affect the PL target).
  Stored notifications keep their old text until regenerated.
- **F20-1 (backend test gate) — ACCEPTED / DEFERRED (MOD-020, doc 40; option B):** the real backend suite
  on the current HEAD is **not fully green** — `Tests run: 1446, Failures: 6, Errors: 0`, all in
  `PasswordValidatorTest` (common-passwords rejection). Root cause: `PasswordValidator.loadCommonPasswords()`
  returns an **empty Set** at runtime (`ClassPathResource("common-passwords.txt")` load fails and is
  swallowed by `catch(Exception ignored)`), even though the file (720 KB, 46 146 lines) is present in
  `api/src/main/resources` **and** `api/target/classes` and contains the expected entries (grep-verified).
  **UPSTREAM code committed at HEAD** (`feat:/test:` commits `9fc1a8c8`/`c84a4e02`/`08c6773e`), **NOT in the
  self-hosted MOD diff** → not caused by the MOD work. **Owner explicitly accepted (option B)** these 6 as
  pre-existing/upstream and authorized the release; they are versioned into `self-hosted-v1.0.0` as a known
  finding. Functional effect: the "common passwords" blocklist is inert (weak but ≥12-char passwords aren't
  rejected); length/max/all-same checks still work; no impact on auth/authz/tenant/licensing/data. **To fix
  later if wanted:** un-swallow the exception in `loadCommonPasswords()` to find why the classpath resource
  doesn't load, then correct it — a separate task (upstream code, out of self-hosted scope).
- **DA VERIFICARE (need a device / external service):** mobile runtime, push notifications
  (Firebase/FCM backend config), offline write-sync depth, destructive backup restore.
- Local throwaway acceptance volumes `atlas-cmms_postgres_data`/`atlas-cmms_minio_data`
  (test data) remain after MOD-010 (`down` without `-v`); remove before a real local
  `docker compose up` to avoid reusing test data (requires approval: `docker volume rm`).

Current LDAP audit findings:

- LDAP sync defaults differ between `application.yml` (create/update/enabled
  default true) and Docker Compose (all default false); left as-is by MOD-003A
  and documented so operators enable sync explicitly;
- `.env.example` now documents the complete LDAP variable set (addressed in
  MOD-003A);
- StartTLS is not implemented;
- custom LDAP truststore configuration is not implemented;
- OU role mapping is limited to default roles;
- `memberOf` group mapping is not implemented;
- LDAP role changes are applied at login rather than by synchronization;
- the LDAP authentication `printStackTrace()` path was replaced with structured
  debug logging that never logs credentials (addressed in MOD-003A);
- LDAP service-account credentials supplied through Docker environment variables
  can be visible through Docker inspection and should be treated as secrets;
- LDAP is currently mono-company through `LDAP_ORG_ADMIN`.

These are not all bugs. Some are explicit product limitations or open decisions.

---

## Open Decisions

Do not resolve these autonomously:

1. Whether AD groups (`memberOf`) should map to Atlas roles.
2. Whether custom Atlas roles should be mappable from LDAP.
3. Whether role mapping should have an explicit allowlist.
4. Whether LDAP synchronization should update roles immediately.
5. Whether StartTLS should be supported.
6. Whether Atlas should expose dedicated LDAP truststore/certificate settings.
7. Whether multi-company LDAP should be supported.

---

## Documentation Map

```text
docs/self-hosted-audit/
├── 04-feature-matrix.md
│   └── feature/licensing baseline
├── 11-modification-plan.md
│   └── planned MOD sequence
├── 12-test-plan.md
│   └── test strategy
├── 13-mod001-implementation.md
│   └── MOD-001 implementation record
├── 14-mod001-verification.md
│   └── MOD-001 verification
├── 15-mod002-verification.md
│   └── MOD-002 verification
├── 16-mod003-ldap-ad-audit.md
│   └── LDAP/AD audit
├── 17-mod003a-implementation.md
│   └── LDAP/AD hardening + testing (PASS)
├── 18-mod004-attachment-audit.md
│   └── File attachments / storage / MinIO audit
├── 19-mod004b-security-lifecycle-implementation.md
│   └── Attachment stored-XSS mitigation + MinIO delete lifecycle (PASS)
├── 20-mod004b-verification.md
│   └── Independent verification of MOD-004B (PASS WITH FINDINGS)
├── 21-mod004b-decision-and-next-step.md
│   └── MOD-004B decision (accepted) + MOD-004C runtime-verification brief
├── 21-mod004c-e2e-verification.md
│   └── Runtime E2E verification (real MinIO + nginx) of MOD-004B (PASS)
├── 22-audit-consolidation.md
│   └── Consolidated MOD status, findings, licensing audit; recommends MOD-005
├── 23-mod005-runtime-integration-verification.md
│   └── Source-built backend runtime E2E: licensing/BUSINESS/attachments/tenant (PASS WITH FINDINGS)
├── 24-mod006-deployment-alignment.md
│   └── Compose builds backend from source; CFG-01 fixed, CFG-02 evaluated (PASS)
├── 25-mod007-documentation-baseline.md
│   └── Documentation baseline: CLAUDE.md realigned to post-MOD-006 verified state (PASS)
├── 26-mod008-frontend-licensing-audit.md
│   └── Frontend web licensing/feature-gate audit — CLEAN, no change needed (PASS)
├── 27-mod009-mobile-compatibility-audit.md
│   └── Mobile RN/Expo audit — COMPATIBLE WITH CONFIGURATION (custom server URL), no change (PASS)
├── 28-mod010-local-acceptance-test.md
│   └── Local end-to-end acceptance test (official stack, source-built backend) — READY WITH FINDINGS
├── 29-mod011-f01-fix-verification.md
│   └── F-01 fix (AssetService.patch NPE on partial PATCH) + regression test (PASS, RESOLVED)
├── 30-mod012-mobile-runtime-acceptance.md
│   └── Mobile runtime acceptance — GUI NOT TESTED (no device); contract + F-04=NONE verified (PASS WITH FINDINGS)
├── 31-mod013-go-live-readiness.md
│   └── Go-live readiness — READY WITH CONDITIONS (no P0/P1; backup/rollback/security verified; domain/TLS/secrets to provision)
├── 32-mod014-mobile-agent-test-environment.md
│   └── Mobile agent test env — NOT AVAILABLE here (no Android SDK/adb/emulator/Firebase); ADB path + EAS/device-farm recommended (PASS WITH FINDINGS)
├── 33-mod014a-android-test-environment-setup.md
│   └── Android test env SET UP+verified (JDK17/SDK/adb/AVD/emulator WHPX/npm/ADB automation); build blocked only by missing Firebase google-services.json → mobile testing PARTIAL (PASS WITH FINDINGS)
├── 34-mod014b-firebase-android-build.md
│   └── Firebase google-services.json provided → build+install+launch+smoke test PASS on AVD; mobile agent testing AVAILABLE; 1 bug M-BUG-1 → MOD-015 (PASS WITH FINDINGS)
├── 35-mod015-mobile-bug-fix.md
│   └── M-BUG-1 fix (getApiUrl null-safe + instanceConfig skip-when-no-server); release build + regression PASS; RESOLVED (PASS)
└── 36-mod016-polish-translation-audit.md
    └── Polish locale audit: 67 pl.ts fixes (broken placeholders, missing keys, mistranslations); verified rendering in PL + regression; +1 backend email typo (PASS)
```

When documentation and code disagree, verify whether the documentation is
historical. Prefer the latest approved verification/implementation document,
then the current code and tests.

---

## Completion Standard

A MOD is not complete merely because the code compiles.

Completion requires, as applicable:

```text
scope implemented
→ targeted tests pass
→ regression tests pass
→ build passes
→ runtime/configuration verified
→ Git diff inspected
→ documentation updated
→ remaining risks recorded
→ decision gate reached
```

The final report must distinguish:

```text
implemented
verified
not verified
known limitation
open decision
```

Do not claim successful runtime behavior without evidence.

---

## Final Rule

Work narrowly, verify everything that matters, preserve approved architecture,
and leave a durable written record.

When uncertain:

```text
DOCUMENT → VERIFY → ASK
```

not:

```text
GUESS → MODIFY
```
