# MOD-005 — Runtime Integration Verification

Verifica end-to-end **nel processo applicativo reale**, usando un'immagine backend
**costruita dai sorgenti** del repository (non l'immagine upstream prebuilt), avviata
in uno stack Docker self-hosted **isolato**. Fase di sola verifica: nessuna modifica a
codice, licensing, nginx, Docker Compose del repo o dati di ambienti esistenti.
`Code changes: none.`

> Documento autosufficiente. I secret (JWT, password, MinIO/DB) sono mascherati
> (`********`).

---

## 1. Objective

Dimostrare che, con il backend buildato dai sorgenti correnti e avviato con
`LICENSING_SELF_HOSTED_MODE=true`:

1. la self-hosted licensing è attiva a runtime e non contatta Keygen;
2. gli entitlement e il piano BUSINESS / `PlanFeatures` sono risolti;
3. gli allegati (upload/download/delete) funzionano attraverso la catena reale
   `nginx → API → FileController → FileService → StorageService → MinIO`, con il
   comportamento di sicurezza (MOD-004B) e il lifecycle (MOD-004B/004C);
4. l'isolamento company/tenant (`CompanyAudit.@PostLoad`) è attivo;
5. la suite di regressione resta verde.

---

## 2. Scope

IN scope: build immagine backend dai sorgenti, provenienza immagine, stack isolato,
licensing runtime, piano BUSINESS/`PlanFeatures`, upload/download/delete allegati,
security header, lifecycle MinIO, isolamento tenant, routing nginx, regressione.

OUT of scope (non modificati/non-focus): licensing/codice/LDAP/nginx.conf/Docker del
repo, filesystem storage, VF-01 (non corretto), frontend (solo verificato che serva
la chain), GCP.

---

## 3. Sources

`CLAUDE.md`; `22-audit-consolidation.md`; MOD-001 (`13/14`); MOD-004B (`19/20`);
MOD-004C (`21-mod004c`); `docker-compose.yml`; `api/Dockerfile`; `nginx.conf`;
`application.yml`. Codice riletto per il flusso di verifica: `AuthController`,
`UserService.signup/signin`, `SubscriptionPlanService`, `ApplicationInitializer`
(seeding piani), `UserSignupRequest`, `JwtTokenProvider`, `FileController`.

Conclusioni dell'audit 22 assunte come vincolanti (MOD-001/002/003A/004B/004C).

---

## 4. Environment

Stack **isolato**, project name `atlas-cmms-mod005`, rete e volumi dedicati
(`pg_mod005`, `minio_mod005`), **nessun impatto** su ambienti esistenti. Definito da
un compose throwaway fuori dal repo. Un solo punto di ingresso pubblicato: nginx su
`localhost:18085`.

| Servizio | Immagine | Note |
|---|---|---|
| postgres | `postgres:16-alpine` | db `atlas`, volume dedicato |
| minio | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | identica al compose repo |
| api | **`atlas-mod005-backend:local`** | **buildata dai sorgenti** (vedi §5/§6) |
| frontend | `intelloop/atlas-cmms-frontend` | prebuilt upstream, non-focus |
| nginx | `nginx:1.27-alpine` | monta il **`nginx.conf` reale** del repo `:ro` |

Env chiave api: `LICENSING_SELF_HOSTED_MODE=true`, `STORAGE_TYPE=MINIO`,
`MINIO_ENDPOINT=http://minio:9000`, `PUBLIC_MINIO_ENDPOINT=http://localhost:18085/storage`,
`PUBLIC_API_URL=http://localhost:18085/api`, DB/JWT/MinIO creds `********`.

---

## 5. Build Strategy

```text
Dockerfile:      api/Dockerfile (multi-stage)
Build context:   ./api
Base image:      build = maven:3.9.9-eclipse-temurin-17 ; runtime = eclipse-temurin:17-jre
Java/runtime:    Java 17
Build command:   docker build -t atlas-mod005-backend:local ./api
                 (Dockerfile runs: mvn clean package -DskipTests)
Output image:    atlas-mod005-backend:local  (~740 MB)
Tag:             local
Source commit:   e1d24406fe41601773e4924ed68034068d340991
```

TEST: build immagine dai sorgenti · EXPECTED: BUILD SUCCESS · ACTUAL: `[INFO] BUILD
SUCCESS`, exit 0 · RESULT: **PASS** · EVIDENCE: `mod005_build.log`.

L'immagine **non** è `intelloop/atlas-cmms-backend` (upstream): è buildata localmente
dai sorgenti che includono MOD-001/002/004B.

---

## 6. Image Provenance

Il jar è stato estratto dall'immagine (`/app/my-spring-boot-app.jar`) e il bytecode
ispezionato con `javap`.

TEST: l'immagine contiene il codice modificato · EXPECTED: metodi MOD-001/004B
presenti · ACTUAL:

```text
MinioService  : static Map responseHeaderOverrides(com.grash.model.File);  (MOD-004B)
MinioService  : public void delete(java.lang.String);                       (MOD-004B)
StorageService: public abstract void delete(java.lang.String);              (MOD-004B)
LicenseService: private boolean selfHostedMode;                             (MOD-001)
LicenseService: private LicensingState buildSelfHostedLicensingState();     (MOD-001)
```

RESULT: **PASS** — l'immagine buildata contiene le modifiche (non l'upstream).
EVIDENCE: `javap -p` sul jar estratto, commit `e1d24406`.

