# MOD-013 — Go-Live Readiness

Ultimo controllo tecnico **prima** del deployment production dell'Atlas self-hosted
modificato. Attività di **sola verifica/documentazione**: nessuna modifica a codice,
licensing, frontend, mobile, Docker, nginx, `websrv01`, Caddy, DNS, certificati o
database/MinIO production. **Nessun deployment eseguito.** Secret **non** stampati
(solo nome/posizione della variabile).

```text
Code changes: NONE
```

> **Esito in una riga:** il *prodotto* self-hosted è tecnicamente pronto (backup,
> rollback, persistenza, sicurezza, immagine buildata dai sorgenti — tutti verificati o
> supportati dal repository). Restano **attività operative di provisioning già definite**
> (scelta dominio + DNS + TLS reverse-proxy, rotazione dei secret di produzione, portare i
> sorgenti modificati sul build host) → **GO-LIVE STATUS: READY WITH CONDITIONS**.

---

## 1. Objective

Stabilire se l'Atlas self-hosted modificato può andare in produzione, distinguendo
nettamente **ciò che è già verificato**, **ciò che resta da verificare/provisionare** e
**ciò che richiede una decisione**. Produrre un Go-Live Readiness Report con procedura di
backup, rollback e deployment ricostruite **solo** da ciò che il repository supporta
realmente. Non eseguire il deployment.

## 2. Current Version

| Elemento | Valore | Fonte |
|---|---|---|
| Branch / HEAD | `main` @ **`e1d24406`** | `git log -1` |
| Stato working tree | **modifiche MOD non committate**: 12 file tracciati modificati (`AssetService.java`, `LicenseService.java`, `MinioService.java`, `StorageService.java`, `FileService.java`, `FileMapper.java`, `GCPService.java`, `LdapSecurityConfig.java`, `application.yml`, `docker-compose.yml`, `AssetServiceTest.java`, `.env.example`) + test untracked + `docs/` untracked | `git status --short` |
| Backend image | `atlas-cmms-backend:local` — **build da `./api`** (MOD-006) | `docker-compose.yml:21-23` |
| Frontend image | `intelloop/atlas-cmms-frontend` (upstream, non modificato) | `docker-compose.yml:104` |
| PostgreSQL | `postgres:16-alpine` | `docker-compose.yml:4` |
| MinIO | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | `docker-compose.yml:130` |
| nginx | `nginx:1.27-alpine` (`nginx.conf` del repo, single-ingress) | `docker-compose.yml:143` |
| Test baseline | `mvnw test` **1446/1446** (MOD-011) | doc 29 |

**Nota di provenienza (condizione go-live):** la produzione builda il backend dai sorgenti
(`build: ./api`). Le modifiche MOD sono attualmente **solo nel working tree locale, non
committate/pushate**. Un `git pull` sul server **non** le includerebbe: i sorgenti modificati
devono essere resi disponibili sul build host (commit+push del branch, oppure trasferimento
del working tree). Vedi §17/§18.

## 3. Audit Summary

Sintesi dello stato consolidato (MOD-001 → MOD-012). Nessun blocker di prodotto aperto.

| Area | Stato | Blocker? | Evidence |
|---|---|---|---|
| Backend (Spring Boot, source-built) | Verificato live | No | MOD-005/010 (doc 23/28) |
| PostgreSQL 16 | Verificato (139 tabelle, Liquibase) | No | doc 28 §5/§14 |
| MinIO | Verificato (upload/download/delete) | No | doc 28 §12, doc 23 |
| Licensing self-hosted | Verificato `SELF_HOSTED`/valid/34 entitlement | No | doc 28 §6 |
| Authentication | Verificato (signup/login/logout/re-login) | No | doc 28 §7 |
| Authorization | Verificato (401 no-token, permessi ruolo) | No | doc 28 §8 |
| Multi-tenancy | Verificato (`@PostLoad` 403 cross-company) | No | doc 28 §9, doc 23 |
| Assets | Verificato (CRUD) | No | doc 28 §10 |
| Work Orders | Verificato (CRUD) | No | doc 28 §11 |
| Attachments (MOD-004B) | Verificato (disposition per tipo + lifecycle) | No | doc 28 §12, doc 21 |
| Frontend web | Servito + audit CLEAN | No | doc 26, doc 28 §16 |
| Mobile contract | Verificato code+protocol | No | doc 27, doc 30 |
| Mobile GUI | Parziale (owner: iOS connette; agent GUI deferita a MOD-014) | No (deferito) | doc 30, MOD-014 preambolo |
| F-01 | **RESOLVED** (fix + regression, runtime) | No | doc 29 |
| F-04 | Pre-esistente, P3, mobile impact NONE, out-of-scope | No | doc 29 §11, doc 30 §16 |
| Backup | Supportato (script + `pg_dump` validato) | No | §6 |
| Restore/Rollback | Supportato (script con `atlas_old` safety copy) | No | §7 |
| Deployment procedure | Ricostruibile dal repo (compose + TLS dev-doc) | No | §15 |
| Dominio / DNS / TLS | **Non definito nel repo** | **Condizione** | §9 |
| Secret di produzione | Attualmente valori esempio/test | **Condizione** | §11/§12 |
| Provenienza sorgenti (commit) | Modifiche MOD non committate | **Condizione** | §2/§17 |

