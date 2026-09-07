# Atlas CMMS self-hosted — Snapshot progetto · 2026-09-07

> **Documento datato (fotografia del giorno).** Stato reale verificato **sul codice**, non solo sulla documentazione.
> Deployment live: `https://cmms.firmabratex.pl` (LAN-only, dietro **Caddy** TLS wildcard). Fork `Daviz96/cmms`,
> branch **`self-hosted`** (HEAD `640a510b`). Immagini Docker Hub: `dablio96/self-hosted-cmms-{backend,frontend}`.
> Stato/changelog storico "vivo": [PROJECT-STATUS.md](PROJECT-STATUS.md) · questo file è lo snapshot puntuale del 07/09.

---

## 0. In una riga

Progetto **in produzione stabile**. Bug storici 1/2/3 risolti, sync upstream integrato, feature "Crea utente" live.
**Unica attività in corso:** build + deploy del **frontend `v1.2.2`** (batch i18n `timers` + fix WebSocket) — codice
già committato e pushato, manca solo build immagine → push → swap sul server.

---

## 1. Stato LIVE (produzione, oggi)

| Componente | Immagine / versione | Stato |
|---|---|---|
| **Backend** | `dablio96/self-hosted-cmms-backend:self-hosted-v1.2.1` | ✅ live |
| **Frontend** | `dablio96/self-hosted-cmms-frontend:self-hosted-v1.2.0` | ✅ live · **`v1.2.2` in build/deploy** (vedi §3) |
| **DB** | `postgres:16-alpine` (`atlas_db`) | ✅ dati preservati (bind-mount) |
| **Storage** | `minio/...2025-04-22` (`atlas_minio`) | ✅ (bind-mount) |
| **Ingress interno** | `nginx:1.27-alpine` (`atlas_nginx`) | ✅ solo `80/tcp` interno (porta host `3000` **chiusa**) |
| **Reverse proxy** | `caddy:2` (`/srv/docker/proxy`) | ✅ TLS wildcard, unico ingress |
| **Licensing** | `LICENSING_SELF_HOSTED_MODE=true` | ✅ `SELF_HOSTED` |

**Server:** stack = `/srv/docker/atlas` (compose + `.env`); Caddy = `/srv/docker/proxy`. Dati in **bind-mount**
(`/srv/data/databases/atlas/{postgres,minio}`) → **mai `docker compose down -v`**. Backup in `/srv/data/backups/`,
APK in `/srv/data/applications/caddy/download/atlas-cmms.apk`.

---

## 2. Cose FATTE (verificate sul codice)

> Ogni voce riporta la posizione nel codice reale controllata il 07/09 (non solo la doc).

### 2.1 Baseline self-hosted — `v1.0.0` ✅
Licensing self-hosted centralizzato (`SELF_HOSTED`, 34 entitlement), storage MinIO/GCP, i18n, mobile.
Modifiche storiche MOD-001..020 (baseline documentata in `docs/MOD-*`). Tag git `self-hosted-v1.0.0`.

### 2.2 Bug storici 1/2/3 — `v1.0.2` / `v1.0.3` ✅
- **Bug 1** (`conflict_error` su auto-eliminazione): `logout` ricarica l'utente per id invece di salvare l'entità stale
  → `AuthController.logout` chiama `userService.invalidateSessionsById(...)`.
  *Verificato:* `UserService.invalidateSessionsById` (`UserService.java:501`), chiamata in `AuthController.java:195`.
- **Bug 2** (mail invito che non partiva): risolto lato frontend nostro (`disableSendingEmail=false`).
- **Bug 3** (ricerca Work Order NPE con relazioni super-account LAZY): query dedicata che evita la navigazione LAZY.
  *Verificato:* `SuperAccountRelationRepository.findChildCompanyIdsBySuperUserId` (`:17`), usata in
  `WorkOrderService.java:74,588`. Validato live con seed (`totalElements=6`, nessun NPE).

### 2.3 Sync upstream (`Grashjs/cmms`, +32 commit) — `v1.1.0` ✅
Rate-limiting login, PDF RTL/CJK, **flusso eliminazione account a 2 passi (richiesta + conferma via email)**,
signed-URL caching, webhook, migrazione `Part.version`. 4 conflitti risolti, 129 file auto-mergiati.
*Verificato:* `AuthController.deleteAccountRequest` (`:253`) + `deleteAccountConfirm` (`:258`).

