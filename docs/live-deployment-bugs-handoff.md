# Live Deployment — Bug Tracker & Session Handoff

> Documento di **handoff** per riprendere i lavori alla prossima sessione. Stato al 2026-09-01.
> Deployment self-hosted **live** su `https://cmms.firmabratex.pl` (LAN-only, dietro Caddy con cert
> wildcard). Backend custom in produzione; DB originale preservato.

---

## 1. Stato del deployment (dove siamo)

- **Backend live:** immagine `dablio96/self-hosted-cmms-backend:self-hosted-v1.0.1` (container `atlas-cmms-backend`).
  Avvio OK: `Atlas licensing mode: SELF_HOSTED`, 34 entitlement, `usersCount=MAX`. **DB esistente intatto**
  (upgrade fatto sostituendo solo l'immagine `api`, volumi mantenuti).
- **Frontend live:** immagine **upstream** `intelloop/atlas-cmms-frontend` (NON buildata dal nostro repo → vedi Bug 2).
- **Stack atlas sul server:** `/srv/docker/atlas` (compose + `.env`). Container: `atlas-cmms-backend`,
  `atlas-cmms-frontend`, `atlas_db` (postgres:16-alpine), `atlas_minio`, `atlas_nginx`.
- **Reverse proxy:** Caddy in `/srv/docker/proxy` (container `caddy`, immagine `caddy:2`), rete `bratex_proxy`
  (external). Cert wildcard `/certs/wildcard.crt`. `cmms.firmabratex.pl` → `reverse_proxy atlas_nginx:80`.
- **APK Android (invito):** servita da Caddy → `/srv/data/applications/caddy/download/atlas-cmms.apk`
  (route `handle_path /download/*` → `file_server`). **Download funzionante** (testato).
- **Git:** branch `self-hosted` (fork `Daviz96/cmms`); ultimo commit **`decbc2cd`** (feat mail invito QR/download),
  pushato su `origin`. `main` intatto a `e1d24406`.
- **Immagini Docker Hub (dablio96/self-hosted-cmms-backend):** `self-hosted-v1.0.0` (release baseline),
  `self-hosted-v1.0.1` (= commit `decbc2cd`, feature mail), `latest`.

### Feature mail invito (deployata in v1.0.1)
Aggiunta alla mail di **invito** (`invite.html`) e **benvenuto** (`signup.html`): sezione "Scarica l'app"
(QR + link a `https://cmms.firmabratex.pl/download/atlas-cmms.apk`) + istruzioni "Configura server"
(`https://cmms.firmabratex.pl/api`). Immagini **inline CID** (logo + QR) per non farle bloccare dai client.
File: `EmailService2.java` (addInline condizionale su `cid:logo`/`cid:appQr`), `fragments/main-layout.html`
(fragment `appSection` + logo `cid:logo`), `mailMessages(.properties/_it_IT/_pl_PL)` (nuove chiavi `app*`),
`static/images/download-apk.png` (QR). **Da testare a runtime** una volta risolto il Bug 2 (invito che parte).

---

## 2. Bug aperti (3) — tutti codice UPSTREAM emerso ora

Motivo comune: giriamo il backend **buildato dai sorgenti** all'HEAD `e1d24406` (Hibernate 6.2.19 / Liquibase
5.0.3, più stretti), invece dell'immagine prebuilt originale → alcuni bug latenti upstream ora emergono.

### 🐛 Bug 2 — l'invito non parte alla creazione utente
- **Sintomo:** creo utente e clicco **"zaproś"** → nessuna mail. Se **elimino** l'account creato, l'invito resta
  nel DB → banner con email + **"Wyślij zaproszenia ponownie"** (reinvia) → cliccando, **la mail parte**.
- **Config verificata OK sul server** (`/srv/docker/atlas/.env`): `ENABLE_EMAIL_NOTIFICATIONS=true`,
  `INVITATION_VIA_EMAIL=true`, `MAIL_TYPE=smtp`, `SMTP_*` valorizzati. Il resend che funziona **prova che SMTP va**.