## 4. Mobile Device Test

Nessun dispositivo Android/iOS pilotabile **dall'agent** in questo ambiente (Windows
headless: no adb/SDK/emulatore/Expo; iOS richiede macOS + Xcode). Quindi il test GUI
strutturato via agent:

```text
Android GUI: NOT TESTED (ambiente agent privo di SDK/emulatore/device)
iOS GUI (agent): NOT TESTED (richiede macOS + Xcode)
```

**Aggiornamento dal responsabile (preambolo MOD-014):** il responsabile del progetto ha
**personalmente verificato**: (a) Atlas web desktop con le funzionalità prima bloccate
dalla licenza; (b) la **connessione dell'app iOS reale al backend self-hosted**; (c) la
presenza di **alcuni bug nell'app mobile**. Quindi la connettività mobile↔backend è
confermata su device reale dal responsabile; un **pass GUI ripetibile e automatizzato**
è l'oggetto di **MOD-014** (predisposizione ambiente) e l'eventuale fix dei bug di
**MOD-015**. Questo **non è un blocker go-live per il prodotto backend + web**, ma è un
motivo per non annunciare "mobile production-ready" finché i bug non sono trattati.

## 5. F-04 Status

`F-04 = OPEN (pre-esistente, P3, out-of-scope)` — **non modificato in questo MOD**. Una
PATCH parziale che omette un campo `@NotNull` (es. `name`) → 500
`ConstraintViolationException` (mapper MapStruct `SET_NULL`, `AssetService.update`).
**Mobile impact = NONE OBSERVED** (l'app invia il DTO completo, doc 30). Non si propone una
modifica globale a MapStruct solo per eliminare F-04 (§7 del prompt). Resta issue non
urgente, decisione separata.

## 6. Backup Readiness

**PASS.** Il repository fornisce backup **completo e documentato**:

- **Script:** `scripts/backup/atlas-backup.sh` (Linux) e `atlas-backup.ps1` (Windows);
  guida `dev-docs/Backup.md`.
- **PostgreSQL:** `docker exec atlas_db pg_dump -U <POSTGRES_USER> --encoding=UTF8 atlas`
  → `atlas_db.sql`. Il `pg_dump` è già stato **validato a runtime** in MOD-010 (dump 544 KB,
  139 `CREATE TABLE`, header valido).
- **MinIO:** container `mc` temporaneo sulla rete `atlas-cmms_default` →
  `mc mirror atlas-minio/atlas-bucket /backup_data` (bucket `atlas-bucket`, objects +
  struttura). I metadati applicativi degli allegati vivono in PostgreSQL (coperti dal dump).
- **Archivio:** `tar.gz` (Linux) / `.zip` (Windows) in `./atlas_backups/` con timestamp.
- **Configurazione da salvare a parte (non nello script):** `docker-compose.yml`, `.env`
  (**contiene secret → cifrare/escludere dal VCS**), `nginx.conf`, `config/` e `logo/`
  (montati come volumi bind sul backend, `docker-compose.yml:28-29`), ed eventuali
  `Caddyfile`/certificati del reverse proxy esterno (fuori dal repo Atlas). **Non stampare
  i secret.**

Requisito operativo: eseguire un backup **immediatamente prima** del deployment (§15).

## 7. Rollback Readiness

**PASS (con una raccomandazione).** Rollback dei **dati** già supportato dallo script:

```text
restore <backup>:
  atlas  → RENAME → atlas_old      (copia di sicurezza automatica)
  atlas  → CREATE (vuoto) → psql restore da atlas_db.sql
  MinIO  → mc mirror /backup_data → atlas-bucket (--overwrite)
  (prompt di conferma prima di sovrascrivere DB e file)
```

→ `dev-docs/Backup.md` §Important Notes: *"the script renames your current database to
`atlas_old` before creating a new `atlas` … allows for recovery if the restore fails"*.
La guida richiede di **fermare il container backend prima del restore** ("Ensure the backend
container is stopped").

Rollback **applicativo** (versione codice): oggi l'immagine backend ha tag fisso
`atlas-cmms-backend:local`, **sovrascritto ad ogni rebuild** → non esiste un'immagine
precedente a cui tornare automaticamente. **Raccomandazione (condizione, non blocker):**
prima del deploy, taggare l'immagine buildata con una versione/data
(`docker tag atlas-cmms-backend:local atlas-cmms-backend:<versione>`) e/o committare i
sorgenti, così il rollback applicativo = ridispiegare l'immagine/commit precedente. Vedi §18.

Procedura di rollback concreta (solo comandi supportati dal repo):

```text
FAIL post-deploy
  ↓ docker compose stop api
  ↓ ./atlas-backup.sh restore ./atlas_backups/<backup-pre-deploy>   (DB + MinIO)
  ↓ (se regressione codice) redeploy immagine/commit precedente + docker compose up -d
  ↓ smoke test (§13)
```

## 8. Production Architecture

Architettura single-ingress già definita nel repo (`nginx.conf`, `docker-compose.yml`):

```text
Internet
  ↓ (TLS)   [reverse proxy esterno: Caddy/Traefik/NPM/Cloudflare — NON nel repo, §9]
  ↓ HTTP
nginx (atlas_nginx, :80 → host :3000)   unico ingresso pubblico
  ├── /         → frontend:3000   (React SPA)
  ├── /api/     → api:8080        (Spring Boot)
  └── /storage/ → minio:9000      (allegati / presigned)
postgres (atlas_db, :5432)  ─ solo `expose`, non pubblicato
minio    (atlas_minio, :9000/:9001) ─ solo `expose`, non pubblicato
```

- Solo **nginx** pubblica una porta host (`3000:80`). PostgreSQL, MinIO, API e frontend
  usano `expose` (raggiungibili solo nella rete Docker interna) → **non esposti al host/
  Internet**. Coerente con §14 sicurezza.
- `SINGLE_INGRESS` default `true`; `PUBLIC_MINIO_ENDPOINT = <PUBLIC_SERVER_URL>/storage`
  → i presigned URL passano dal nginx.

## 9. Domain / Reverse Proxy

**INCOMPLETE — OPEN DECISION.** Il repository documenta il **meccanismo** TLS ma **non** il
dominio production concreto.

- `dev-docs/Set up TLS.md` fornisce opzioni pronte: **Caddy** (auto-HTTPS Let's Encrypt,
  `reverse_proxy localhost:3000`), **Traefik** (label sul servizio nginx), **NGINX Proxy
  Manager**, **Cloudflare Tunnel**, **Certbot** (blocco `443` commentato in `nginx.conf`).
- `nginx.conf` §HTTPS raccomanda esplicitamente di mettere il container dietro
  Caddy/Traefik/Cloudflare/NPM per il TLS (placeholder `cmms.example.com`).

**Non documentato nel repository (UNKNOWN / TO VERIFY):**

| Elemento | Stato |
|---|---|
| Dominio Atlas production | **UNKNOWN** — non presente nel repo (nessun `firmabratex`/dominio Atlas nei file di config; compare solo nel prompt MOD-013) |
| Reverse proxy production scelto (Caddy/Traefik/NPM/Cloudflare) | **UNKNOWN** — nessun `Caddyfile` nel repo |
| Record DNS | **UNKNOWN** — fuori dal repo |
| Certificato TLS | **UNKNOWN** — nessun cert nel repo (blocco `443` commentato) |
| Porta interna | `3000` (host) → `80` (nginx) — **NOTO** (`docker-compose.yml:154`) |
| Rete Docker | `atlas-cmms_default` (project `atlas-cmms`) — **NOTO** |

**Distinzione richiesta (`wiki.firmabratex.pl` vs Atlas):** il dominio `wiki.firmabratex.pl`
citato nel prompt **non esiste in alcun file del repository** (né Caddyfile né altro). Non
posso quindi documentarne la relazione con Atlas senza inventare: è una configurazione del
server `websrv01` **esterna a questo repo → UNKNOWN / TO VERIFY**. Il dominio Atlas e la sua
separazione dall'eventuale wiki restano una **OPEN DECISION** da risolvere sul server prima
del go-live. Quando il dominio è scelto: impostare `PUBLIC_SERVER_URL=https://<dominio>` e
`HOME_URL` coerente, e (se dietro proxy) `TRUSTED_PROXY_IPS`.

## 10. Storage

**PASS per la persistenza; percorsi host UNKNOWN.**

- Il compose usa **volumi Docker con nome** (`postgres_data:/var/lib/postgresql/data`,
  `minio_data:/data`) → **storage persistente**, non effimero (persistenza verificata in
  MOD-010 §14 su `restart` e `down`/`up` senza `-v`).
- I percorsi citati nel prompt `/srv/data/databases/atlas/postgres` e
  `/srv/data/databases/atlas/minio` **non compaiono nel repository** (solo nel prompt) →
  **UNKNOWN / TO VERIFY**. Se la produzione vuole bind-mount su quei path (invece dei volumi
  Docker con nome), è una scelta di configurazione del server da confermare/applicare sul
  compose production; **non modificata qui**. In entrambi i casi lo storage è persistente:
  requisito soddisfatto.

## 11. Environment Variables

Checklist derivata da `docker-compose.yml` + `.env.example` (solo **nomi**, nessun valore).
`REQUIRED` = necessaria per un self-hosted+MinIO funzionante; `OPTIONAL` = ha default/uso
non-core; `NOT REQUIRED` = non serve in self-hosted+MinIO.

```text
# --- Core (REQUIRED) ---
POSTGRES_USER               REQUIRED   (nessun default nel compose)
POSTGRES_PWD                REQUIRED   (secret → ruotare dal valore d'esempio)
JWT_SECRET_KEY              REQUIRED   (secret → GENERARE nuovo per produzione)
MINIO_USER                  REQUIRED   (root MinIO + access key backend)
MINIO_PASSWORD              REQUIRED   (secret → ruotare)
PUBLIC_SERVER_URL           REQUIRED   (https://<dominio>; guida API/FRONT/MINIO + presigned)
LICENSING_SELF_HOSTED_MODE  REQUIRED   (=true; altrimenti COMMERCIAL/Keygen)
STORAGE_TYPE                OPTIONAL   (default minio; =MINIO)

# --- Reverse proxy / rete ---
SINGLE_INGRESS              OPTIONAL   (default true; coerente con nginx unico ingresso)
TRUSTED_PROXY_IPS           OPTIONAL   (RACCOMANDATO se dietro Caddy/Traefik/CF per X-Forwarded)
ENABLE_CORS                 OPTIONAL   (default false; single-ingress non richiede CORS)

# --- Email (OPTIONAL, off di default) ---
ENABLE_EMAIL_NOTIFICATIONS  OPTIONAL   (default false)
INVITATION_VIA_EMAIL        OPTIONAL   (default false)
MAIL_TYPE / SMTP_* / MAIL_RECIPIENTS / SENDGRID_*   OPTIONAL (solo se email attive; CFG-01: MAIL_RECIPIENTS default vuoto)

# --- SSO / OAuth / LDAP (OPTIONAL, off) ---
ENABLE_SSO / OAUTH2_*       OPTIONAL   (default false/vuoto)
LDAP_* (LDAP_ENABLED, URL, BASE_DN, ORG_ADMIN, MANAGER_DN, MANAGER_PASSWORD, …)
                            OPTIONAL   (LDAP_ENABLED=false; MANAGER_PASSWORD è secret)

# --- Branding / frontend (OPTIONAL) ---
HOME_URL                    OPTIONAL   (default https://atlas-cmms.com → impostare proprio)
LOGO_PATHS / CUSTOM_COLORS / BRAND_CONFIG / DEMO_LINK / GOOGLE_KEY / GOOGLE_TRACKING_ID / RECAPTCHA_*
                            OPTIONAL
CLOUD_VERSION               OPTIONAL   (default false; self-hosted)

# --- Commerciale / cloud storage (NOT REQUIRED in self-hosted+MinIO) ---
LICENSE_KEY / LICENSE_FINGERPRINT_REQUIRED / LICENSE_FILE_PATH   NOT REQUIRED (self-hosted bypassa Keygen)
KEYGEN_PRODUCT_TOKEN / PADDLE_*                                  NOT REQUIRED (nessun Paddle/Keygen)
GCP_BUCKET_NAME / GCP_JSON / GCP_PROJECT_ID                      NOT REQUIRED (STORAGE_TYPE=minio)

# --- Da verificare ---
SPRING_PROFILES_ACTIVE      UNKNOWN/OPTIONAL (compose lo passa senza default → warning se vuoto;
                                             l'app usa il profilo di default se non impostato; verificare il profilo inteso)
```

Nessuna variabile **core** risulta non documentata → nessun `OPEN DECISION` di variabili
mancanti, **tranne** la conferma di `SPRING_PROFILES_ACTIVE` (profilo prod inteso) e il fatto
operativo che i **secret core sono ancora valori d'esempio** (§12).

## 12. Security

Verifica concettuale (nessuna security config modificata in questo MOD):

| Controllo | Stato | Nota |
|---|---|---|
| Secret non nel repository (tracciati) | PASS | `.env` è gitignored; `.env.example` contiene solo placeholder/esempi. `LDAP_MANAGER_PASSWORD`/SMTP/MinIO/JWT mai committati con valori reali |
| Secret di produzione impostati | **CONDIZIONE** | `.env` di deploy usa ancora `POSTGRES_PWD`/`MINIO_PASSWORD`/`JWT_SECRET_KEY` d'esempio → **ruotare** prima del go-live |
| TLS previsto | PASS (meccanismo) | via reverse proxy esterno (§9); da provisionare |
| Backend non esposto | PASS | `api` solo `expose:8080` |
| PostgreSQL non pubblico | PASS | `expose:5432`, nessun `ports` |
| MinIO non pubblico | PASS | `expose` 9000/9001, accesso solo via `/storage` nginx |
| Reverse proxy unico ingresso | PASS | solo nginx pubblica `3000:80` |
| Authorization attiva | PASS | 401 no-token, permessi ruolo (doc 28 §8) |
| Company isolation attiva | PASS | `@PostLoad` 403 cross-company (doc 28 §9, doc 23) |

Unico intervento di sicurezza necessario per il go-live: **rotazione dei secret** e
provisioning TLS — entrambe attività operative note.

## 13. Health Checks

Smoke test post-deployment (comandi da eseguire **sul server** — qui solo **documentati**,
NON eseguiti; `<dominio>` = dominio production da definire, §9):

```text
HTTPS endpoint      curl -fsS https://<dominio>/ -o /dev/null -w '%{http_code}\n'      → 200
API reachable       curl -fsS https://<dominio>/api/license/state                      → {"valid":true,"planName":"Self-Hosted",…}
licensing mode      docker compose logs api | grep -i "licensing mode"                 → SELF_HOSTED
services up         docker compose ps                                                  → tutti "running"/"Up"
login               curl -fsS -X POST https://<dominio>/api/auth/signin -H 'Content-Type: application/json' -d '{…,"type":"client"}'  → 200 + accessToken
Asset               curl -fsS https://<dominio>/api/assets/search  (Authorization: Bearer …)  → 200
Work Order          curl -fsS https://<dominio>/api/work-orders/search (…)             → 200
Attachment          upload /api/files/upload + download presigned                      → 200 (immagine inline / altro attachment)
```

Sequenza logica: `HTTPS 200 → API → login → Asset → Work Order → attachment`. Ogni flusso è
già passato in locale (MOD-010) sullo stesso stack source-built.

## 14. Data Migration

**Da determinare sul server.** Il repository non contiene dati production. Due casi:

- Ambiente production **vuoto** (nuova installazione) → `DATA MIGRATION = NONE`. I dati di
  test locali (volumi throwaway `atlas-cmms_postgres_data`/`atlas-cmms_minio_data` di
  MOD-010/deploy locale) **non** vanno migrati.
- Ambiente production con **dati esistenti** → identificare e migrare: PostgreSQL (`pg_dump`/
  restore), MinIO (`mc mirror`), `.env`/secret, utenti, company. Usare **esclusivamente** lo
  script di backup/restore (§6/§7).

Nessuna migrazione eseguita in questo MOD.

## 15. Deployment Procedure

Procedura ricostruita **solo** da ciò che il repo supporta (compose + backup script + TLS
dev-doc). **NON eseguita** — documentata per l'esecuzione sul server dopo approvazione.

```text
 1. PRE-FLIGHT
    docker --version ; docker compose version
    df -h                       # spazio disco sufficiente per DB+MinIO+immagini
    docker compose config       # compose valido (api build-from-source, no upstream backend image)

 2. SORGENTI MODIFICATI SUL BUILD HOST (condizione §2/§17)
    Rendere disponibili le modifiche MOD (commit+push del branch e git pull sul server,
    oppure trasferimento del working tree). Verificare la provenienza dell'immagine
    (buildSelfHostedLicensingState / responseHeaderOverrides / mail.recipients:${MAIL_RECIPIENTS:}).

 3. BACKUP PRE-DEPLOY (se esistono dati)
    ./atlas-backup.sh backup        # → ./atlas_backups/atlas_backup_<ts>.tar.gz  (DB + MinIO)
    (salvare a parte anche docker-compose.yml, .env cifrato, nginx.conf, config/, logo/, Caddyfile/certs)

 4. CONFIG (.env di produzione — NON committare)
    POSTGRES_PWD / MINIO_PASSWORD / JWT_SECRET_KEY   → valori NUOVI (rotazione)
    PUBLIC_SERVER_URL=https://<dominio> ; HOME_URL coerente
    LICENSING_SELF_HOSTED_MODE=true
    STORAGE_TYPE=minio ; TRUSTED_PROXY_IPS=<ip proxy> (se dietro reverse proxy)

 5. REVERSE PROXY / TLS  (dev-docs/Set up TLS.md)
    Mettere Caddy/Traefik/NPM/Cloudflare davanti a nginx (host:3000) per il TLS del <dominio>.
    nginx resta su HTTP interno (blocco 443 commentato lasciato tale, salvo Certbot in-container).

 6. BUILD & START
    docker tag atlas-cmms-backend:local atlas-cmms-backend:<versione>   # per rollback (§7)
    docker compose build
    docker compose up -d

 7. VERIFICA RETE / ENDPOINT / SMOKE TEST
    docker compose ps                       # tutti up
    Health checks §13 (HTTPS 200 → license/state → login → Asset → WO → attachment)

 8. BACKUP POST-DEPLOY
    ./atlas-backup.sh backup                # snapshot dello stato appena rilasciato

 9. ROLLBACK (solo se FAIL) — §7
    docker compose stop api
    ./atlas-backup.sh restore ./atlas_backups/<backup-pre-deploy>
    redeploy immagine/commit precedente ; docker compose up -d ; ri-smoke test
```

Ordine servizi (dipendenze del compose): `postgres`/`minio` → `api` → `frontend` → `nginx`.

## 16. Final Checklist

```text
[x] Backup PostgreSQL              (script + pg_dump validato — MOD-010)
[x] Backup MinIO                   (mc mirror atlas-bucket)
[x] Backup configuration           (compose/.env/nginx.conf/config/logo — procedura §6)
[x] Rollback procedure             (restore con atlas_old safety copy — §7)
[x] Production compose verified    (build-from-source, single-ingress, volumi persistenti)
[~] Environment variables verified (checklist §11 completa; secret core da ruotare; SPRING_PROFILES_ACTIVE da confermare)
[ ] Domain verified                (OPEN — non definito nel repo, §9)
[ ] DNS verified                   (OPEN — fuori dal repo)
[ ] TLS verified                   (meccanismo documentato; certificato da provisionare)
[ ] Caddy configuration ready      (OPEN — nessun Caddyfile nel repo)
[x] Storage paths verified         (volumi Docker persistenti; bind /srv/data OPZIONALE/UNKNOWN)
[x] Docker networks verified       (atlas-cmms_default)
[x] Backend image/version          (atlas-cmms-backend:local, source-built; taggare versione per rollback)
[x] Frontend image/version         (intelloop/atlas-cmms-frontend upstream)
[x] Database version               (postgres:16-alpine)
[x] MinIO version                  (RELEASE.2025-04-22T22-12-26Z)
[x] Smoke tests defined            (§13)
[~] Mobile GUI verified OR deferred (DEFERRED a MOD-014/015; owner ha verificato iOS connect)
[ ] Production secrets rotated      (CONDIZIONE §12)
[ ] MOD sources committed/on host   (CONDIZIONE §2)
```

## 17. Blockers

**Nessun blocker tecnico di prodotto (P0/P1 = 0).** Elementi che devono essere risolti
**come parte del deployment** (condizioni operative, non difetti):

| ID | Tipo | Descrizione |
|---|---|---|
| GL-1 | Condizione (config) | **Secret di produzione** ancora ai valori d'esempio (`POSTGRES_PWD`, `MINIO_PASSWORD`, `JWT_SECRET_KEY`) → ruotare prima del go-live (§12) |
| GL-2 | Condizione (provenienza) | **Modifiche MOD non committate/pushate** → renderle disponibili sul build host, altrimenti la produzione builderebbe codice senza le modifiche (§2) |
| GL-3 | Condizione (rollback) | Immagine con tag fisso `:local` sovrascritto ad ogni rebuild → **taggare una versione** prima del deploy per il rollback applicativo (§7) |

Nessuno dei tre è un difetto del software: sono passi di provisioning noti e definiti.

## 18. Open Decisions

Richiedono una decisione del responsabile prima/durante il go-live (non risolvibili dal repo):

1. **Dominio Atlas production + reverse proxy + DNS + certificato TLS** — non definiti nel
   repository (§9). Scegliere dominio, reverse proxy (Caddy consigliato dal repo per
   auto-HTTPS), record DNS, certificato. Chiarire la separazione dall'eventuale
   `wiki.firmabratex.pl` (config di `websrv01` esterna al repo → UNKNOWN).
2. **Percorsi storage host** — volumi Docker con nome (default, persistenti) **vs** bind-mount
   `/srv/data/databases/atlas/{postgres,minio}` citati nel prompt (§10). Decisione di
   configurazione del server.
3. **`SPRING_PROFILES_ACTIVE` production** — confermare il profilo inteso (§11).
4. **Versioning/tag delle immagini** per rollback e tracciabilità (§7/§17 GL-3).
5. **Mobile** — quando trattare i bug segnalati dal responsabile (MOD-014 ambiente → MOD-015 fix).
6. **F-04** — se/quando correggere la semantica partial-patch (decisione separata, §5).

## 19. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-013 (Go-Live Readiness, READY WITH CONDITIONS) a Current focus +
Current Project State + Documentation Workflow/Map; registrate le condizioni go-live
(GL-1 secret, GL-2 provenienza sorgenti, GL-3 image tagging) e le Open Decisions
(dominio/DNS/TLS/Caddy, storage host paths, SPRING_PROFILES_ACTIVE, mobile MOD-014/015);
aggiornato lo stato mobile (owner ha verificato la connessione iOS al backend; GUI agent
deferita a MOD-014). Nessuna modifica di codice.
```

## 20. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
Mobile Android: NOT TESTED (agent) — deferito a MOD-014
Mobile iOS: NOT TESTED (agent); connessione al backend VERIFICATA dal responsabile su device reale
F-04: NONE OBSERVED (mobile) / OPEN (pre-esistente, out-of-scope)
Backup readiness: PASS
Rollback readiness: PASS (con raccomandazione image tagging)
Production configuration: READY (core) — condizioni: secret, provenienza sorgenti
Domain/TLS: INCOMPLETE (meccanismo documentato; dominio/DNS/cert da provisionare — OPEN)
Security: PASS (con rotazione secret richiesta)
P0: 0
P1: 0
P2: 0
P3: 1 (F-04, pre-esistente)
Go-live status: READY WITH CONDITIONS
Final verdict: PASS WITH FINDINGS
```

**GO-LIVE STATUS: READY WITH CONDITIONS.** Il prodotto self-hosted (backend source-built +
PostgreSQL + MinIO + frontend + nginx single-ingress) è tecnicamente pronto: backup, rollback,
persistenza, autorizzazione e isolamento multi-tenant sono verificati o pienamente supportati
dal repository; nessun blocker P0/P1. Le condizioni residue sono **attività operative già
definite** — provisioning di dominio/DNS/TLS (meccanismo documentato in `dev-docs/Set up TLS.md`),
rotazione dei secret di produzione, e disponibilità dei sorgenti modificati sul build host —
più le Open Decisions elencate (§18). Il lato mobile (bug segnalati dal responsabile) è
deferito a MOD-014/015 e non blocca il go-live di backend + web.

⏹️ **STOP** — non eseguo il deployment, non modifico `websrv01`/Caddy/DNS/certificati/
database/MinIO production/frontend/mobile, non correggo F-04, non avvio MOD-014. Il deployment
sarà eseguito solo dopo la revisione di questo report e una decisione esplicita del responsabile.