### 2.4 Feature admin "Invita ⇄ Crea utente" — `v1.2.0` ✅
Toggle nel dialog invito; "Crea utente" crea l'account (enabled, password random inutilizzabile) e invia mail di
benvenuto con **link imposta-password** (nessuna password in chiaro nella mail).
*Verificato:* `UserService.createUserByAdmin` (`:425`, classe `@Transactional` `:56`) · endpoint
`POST /auth/set-password` (`AuthController.java:271`) · `VerificationTokenService.confirmSetPassword` (`:74`) ·
frontend `CreateUserByAdminForm.tsx` + pagina `/account/set-password` (`content/pages/Auth/SetPassword/index.tsx`).

### 2.5 Fix mail "imposta password" — `v1.2.1` (solo backend) ✅
`accountCreatedSubject` risolto dal message source corretto (`messages*`, non `mailMessages*`) → niente più
`No message found for pl_PL`; `createUserByAdmin` reso `@Transactional` (niente utenti orfani su errore).

### 2.6 Link download app → APK self-hosted (non app ufficiale) — `v1.2.0` ✅
QR + link puntano all'APK servito da Caddy.
*Verificato:* `Userbox/index.tsx:504,518` e `MobileAppDownloadDialog/index.tsx:18` → `/download/atlas-cmms.apk`.

### 2.7 Batch frontend (committato, deploy in corso) — `v1.2.2` ⏳
- **i18n PL:** `timers` era "Liczniki" (uguale a `meters`) → **"Timery"**.
  *Verificato:* `pl.ts:830` `timers:'Timery'` (e `:374` `meters:'Liczniki'`, ora distinti).
- **WebSocket import/export/notifiche:** su CONNECT fallito (token scaduto) rinfresca il token e riconnette
  (prima falliva in silenzio: "WebSocket connection not initialized").
  *Verificato:* `hooks/useImport.ts:57`, `hooks/useExport.ts`, `Notifications/index.tsx`.
- Codice **committato e pushato** (`377fa3bd`, `973daaa3`). Manca solo la build immagine + deploy → **§3**.

### 2.8 Infrastruttura & operatività ✅
- Porta host `3000` **chiusa** (`atlas_nginx` solo `expose: 80`) → unico accesso via Caddy/TLS.
- **Backup DB** funzionanti e documentati (scoperto e risolto il bug dei backup vuoti da 127 byte):
  guida [dev-docs/backup-and-restore-guide.md](../dev-docs/backup-and-restore-guide.md) (locale).
- **Schema DB** documentato: [database-schema.md](database-schema.md) (dump `pg_dump --schema-only` = fonte di verità,
  query colonne/FK, ERD core, raggruppamento 72 tabelle).

---

## 3. IN CORSO — deploy frontend `v1.2.2`

Stato: codice pronto e pushato; **build + push + swap** da eseguire.

```powershell
# build + push (da repo root, macchina locale)
docker build -t dablio96/self-hosted-cmms-frontend:self-hosted-v1.2.2 ./frontend
docker push dablio96/self-hosted-cmms-frontend:self-hosted-v1.2.2
```
```bash
# server: cambia il tag frontend nel compose -> v1.2.2, poi
cd /srv/docker/atlas
sudo docker compose pull frontend
sudo docker compose up -d frontend
sudo docker compose restart nginx      # l'nginx interno deve ri-risolvere il nuovo container
sudo docker compose ps
```
**Verifica post-deploy:** hard-refresh del sito → categoria mostra "Timery"; import/export senza errore WebSocket.
**Rollback:** rimetti `:self-hosted-v1.2.0` → `up -d frontend` + `restart nginx`.
Ad avvenuto deploy: aggiornare [PROJECT-STATUS.md](PROJECT-STATUS.md) e [CLAUDE.md](CLAUDE.md) (riga v1.2.2 live).

---

## 4. Attività APERTE — da finire (già avviate / a un passo)

| # | Attività | Note | Priorità |
|---|---|---|---|
| A1 | **Deploy `v1.2.2`** (§3) e aggiornare i doc di stato | ultimo passo del batch frontend | **Alta** |
| A2 | **Conferma end-to-end "Crea utente"** | verificare arrivo mail imposta-password + link `/account/set-password` su un caso reale | Media |
| A3 | **Mappa ERD completa (72 tabelle)** | eseguire query FK §1d di [database-schema.md](database-schema.md) (o `grep "FOREIGN KEY"` sul dump) e incollare l'output → genero l'ERD completo | Media |
| A4 | **Decidere sui 2 file untracked** | `docker-compose.prod.yml` (bozza stale, pinna `v1.0.1`, non è ciò che gira) → aggiornare o gitignorare; `images/download-apk.png` (848 B, non referenziata) → cancellare | Bassa |
| A5 | **Tag git versioni `v1.0.1..v1.2.2`** | oggi solo `self-hosted-v1.0.0` è taggato; le altre esistono solo come commit + tag immagine | Bassa |