- **Causa (confermata):** il **frontend deployato è l'immagine upstream** `intelloop/atlas-cmms-frontend`
  (NON il nostro `frontend/` del repo). Alla creazione manda `disableSendingEmail: true` (verificato nel payload
  DevTools: `POST /api/users/invite` → `{role:{id:7}, emails:[...], disableSendingEmail:true}`). Il backend
  `UserService.invite()` **riga 379** salta l'invio (`if (!Boolean.TRUE.equals(disableSendingMails)) send(...)`).
  Il resend manda `false` → invia. **Il nostro repo** (`frontend/.../InviteUserDialog.tsx:181`) passa invece
  `false` (corretto) → ma non compiliamo il frontend dai sorgenti, quindi in produzione conta l'upstream.
- **Scoperta (2026-09-01):** `disableSendingEmail:true` è **legittimo** in un caso — l'**auto-registrazione**
  (`frontend/.../RegisterJWT.tsx:103` chiama `inviteUsers(role, [email], true)`): chi si registra da solo non deve
  ricevere una mail d'invito. Perciò **l'Opzione A (backend "invia sempre") è stata SCARTATA**: manderebbe l'invito
  anche in auto-registrazione. Il backend `invite():379` è **corretto**; il difetto è solo l'immagine frontend deployata.
- **✅ FIX SCELTO — Opzione B (frontend dal nostro repo):** il nostro `InviteUserDialog.tsx:181` già passa `false`
  (invia). Basta **buildare+pushare il frontend dai sorgenti** (`docker build ./frontend`) e **sostituire** l'immagine
  upstream `intelloop/atlas-cmms-frontend` nel compose. **Nessuna modifica codice.** Preserva il caso legittimo.
  Bonus: allinea eventuali altre nostre modifiche frontend oggi non live.

### 🐛 Bug 3 — NullPointerException nella ricerca Work Order
- **Origine:** emerso nei log del server (non era il trace del delete). Stack:
  `NullPointerException: ... SharedSessionContractImplementor.getPersistenceContext() ... this.session is null`
  → `PersistentBag.isEmpty()` → **`WorkOrderService.getSearchCriteria` riga 575** → `WorkOrderController.search`.
- **Causa:** riga 575 `if (!user.getSuperAccountRelations().isEmpty())` accede a una **collezione LAZY**
  (`user.getSuperAccountRelations()`) quando lo `user` (`@CurrentUser`) è **detached** (fuori sessione Hibernate)
  → lazy init fallisce. Colpisce la **ricerca Work Order** per utenti `ROLE_CLIENT`.
- **Impatto:** DB live **quasi vuoto** → l'utente non ha ancora testato la ricerca WO con dati reali (pochi test
  pratici finora). Va validato dopo il seed dei dati di test.
- **✅ FIXATO nel codice (2026-09-01):** sostituito il check lazy con una **query JPQL session-safe**.
  - `SuperAccountRelationRepository.findChildCompanyIdsBySuperUserId(superUserId)` →
    `select distinct r.childUser.company.id from SuperAccountRelation r where r.superUser.id = :superUserId`.
  - `WorkOrderService`: iniettato `SuperAccountRelationRepository`; `getSearchCriteria` ora chiama la query
    (`user.getId()`, sempre disponibile su entità detached) invece di `user.getSuperAccountRelations()`. Comportamento
    identico. File: `WorkOrderService.java:573-589`, `SuperAccountRelationRepository.java`. **Da validare a runtime.**

### 🐛 Bug 1 — `conflict_error` eliminando il proprio account
- **Sintomo:** dal menu profilo, "elimina account" (auto-eliminazione) → la pagina si ricarica → errore
  **`conflict_error`** → devo ricaricare per tornare alla login.