---

## 7. Self-Hosted Licensing Verification

TEST: self-hosted mode attiva a runtime · EXPECTED: log `SELF_HOSTED`, nessun Keygen ·
ACTUAL:

```text
com.grash.service.LicenseService : Atlas licensing mode: SELF_HOSTED
Started ApiApplication in 31.084 seconds
```

Nessuna riga di rete/entitlement Keygen nei log (`keygen` / `api.keygen.sh` /
`Cached ... entitlements` / `Daily Keygen` → 0 occorrenze).

TEST: gli entitlement sbloccano le feature · EXPECTED: nessun 403 su feature che
MOD-001 deve concedere · ACTUAL: upload allegati (gate `FILE_ATTACHMENTS`) → **HTTP
200** (vedi §9). RESULT: **PASS** — self-hosted licensing riconosciuta, `LicensingState`
valido, Keygen non contattato, entitlement `FILE_ATTACHMENTS` concesso.
EVIDENCE: log api, upload 200.

---

## 8. Plan / PlanFeatures Verification

Domanda aperta dell'audit 22: *il piano BUSINESS è effettivamente risolto a runtime?*

Codice: `UserService.signup` (company-owner) costruisce la `Subscription` con
`subscriptionPlanService.findByCode("BUSINESS")`; `ApplicationInitializer` semina il
piano BUSINESS con `features = Arrays.asList(PlanFeatures.values())` (tutti i 17).

TEST: la company creata vede BUSINESS con FILE · EXPECTED: code=BUSINESS, FILE
presente · ACTUAL (query DB diretta):

```text
 CompanyA | BUSINESS | users_count 300
 CompanyB | BUSINESS | users_count 300
 BUSINESS | n_features = 17 | has_file (ordinal 2) = t
```