---

## 5. Attività DA INIZIARE — in programma (piani pronti)

### 5.1 Eliminazione utenti solo agli admin — ⚠️ **premessa da rivedere**
Piano: [restrict-user-deletion-to-admins-plan.md](restrict-user-deletion-to-admins-plan.md).
**Verifica 07/09:** il pericolo principale descritto nel piano — `DELETE /auth` `permitAll` **hard-delete** (owner
cancella l'intera company con un click) — **NON esiste più**: il sync upstream (`v1.1.0`) l'ha sostituito con il
flusso a 2 passi `deleteAccountRequest`/`deleteAccountConfirm` (conferma via email). L'unico `@DeleteMapping` rimasto
in `AuthController` è `/{username}`, gated `ROLE_SUPER_ADMIN`.
**Cosa resta davvero aperto:** `UserService.softDeleteUser` (`:655`) mantiene il ramo di **auto-eliminazione soft**
(`requester.getId().equals(id) || ...PEOPLE_AND_TEAMS`) → un utente può ancora auto-disabilitarsi (soft, reversibile).
**Decisione:** stabilire se blindare anche questo ramo (togliere l'auto-soft-delete) e verificare che
`confirmDeleteAccount` per un owner non elimini comunque l'intera company. **Invasività bassa**; da riscrivere il
piano sulla situazione attuale prima di implementare.

### 5.2 Traduzione PL completa del dialog "scarica app"
`MobileAppDownloadDialog` è ancora in inglese sul sito polacco → tradurre le stringhe. Invasività minima (solo i18n).

### 5.3 Backlog minore (noto, non urgente)
- **`PasswordValidatorTest`**: 6 fallimenti pre-esistenti/**upstream** (feature "common passwords", `loadCommonPasswords()`
  restituisce set vuoto a runtime). Accettati (F20-1); fix futuro se servirà.
- **`messages_it_IT.properties`**: bug apostrofo non raddoppiato rompe `MessageFormat` (solo **italiano**; il polacco è
  integro) → deferred (F19-4).
- **LDAP live** mai esercitato in produzione; **iOS** mai verificato dall'agent; **altre lingue** i18n non auditate.

---

## 6. Stato repo & documentazione (git)

- Branch `self-hosted` **allineato con GitHub** (`origin/Daviz96/cmms`), HEAD `640a510b`.
- Tutto il **codice** è committato e pushato. Committato oggi: `docs/database-schema.md` (`640a510b`).
- **Untracked** residui (non codice): `docker-compose.prod.yml`, `images/download-apk.png` → decisione in **A4**.
- **Escluso da git** (`.gitignore`): `.env*`, `logo`, `*.lic`, `dev-docs/`, `release/`, compose di sviluppo/minio.
- **Drift documentale rilevato** (da sistemare quando si aggiornano i doc di stato):
  - `CLAUDE.md` / `PROJECT-STATUS.md` indicano HEAD `377fa3bd` e frontend batch "in coda" → ora HEAD `640a510b`,
    batch promosso a `v1.2.2` (build in corso).
  - `restrict-user-deletion-to-admins-plan.md` descrive il vecchio `DELETE /auth` hard-delete → **superato** dal sync
    upstream (vedi §5.1).

---

## 7. Riferimenti
- **Stato/changelog vivo:** [PROJECT-STATUS.md](PROJECT-STATUS.md) · **Guida sviluppo:** [CLAUDE.md](CLAUDE.md)
- **Bug storici 1/2/3:** [live-deployment-bugs-handoff.md](live-deployment-bugs-handoff.md)
- **Sync upstream:** [upstream-sync-plan.md](upstream-sync-plan.md) · **Feature crea-utente:** [admin-invite-vs-create-user-plan.md](admin-invite-vs-create-user-plan.md)
- **Schema DB:** [database-schema.md](database-schema.md) · **Backup:** [dev-docs/backup-and-restore-guide.md](../dev-docs/backup-and-restore-guide.md)
- **Deploy runbook (backend):** [dev-docs/deploy-v1.1.0-runbook.md](../dev-docs/deploy-v1.1.0-runbook.md) (riusabile bumpando la versione)