- **✅ CAUSA CONFERMATA dal trace (2026-09-01, v1.0.2):** NON è `softDeleteUser` a fallire (quello va a buon fine
  e l'account viene eliminato), ma il **`logout` che il frontend chiama subito dopo**. Trace:
  ```
  org.hibernate.StaleObjectStateException: Row was updated or deleted ... [com.grash.model.User#352]
    → UserService.invalidateSessions(UserService.java:415)   // userRepository.save(user)
    → AuthController.logout(AuthController.java:168)
  ```
  Sequenza (pulsante profilo → `UserProfile/index.tsx` → `api.deletes('auth')`): (1) **`DELETE /auth`** →
  `AuthController.deleteAccount` (`@PreAuthorize permitAll`) → **HARD delete** (`userRepository.delete(user)`; se
  owner → `companyService.delete()` cancella l'intera company) → **OK**. (2) `POST /auth/logout` →
  `invalidateSessions(@CurrentUser user)` → `save(user)` su una riga **appena eliminata** → UPDATE **0 righe** →
  `StaleObjectStateException` (User **non ha `@Version`** → caso "unsaved-value/riga inesistente") → **409 →
  `conflict_error`**. NB: `/users/soft-delete/{id}` (soft) è invece il percorso admin-elimina-altri, non questo.
- **✅ FIX APPLICATA (v1.0.3):** il `logout` non salva più l'entità stale.
  - Nuovo `UserService.invalidateSessionsById(Long userId)`: ricarica l'utente **fresco per id**
    (`findById(...).ifPresent(...)`), setta `sessionInvalidatedAt`, salva, evict cache, revoca refresh token;
    **no-op se l'utente non esiste più** (logout riesce comunque). NON tocca `invalidateSessions(User)` (usato da
    `softDeleteUser`/`updatePassword`, che devono salvare le loro mutazioni in-memory).
  - `AuthController.logout` ora chiama `invalidateSessionsById(user.getId())`.
  - **Rimosso** il log temporaneo `printStackTrace()` da `handleOptimisticLocking` (causa confermata).
  - File: `UserService.java` (~419), `AuthController.java:167-170`, `GlobalExceptionHandlerController.java`.

---

## 3. Piano — `v1.0.2` (due immagini: backend + frontend) — DECISO 2026-09-01

Codice **già modificato** (✅ Bug 3 + ✅ log Bug 1). Bug 2 = build frontend (nessun codice).

**Build (Docker locale; Java/Maven NON installati → compila nel container):**
1. **Backend** (bug3 + log bug1): `docker build ./api -t dablio96/self-hosted-cmms-backend:self-hosted-v1.0.2`
   (Dockerfile fa `mvn package -DskipTests`; la build valida la compilazione).
2. **Frontend** (bug2, Opzione B): `docker build ./frontend -t dablio96/self-hosted-cmms-frontend:self-hosted-v1.0.2`
   (multi-stage node:22 → nginx; env a runtime via `runtime-env-cra` → drop-in compatibile col compose).
3. Tag anche `:latest` su entrambe.

**Push** (utente fa `docker login`, push bloccato per l'assistente): entrambe le immagini su `dablio96/…`.

**Deploy sul server** (`/srv/docker/atlas`): nel compose sostituire `intelloop/atlas-cmms-frontend` →
`dablio96/self-hosted-cmms-frontend:self-hosted-v1.0.2` e `api` → `:self-hosted-v1.0.2` →
`docker compose pull api frontend` → `docker compose up -d api frontend` → **`docker compose restart nginx`**
(obbligatorio, vedi §5). Aggiornare commit git (`self-hosted`) + tag `self-hosted-v1.0.2` (solo se richiesto).

**Poi:** seed dati di test via API sul **live LAN** (§4) → validare ricerca WO (Bug 3) e testare le funzioni →
**riprodurre il Bug 1** e raccogliere il trace ora visibile → fix `v1.0.3`.

---

## 4. Decisioni prese (2026-09-01) + seed dati di test

1. ✅ **Bug 3:** DB live quasi vuoto → non ancora testato con dati reali. Fix applicata comunque (a basso rischio).
2. ✅ **Scope `v1.0.2` confermato:** Bug 2 (Opzione B, frontend) + Bug 3 (fix) + log Bug 1.
3. ✅ **Bug 2:** scelta **Opzione B** (build frontend dal repo). Opzione A scartata (romperebbe l'auto-registrazione).
4. ✅ **Ambiente di test:** **seed sul live LAN** (DB quasi vuoto) via **API REST** (login admin → JWT → POST
   `locations → assets → parts → work orders (vari stati) → utenti technician/requester/client`). Passa dalla
   validazione reale, esercita i code path (incl. `getSearchCriteria` → Bug 3), ripetibile. Da scrivere lo script.
5. ⏳ **Rete Caddy (vedi §5):** ancora da rendere permanente (dichiararla nel compose di Caddy) — al momento
   ripristinata **a mano** con `docker network connect atlas-cmms_default caddy` → si riscollega ad ogni update.

---

## 5. Note operative apprese (gotcha da ricordare)

- **502 dopo swap/ricreazione container:** ricreando un container (`up -d`, swap immagine) l'`api` prende un
  **nuovo IP**; `atlas_nginx` ha in cache il vecchio → 502 su `/api`. **Sempre `docker compose restart nginx`
  per ultimo** dopo qualsiasi ricreazione.
- **502 dal browser = Caddy fuori dalla rete atlas:** la connessione Caddy↔`atlas-cmms_default` era **manuale**
  (non dichiarata nel compose di Caddy) → persa quando Compose riconcilia lo stack. Ripristino temporaneo:
  `sudo docker network connect atlas-cmms_default caddy`. **Fix definitivo:** dichiararla nel compose di Caddy
  (`/srv/docker/proxy/compose.yml`, rete esterna `atlas-cmms_default`) **oppure** aggiungere `bratex_proxy` al
  servizio `nginx` dello stack atlas.
- **Liquibase WARN all'avvio:** `Illegal character in path ... /db/changelog/ 2023_07_14_..._add_simplifiedWorkOrder.xml`
  — il file è **letteralmente nominato con uno spazio iniziale** (upstream), Liquibase 5.0.3 avvisa ma
  **"Defaulting to previous behavior"** = recupera → **NON fatale**. **NON rinominare** il file (cambierebbe
  l'identità del changeset → re-run sul DB live). Lasciare così. (Eventuale pulizia futura via `logicalFilePath`.)
- **Mail:** servono **entrambe** `ENABLE_EMAIL_NOTIFICATIONS=true` **e** `INVITATION_VIA_EMAIL=true`
  (`mail.enable` + `security.invitation-via-email`). SMTP configurato non basta.

---

## 6. Riferimenti

- **Runbook upgrade/deploy backend:** `dev-docs/upgrade-to-self-hosted.md` (locale, non pushato).
- **Seed dati di test (via API):** `dev-docs/seed_test_data.py` (Python 3 stdlib, nessuna dipendenza). Crea
  locations→assets→parts→work orders (priorità/stati vari, prefisso `[SEED]`) + una search finale che esercita
  `getSearchCriteria` (Bug 3). Env: `BASE_URL` (incl. `/api`), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `INSECURE=1` se
  cert TLS non fidato, `DRY_RUN=1` per anteprima. **Eseguire DOPO il deploy di `v1.0.2`** (la search finale
  richiede la fix Bug 3; su v1.0.1 andrebbe in NPE).
- **Piano mail invito+QR:** `docs/invite-email-apk-qr-plan.md`.
- **Compose prod (pull-based):** `docker-compose.prod.yml` (locale; immagine → `self-hosted-v1.0.1`).
- **Build immagine backend:** `docker build ./api -t atlas-cmms-backend:local` (multi-stage, `-DskipTests`).
- **Server:** atlas = `/srv/docker/atlas` ; Caddy = `/srv/docker/proxy` ; APK = `/srv/data/applications/caddy/download/`.
- **File coinvolti (repo):**
  - Bug 2 → `api/src/main/java/com/grash/service/UserService.java` (`invite()` ~369-386, gate riga 373 e 379)
  - Bug 3 → `api/src/main/java/com/grash/service/WorkOrderService.java:573-589`
  - Bug 1 → `api/src/main/java/com/grash/service/UserService.java` (`softDeleteUser` ~560) +
    `api/src/main/java/com/grash/exception/GlobalExceptionHandlerController.java:90-96` (handler 409, no trace)
  - Mail feature → `api/src/main/resources/templates/{invite,signup}.html`,
    `templates/fragments/main-layout.html`, `mailMessages*.properties`,
    `static/images/download-apk.png`, `service/EmailService2.java`

---

**Stato aggiornato (2026-09-01, sera):**
- `v1.0.2` **deployata** sul live (backend + frontend nostro). Bug 2 e Bug 3 attesi risolti. Rete Caddy resa
  **permanente** dall'utente (compose Caddy + Caddyfile aggiornati) → non si scollega più agli update.
- Bug 1 **diagnosticato** via il trace (log temporaneo) → **causa = logout dopo soft-delete** → **fix applicata** (v1.0.3).

**Prossimo step operativo:** `v1.0.3` = **solo rebuild backend** (fix Bug 1 + rimozione log):
(a) `docker build ./api` → tag `self-hosted-v1.0.3` (+`latest`) → **push** (utente `docker login`);
(b) server: swap immagine `api` → `:self-hosted-v1.0.3` → `pull api` → `up -d api` → **`restart nginx`**;
(c) verificare: eliminare un account di prova → **niente più `conflict_error`**;
(d) seed dati (`dev-docs/seed_test_data.py`) e test funzionale (ricerca WO / Bug 3, invito / Bug 2).