RESULT: **PASS** — piano BUSINESS assegnato a runtime, `PlanFeatures.FILE` presente
(confermato anche empiricamente: l'upload supera il gate `PlanFeatures.FILE`).
EVIDENCE: `psql` su `subscription_plan` / `subscription_plan_features` (features
salvate come ordinale enum; `FILE`=2).

---

## 9. Attachment Upload Verification

Catena reale: Client → nginx `/api` → `FileController.handleFileUpload` →
`FILE_ATTACHMENTS` + perm `FILES` + `PlanFeatures.FILE` → `StorageService.upload` →
MinIO; metadati in Postgres.

Autenticazione: `POST /api/auth/signup` (owner) → utente abilitato (localhost/
`INVITATION_VIA_EMAIL=false`) → access token JWT (191 char, `eyJ…`) usato come
`Authorization: Bearer ********`.

TEST: upload non-immagine · EXPECTED: 200 + metadata + object key · ACTUAL: `HTTP 200`,
`{"id":1,"name":"report.html","type":"OTHER",...}`, object key
`mod005/<uuid>_report.html`. RESULT: **PASS**.

TEST: upload immagine · EXPECTED: 200 · ACTUAL: `HTTP 200`, `{"id":2,"type":"IMAGE"}`.
RESULT: **PASS**.

EVIDENCE: `upOther.json`, `upImage.json`. Il solo fatto che l'upload autenticato
ritorni 200 dimostra a runtime: entitlement + plan + permesso tutti risolti.

---

## 10. Attachment Download Verification

Download tramite il **presigned URL emesso dall'API** (host `…:18085/storage`), cioè
attraverso nginx → MinIO.

TEST: presigned URL non-immagine · EXPECTED: 200 + `Content-Disposition: attachment` +
`nosniff` + `X-Frame-Options` · ACTUAL:

```text
HTTP/1.1 200 OK
Content-Type: text/html
Content-Disposition: attachment
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
```

RESULT: **PASS** — la mitigazione stored-XSS MOD-004B è presente nell'immagine
buildata. (L'URL emesso contiene `response-content-disposition=attachment` firmato.)

TEST: presigned URL immagine · EXPECTED: 200, nessuna disposition, inline · ACTUAL:
`200`, `Content-Type: image/png`, **nessun** `Content-Disposition`, `nosniff` +
`X-Frame-Options: DENY`. RESULT: **PASS**.

EVIDENCE: `curl -D` sugli URL dei DTO. Osservazione **O-01** (Info): `nosniff`
compare due volte (MinIO + nginx), innocuo — coerente con MOD-004C.

---

## 11. Attachment Delete Verification

TEST: autorizzazione delete · EXPECTED: solo l'avente diritto · ACTUAL: `DELETE
/api/files/1` come **B** (altra company) → **403**; come **A** (proprietario) → **200**.
RESULT: **PASS**.

TEST: eliminazione metadata · EXPECTED: 404 dopo delete · ACTUAL: `GET /api/files/1`
dopo delete → **404**. RESULT: **PASS**.

TEST: eliminazione object MinIO · EXPECTED: object assente dopo delete · ACTUAL:
presigned URL dell'object → **200 prima**, **404 dopo**. RESULT: **PASS**.

Thumbnail: gli allegati di test non avevano `thumbnailPath` (nessun thumbnail
generato); il ramo thumbnail resta coperto dagli unit test (doc 19). Comportamento in
caso di errore storage (VF-01): **non indotto live** (richiederebbe rompere MinIO
durante la delete); coperto dall'unit test `delete_whenStorageFails_stillDeletesMetadata`
e **accettato** — non modificato.

EVIDENCE: sequenza curl DELETE/GET + presigned URL 200→404.

---

## 12. MinIO Lifecycle Verification

```text
upload (API)      -> DB record id=1 EXISTS (GET /api/files/1 -> 200)
                  -> MinIO object EXISTS  (presigned GET -> 200)
delete (API) A    -> 200
                  -> DB record REMOVED    (GET /api/files/1 -> 404)
                  -> MinIO object REMOVED  (presigned GET -> 404)
```

RESULT: **PASS** — lifecycle storage→DB confermato nel processo reale (binario rimosso
insieme ai metadati).

---

## 13. Security / Tenant Isolation

Ruolo dell'owner creato da signup: **`ROLE_CLIENT`** (role name "Administrator",
`ownsCompany=true`) → **non** esente dal check `@PostLoad` (che esenta solo
`ROLE_SUPER_ADMIN`). Due company reali (A, B) create via `signup`.

TEST: B accede all'allegato di A · EXPECTED: 403 · ACTUAL: `GET /api/files/1` come B →
**403**:

```text
afterLoad:  the user (id=3)  is not authorized to load  this object
(class com.grash.model.File) with id 1
```

RESULT: **PASS** — `CompanyAudit.@PostLoad` attivo a runtime.

TEST: B cancella l'allegato di A · EXPECTED: 403 · ACTUAL: `DELETE /api/files/1` come B
→ **403**. RESULT: **PASS**.

TEST: A accede al proprio allegato · EXPECTED: 200 · ACTUAL: **200**. RESULT: **PASS**.

Nessun bypass introdotto: autorizzazione (`canBeDeletedBy`) e isolamento company
funzionano nel processo reale come da MOD-004B/004C.

---

## 14. nginx / Proxy Verification

TEST: routing attraverso il `nginx.conf` reale · ACTUAL:

```text
GET /               -> 200   (frontend servito)
GET /api/auth/me    -> 401   (API raggiungibile; senza token)
GET /storage/...    -> 200   (presigned valido) / 404 (dopo delete)
```

Header di sicurezza su `/storage`: `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY` presenti; `Content-Disposition` upstream inoltrato senza
rimozione (§10). RESULT: **PASS** — routing API/frontend/storage e proxy verso
backend/MinIO funzionanti; nginx non altera gli header rilevanti.

---

## 15. Test Results

| Comando | Risultato |
|---|---|
| `mvnw test` (suite completa) | **Tests run: 1445, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

Codice invariato rispetto a MOD-004C → baseline 1445 mantenuta. RESULT: **PASS**.
EVIDENCE: `mod005_full.log`. (Test mirati allegati inclusi nella suite; nessun test
esistente modificato; nessun test nuovo aggiunto.)

---

## 16. Findings

Classificazione secondo la tassonomia del brief (A build · B deployment · C runtime ·
D configurazione · E codice · F test · G documentale).

| ID | Classe | Severity | Descrizione | Impatto | Azione |
|---|---|---|---|---|---|
| **CFG-01** | D (config) | Low | `MAIL_RECIPIENTS` non ha default in `application.yml` (`${MAIL_RECIPIENTS}`); se l'env non è impostata l'API **non** parte (fallisce `emailService2`) | Solo ergonomia di deploy: il compose ufficiale passa la variabile; un operatore che dimentica la env ha un boot fallito con causa chiara | Documentato. Non è una regressione MOD-005; possibile miglioria futura: default vuoto. **Non** modificato. |
| **CFG-02** | D (config) + reliability | Low | Il frontend prebuilt richiede env (`HOME_URL`, …) o esce; e `nginx.conf` usa un `upstream` statico `frontend:3000` → se il frontend è down **nginx non parte affatto**, bloccando anche `/api` e `/storage` | Robustezza deploy: un frontend non configurato impedisce l'avvio dell'intero proxy | Registrato separatamente (frontend non-focus, §15). **Non** modificato nginx. Possibile miglioria: `resolver` dinamico o `set`+variabile negli upstream. |
| **O-01** | — | Info | `X-Content-Type-Options: nosniff` duplicato via nginx+MinIO | Nessuno (valore identico) | Nessuna (ereditato MOD-004C). |
| **VF-01** | — | Low | Binario orfano su errore storage reale durante delete | Igiene storage; nessun impatto accesso/sicurezza | **Accettato** (audit 22). Non indotto live; coperto da unit test. Non corretto. |

Nessun finding di classe A/B/C/E/G. Nessun bypass di sicurezza. Nessuna regressione.

---

## 17. Deviations

- **Frontend stubbed? No** — è stato usato il frontend prebuilt reale, ma è
  **non-focus**; l'avvio ha richiesto di fornirgli le sue env (CFG-02). La verifica
  della chain `browser→nginx→frontend→API` è limitata a: `/` → 200 e `/api` → 401.
- **Storage-failure (VF-01)** e **thumbnail delete**: non esercitati live (coperti da
  unit test); vedi §11.
- **Immagine `api`**: buildata localmente (obbligatorio); l'immagine `api` del compose
  ufficiale resta upstream prebuilt e **non** userebbe queste modifiche in un deploy
  reale finché non si builda dai sorgenti (CFG/infra — vedi §18/Recommendations).

---

## 18. Final Verdict

**PASS WITH FINDINGS.**

Tutti i criteri fondamentali di MOD-005 sono soddisfatti con evidenza runtime
nell'immagine buildata dai sorgenti:

- **Licensing**: self-hosted attiva, `LicensingState` valido, **nessun** Keygen,
  entitlement concessi → **PASS**.
- **Plan**: BUSINESS assegnato, `PlanFeatures.FILE` presente (DB + upload) → **PASS**.
- **Allegati**: upload 200, download con disposition per-tipo + security header,
  delete metadata+object → **PASS**.
- **Security**: isolamento tenant `@PostLoad` (403), autorizzazione delete (403),
  nessun bypass → **PASS**.
- **Infrastructure**: backend buildato dai sorgenti, container su immagine locale,
  Postgres/MinIO/nginx funzionanti → **PASS**.
- **Regression**: 1445/1445 → **PASS**.

I findings (CFG-01, CFG-02) sono **di configurazione/deploy**, non bloccanti e fuori
dallo scope target di MOD-005; O-01/VF-01 sono ereditati e accettati.

⏹️ **STOP** — non avvio MOD-006, non implemento feature, non modifico licensing/LDAP/
storage/architettura/production, non correggo VF-01.

---

## 19. Evidence

Artefatti (scratchpad, fuori dal repo; secret mascherati):
`mod005_build.log` (BUILD SUCCESS), jar estratto + `javap` (provenienza),
compose isolato `docker-compose.mod005.yml`, log api (`SELF_HOSTED`, startup),
`signupA/B.json`, `meA.json`, `upOther/upImage.json`, header curl download,
sequenza isolamento/delete, query `psql` piano BUSINESS, `mod005_full.log` (1445/1445).

Riepilogo evidenze principali:

```text
BUILD SUCCESS ; image atlas-mod005-backend:local ; commit e1d24406
javap: responseHeaderOverrides/delete (MinioService), buildSelfHostedLicensingState (LicenseService)
log: "Atlas licensing mode: SELF_HOSTED" ; no Keygen calls
DB: CompanyA/B -> BUSINESS(300) ; BUSINESS 17 features ; FILE(ord.2)=t
upload OTHER 200 -> url ?response-content-disposition=attachment
upload IMAGE 200 -> url senza disposition
download OTHER 200: Content-Disposition attachment + nosniff + X-Frame-Options DENY
download IMAGE 200: no disposition + nosniff + X-Frame-Options DENY
GET file A=200 ; B=403 (@PostLoad) ; DELETE B=403 ; DELETE A=200
after delete: GET 404 ; storage object 404
nginx: / =200 ; /api=401 ; /storage=200/404
regression: 1445/0/0/0 BUILD SUCCESS
```

---

## 20. Documenti da aggiornare (proposta — non modificati in questa fase)

Come da regola del brief (§22), **non** ho modificato `CLAUDE.md` né i documenti
precedenti. Aggiornamenti consigliati dopo revisione:

- `CLAUDE.md`: aggiungere MOD-005 (PASS WITH FINDINGS) allo stato/Documentation Map;
  chiudere la domanda aperta dell'audit 22 sul piano BUSINESS (**confermato a runtime**).
- `22-audit-consolidation.md`: la open question "assegnazione piano BUSINESS" è ora
  **risolta** (BUSINESS + FILE verificati a runtime).

`Code changes: none.`
