# 18 — MOD-004 Attachment / Storage Audit

Fase **solo audit** (allegati / storage / MinIO). Nessuna modifica al codice,
nessun commit. `Code changes: none`.

Riferimenti: [CLAUDE.md](../CLAUDE.md), [04-feature-matrix.md](04-feature-matrix.md),
[06-storage-attachments.md](06-storage-attachments.md),
[13-mod001-implementation.md](13-mod001-implementation.md),
[15-mod002-verification.md](15-mod002-verification.md),
[17-mod003a-implementation.md](17-mod003a-implementation.md).

---

## Executive Summary

Gli allegati Atlas sono **completamente implementati end-to-end** (upload →
metadati PostgreSQL → binario MinIO → download via presigned URL). L'unico gate
commerciale è l'entitlement **`FILE_ATTACHMENTS`**, già concesso in self-hosted
da **MOD-001**; il secondo gate `PlanFeatures.FILE` è soddisfatto dal piano
BUSINESS. L'isolamento multi-tenant è **effettivamente applicato a livello ORM**
(`CompanyAudit.@PostLoad`). Per il self-hosted serve **solo configurazione**
(MinIO), già cablata in `docker-compose.yml`.

**Raccomandazione: MOD-004 = configuration + verification only.** Nessuna modifica
al codice necessaria per la funzionalità base. Restano alcuni findings di
sicurezza/lifecycle da valutare separatamente (nessun bypass di autorizzazione).

---

## Audit Scope

IN scope: implementazione allegati, gate licensing, storage MinIO, metadati DB,
upload/download/delete, autorizzazione, isolamento tenant, validazione file,
frontend, test, Docker. OUT of scope (non toccati): licensing, `FILE_ATTACHMENTS`
semantics, redesign MinIO, migrazioni, config Docker di produzione, MOD-005/006.

Repository: branch `main`, HEAD `e1d24406`. `git status` mostra solo le modifiche
dei MOD precedenti (MOD-001/002/003A) — nessuna modifica introdotta da MOD-004.

---

## Current Architecture

```
Frontend (FileShowDTO.url = presigned URL)
   ↓
POST /files/upload         (FileController)          ← gate: FILE_ATTACHMENTS + PlanFeatures.FILE + perm FILES
GET  /files/{id}           (metadata + signed URL)   ← canBeViewedBy + @PostLoad company check
POST /files/search         (paged list)              ← company filter + @PostLoad
PATCH/DELETE /files/{id}                              ← canBeEditedBy / canBeDeletedBy
   ↓
FileService (CRUD)  →  FileRepository  →  PostgreSQL (metadata)
   ↓
StorageServiceFactory.getStorageService()  →  MinioService | GCPService
   ↓
MinIO bucket (binario)  ←→  download via presigned URL (nginx /storage proxy)
```

Componenti (file:classe):

| Componente | File |
|---|---|
| Controller | [`controller/FileController.java`](../../api/src/main/java/com/grash/controller/FileController.java) |
| Entity | [`model/File.java`](../../api/src/main/java/com/grash/model/File.java) (estende `abstracts/CompanyAudit`) |
| Service | [`service/FileService.java`](../../api/src/main/java/com/grash/service/FileService.java) |
| Repository | [`repository/FileRepository.java`](../../api/src/main/java/com/grash/repository/FileRepository.java) |
| Mapper (URL firmato) | [`mapper/FileMapper.java`](../../api/src/main/java/com/grash/mapper/FileMapper.java) |
| Storage interface | [`service/StorageService.java`](../../api/src/main/java/com/grash/service/StorageService.java) |
| MinIO | [`service/MinioService.java`](../../api/src/main/java/com/grash/service/MinioService.java) |
| GCP | [`service/GCPService.java`](../../api/src/main/java/com/grash/service/GCPService.java) |
| Factory | [`factory/StorageServiceFactory.java`](../../api/src/main/java/com/grash/factory/StorageServiceFactory.java) |
| Object key | [`utils/Helper.generateUniqueFilePath`](../../api/src/main/java/com/grash/utils/Helper.java#L63) |

---

## Licensing Analysis

| Feature | Gate | Backend | Frontend | Self-hosted state | Action |
|---|---|---|---|---|---|
| Upload | `FILE_ATTACHMENTS` (throw) + `PlanFeatures.FILE` + perm `FILES` | `FileController:69,76-77` | `hasFeature(PlanFeature.FILE)` (Files/index:333, AddFileModal:64) | ✅ concesso (MOD-001 + BUSINESS) | config MinIO |
| Download | nessun gate entitlement; presigned URL da DTO autorizzato | `FileMapper.getSignedUrl` | usa `FileShowDTO.url` | ✅ | — |
| Delete | nessun gate entitlement; `canBeDeletedBy` | `FileController:174-188` | — | ✅ | — |
| Listing/Search | nessun gate entitlement; company filter | `FileController:121-142` | — | ✅ | — |
| Preview/Thumbnail | nessun gate entitlement; presigned URL | `FileMapper.toThumbnailDto` | — | ✅ | — |
| Request-portal upload | **nessun** gate `FILE_ATTACHMENTS` (path pubblico, rate-limit IP) | `FileController:95-119` | portale pubblico | ✅ | by design |

Catena: `entitlement → licensing state → backend gate → frontend gate → feature`.

- **`FILE_ATTACHMENTS`**: gate solo nell'upload autenticato
  ([`FileController.java:69`](../../api/src/main/java/com/grash/controller/FileController.java#L69)).
  MOD-001 concede l'intero enum in self-hosted → `hasEntitlement(FILE_ATTACHMENTS)`
  è `true` (dimostrato da `LicenseServiceTest`, che verifica l'intero enum). Nessuna
  modifica al licensing.
- **`PlanFeatures.FILE`** (Livello B): richiesto anche nell'upload
  ([`FileController.java:77`](../../api/src/main/java/com/grash/controller/FileController.java#L77))
  e usato dal frontend (`hasFeature`). Soddisfatto dal piano **BUSINESS** assegnato
  in self-hosted (vedi [13-mod001-implementation.md](13-mod001-implementation.md)).
- Download/delete/list **non** hanno gate entitlement: dipendono da autorizzazione
  (permessi + ownership + company).

Classificazione licensing: **B risolto da MOD-001** (upload) + gate ruolo/plan già
aperti in self-hosted.

---

## Backend Attachment Flow

### Upload (`POST /files/upload`)

```
request (files[], folder, hidden, type, taskId)
→ hasEntitlement(FILE_ATTACHMENTS)          [403 se assente]
→ rateLimiterService.tryConsumeFileUpload   [429 se superato]
→ perm FILES (create) AND PlanFeatures.FILE  [oppure bypass]  [403 altrimenti]
→ storageService.upload(file, folder) → object key
→ fileService.create(new File(originalName, path, type, task, hidden))
   → @PrePersist imposta company = utente corrente
→ FileShowDTO (con presigned URL)
```

### Download (nessun endpoint binario dedicato)

Il binario **non** è servito da un endpoint controller: il DTO restituito
(`FileShowDTO.url`) contiene un **presigned URL** MinIO (scadenza 3h) generato in
`FileMapper.getSignedUrl` → `MinioService.generateSignedUrl` (endpoint interno
sostituito con `PUBLIC_MINIO_ENDPOINT` = `/storage`). Il client scarica da MinIO
via proxy nginx `/storage/`. L'autorizzazione avviene **prima** del rilascio
dell'URL (endpoint `getById`/`search`).

### Delete (`DELETE /files/{id}`)

```
→ canBeDeletedBy(user)   [deleteOther FILES OR creator]
→ fileService.delete(id) → fileRepository.deleteById(id)   [SOLO metadati DB]
```

> ⚠️ **Finding**: `delete` rimuove **solo** la riga DB; l'oggetto binario in MinIO
> **non** viene cancellato (`MinioService` ha un `download`/`exists` ma nessuna
> `delete` invocata qui) → oggetti orfani accumulati. Lifecycle, non un bypass di
> sicurezza.

---

## Frontend Attachment Flow

- [`content/own/Files/index.tsx:333`](../../frontend/src/content/own/Files/index.tsx#L333):
  UI upload gated da `hasFeature(PlanFeature.FILE)`.
- [`content/own/WorkOrders/Details/AddFileModal.tsx:64`](../../frontend/src/content/own/WorkOrders/Details/AddFileModal.tsx#L64):
  controllo upload gated da `hasFeature(PlanFeature.FILE)`.
- Il frontend gate **solo** su `PlanFeature.FILE` (subscription), **non** su
  `useLicenseEntitlement('FILE_ATTACHMENTS')`. In self-hosted `PlanFeature.FILE`
  è vero (BUSINESS) → UI mostrata; il backend consente (MOD-001 + BUSINESS).
- Download/preview usano `FileShowDTO.url` (presigned) ricevuto dal backend.

Nessuna modifica al frontend richiesta o eseguita.

---

## Database / Metadata

Entity `File` (tabella `file`, via `@Entity` + `CompanyAudit`):

| Campo | Note |
|---|---|
| `id` (PK) | `CompanyAudit` |
| `company` (FK, NOT NULL) | `CompanyAudit` — **tenant** |
| `createdBy` / audit timestamps | `Audit` — uploader/owner via `createdBy` |
| `name` | nome file |
| `path` | **object key** in MinIO (non URL) |
| `type` (`FileType`) | IMAGE/OTHER/… |
| `hidden` | flag |
| `thumbnailPath` | key thumbnail (nullable) |
| `task` (FK, cascade) | associazione task |
| associazioni ManyToMany | `T_Asset_File_Associations`, `T_Part_File_Associations`, `T_Request_File_Associations`, `T_WorkOrder_File_Associations`, `T_Location_File_Associations` |

- **Binario NON in PostgreSQL** → PostgreSQL = metadati; MinIO = oggetto binario
  (verificato: `putObject`/`getObject` in `MinioService`).
- **Nessun campo** per MIME type, dimensione, original filename separato: i
  metadati sono minimi (`name`, `path`, `type`, `hidden`).
- Nessuna migrazione toccata.

---

## MinIO / Storage

`MinioService` (SDK `io.minio`):

- `@PostConstruct init()`: se una delle env MinIO è vuota → non configurato
  (`configured=false`); altrimenti crea il client e **crea il bucket se assente**.
- `upload(MultipartFile, folder)` → `Helper.generateUniqueFilePath` genera l'object
  key; `putObject` con `contentType = file.getContentType()` (client-controlled).
  Ritorna l'object key.
- `generateSignedUrl(File, minutes)` → **presigned GET URL** (`@Cacheable`),
  sostituisce `endpoint` con `public-endpoint`. Scadenza usata dal mapper: **180
  min (3h)**.
- `download(path)` → GET server-side (usato per export/thumbnail).
- **Nessun metodo `delete` esposto/usato** nel flusso di cancellazione.

Config (env, `application.yml` righe 134-141):

| Proprietà | Env | Note |
|---|---|---|
| `storage.type` | `STORAGE_TYPE` | `MINIO` (default factory → MinIO) |
| `storage.minio.endpoint` | `MINIO_ENDPOINT` | interno (`http://minio:9000`) |
| `storage.minio.bucket` | `MINIO_BUCKET` | `atlas-bucket` |
| `storage.minio.access-key` | `MINIO_ACCESS_KEY` | = `MINIO_USER` |
| `storage.minio.secret-key` | `MINIO_SECRET_KEY` | = `MINIO_PASSWORD` (**secret**) |
| `storage.minio.public-endpoint` | `PUBLIC_MINIO_ENDPOINT` | `${PUBLIC_SERVER_URL}/storage` |

- **MinIO obbligatorio o GCP**: `StorageType` = `{GCP, MINIO}`. **Non esiste
  storage su filesystem** (l'assunzione "MinIO/filesystem" del brief non è
  supportata dal codice: solo MinIO o GCP).
- **Bucket init**: automatica all'avvio.
- **Esposizione**: MinIO in `docker-compose.yml` usa `expose: 9000/9001` (**non**
  `ports:`) → non pubblicato all'host; raggiungibile solo nella rete compose.
  nginx espone `/storage/` → `atlas_minio:9000` (proxy) per i presigned URL.
- **Volume persistente**: `minio_data:/data`.
- **Credenziali**: via env (`MINIO_ROOT_USER/PASSWORD`) — trattare come secret.

---

## Upload / Download / Delete

- **Upload**: gate (entitlement+plan+perm) → object key sicuro (UUID + sanitizzato)
  → putObject → metadati (company via `@PrePersist`). Limite 35MB/file.
- **Download**: presigned URL (3h) nel DTO, rilasciato dopo autorizzazione; download
  da MinIO via `/storage` con firma. Bucket privato (accesso solo con firma).
- **Delete**: rimuove metadati DB; **non** rimuove il binario (finding lifecycle).

---

## Authorization

Meccanismo reale (verificato):

1. **`@PostLoad` su `CompanyAudit`** ([`model/abstracts/CompanyAudit.java`](../../api/src/main/java/com/grash/model/abstracts/CompanyAudit.java)):
   ad ogni load, se l'utente non è super-admin e `entity.company != user.company`
   (e nessuna super-account exception) → `403`. Applica l'isolamento tenant a
   livello ORM su **ogni** `findById`.
2. **`File.canBeViewedBy/canBeEditedBy/canBeDeletedBy`**: permesso ruolo
   (`FILES` view/edit/delete-other) **oppure** ownership (`createdBy == user.id`).
3. **`/search`**: per `ROLE_CLIENT` forza `filterCompany`, filtra `createdBy` se
   manca view-other, e forza `hidden=false`.
4. **`TenantAspect`**: valida i `@RequestBody CompanyAudit` su POST/PATCH ricaricandoli
   (riattiva `@PostLoad`).

`canBeViewedBy` **non** controlla la company direttamente, ma il controllo
company è garantito dal `@PostLoad` sul load del `File`. Quindi non c'è bypass
cross-company via `GET /files/{id}`.

---

## Company / Tenant Isolation

```
current user → company → (findById File) → @PostLoad company check → 403 se diversa
```

Un utente della company A che richiede `GET /files/{id}` di un file della company
B riceve **403** al load (`@PostLoad`), **prima** di `canBeViewedBy`. La ricerca
filtra per company. **Isolamento tenant applicato** (verificato dal codice, non
assunto dalle sole relazioni DB).

Eccezioni legittime: super-admin e super-account relations (multi-company
autorizzato) — coerenti col modello esistente.

---

## File Validation and Security

| Controllo | Stato |
|---|---|
| Dimensione massima | ✅ 35MB/file, 100MB/request (`spring.servlet.multipart`, `application.yml:33-35`) |
| Allowlist tipi/estensioni | ❌ **assente** (nessun controllo MIME/estensione applicativo) |
| Validazione MIME | ❌ `contentType` è client-controlled, salvato as-is |
| Sanitizzazione filename | ✅ `Helper.generateUniqueFilePath`: `cleanPath` + `[^a-zA-Z0-9._-]→_` |
| Path traversal | ✅ prevenuto (`/` e `..` sanitizzati; object key = `folder_sanitizzato/UUID_nome`) |
| Object key generation | ✅ prefisso `UUID.randomUUID()` → no collisioni/overwrite |
| Overwrite | ✅ upload utente non sovrascrive (UUID). `uploadAt` sovrascrive ma è caller-controlled interno |
| Download authorization | ✅ presigned URL rilasciato post-autorizzazione; bucket privato |
| Delete authorization | ✅ `canBeDeletedBy` |
| Cross-company object isolation | ✅ via `@PostLoad` (metadati); l'object key non è indovinabile (UUID) |

**Attenzione (findings, non corretti in audit):**

- `../`, path assoluti → **neutralizzati** dalla sanitizzazione.
- **Content-Type client-controlled** + **nessuna allowlist tipi** → un file
  HTML/SVG caricato come allegato viene servito con quel content-type via
  `/storage` (stessa origine): possibile **stored-XSS** se un utente autorizzato
  apre l'URL. Da valutare (allowlist tipi e/o `Content-Disposition: attachment` /
  `X-Content-Type-Options: nosniff` sul proxy). Classe **G**.
- **Presigned URL 3h**: chiunque abbia l'URL può scaricare entro la scadenza
  (comportamento standard dei presigned URL). Informativo.
- **`/files/upload/request-portal/{uuid}`**: path **pubblico** senza gate
  `FILE_ATTACHMENTS`, rate-limited per IP e legato a un portale valido (by design
  per l'intake pubblico delle richieste). Da tenere presente.

---

## Existing Tests

| Test | Copertura |
|---|---|
| [`factory/StorageServiceFactoryTest.java`](../../api/src/test/java/com/grash/factory/StorageServiceFactoryTest.java) | selezione MinIO vs GCP nel factory |
| `service/WorkOrderServiceTest.java` | usa storage/file indirettamente |

**Nessun** test dedicato a: `FileController` (upload/download/delete/authz),
`MinioService`, isolamento company sugli allegati, gate `FILE_ATTACHMENTS`.

---

## Missing Test Coverage

Piano (non implementato in audit):

```
upload consentito (entitlement+plan+perm) / rifiutato (senza FILE_ATTACHMENTS → 403)
download: URL firmato rilasciato solo se autorizzato
delete autorizzato / non autorizzato (canBeDeletedBy)
company isolation: File company B da utente company A → 403 (@PostLoad)
oggetto mancante in MinIO → errore gestito
file oversize (>35MB) → rifiutato da multipart
tipo file non ammesso (se si introdurrà una allowlist)
storage failure (MinIO down) → 500 gestito
entitlement on/off → upload abilitato/bloccato
```

Infrastruttura: Testcontainers è presente nel progetto; esiste un'immagine MinIO
ufficiale utilizzabile per test di integrazione (non presente oggi un test MinIO
Testcontainers dedicato).

---

## Docker / Deployment Configuration

- Servizio `minio` (`docker-compose.yml`): volume `minio_data:/data` (persistente),
  `expose: 9000/9001` (non pubblicato), credenziali `MINIO_ROOT_USER/PASSWORD`.
- Servizio `api`: `STORAGE_TYPE`, `MINIO_ENDPOINT=http://minio:9000`,
  `MINIO_BUCKET=atlas-bucket`, `MINIO_ACCESS_KEY/SECRET_KEY`,
  `PUBLIC_MINIO_ENDPOINT=${PUBLIC_SERVER_URL}/storage`.
- `nginx.conf`: `location /storage/ → proxy_pass http://atlas_minio/` per i
  presigned URL; `client_max_body_size` da tarare per upload grandi.
- `.env.example`: `STORAGE_TYPE=MINIO`, `MINIO_USER`, `MINIO_PASSWORD`.

**Configurazione self-hosted già presente e sufficiente.** Nessuna modifica alla
config di produzione (come da vincolo).

---

## Feature Classification

Legenda: A implementato e disponibile self-hosted · B implementato ma
licensing-gated risolto da MOD-001 · C implementato ma richiede configurazione ·
D parziale, richiede codice · E non implementato · F dipendenza esterna · G issue
di sicurezza da decidere separatamente.

| Capability | Classe |
|---|---|
| Upload allegati (autenticato) | **B** (FILE_ATTACHMENTS risolto da MOD-001) + **C** (MinIO) |
| Download (presigned URL) | **A** |
| Delete metadati | **A** |
| Listing/Search con isolamento | **A** |
| Thumbnail/preview | **A** |
| Storage MinIO | **C** (config) / **F** (servizio MinIO) |
| Storage GCP (alternativa) | **C/F** |
| Storage filesystem | **E** (non implementato) |
| Isolamento tenant | **A** (`@PostLoad`) |
| Pulizia binario su delete | **D/G** (metadati eliminati, binario orfano) |
| Allowlist tipi + content-type handling | **G** (assente; possibile stored-XSS) |

---

## Findings

1. ✅ Allegati implementati end-to-end; upload gated solo da `FILE_ATTACHMENTS`
   (MOD-001) + `PlanFeatures.FILE` (BUSINESS) + perm `FILES`.
2. ✅ Isolamento tenant applicato a livello ORM (`CompanyAudit.@PostLoad`).
3. ✅ Object key sicuro (UUID + sanitizzazione) → no path traversal/overwrite.
4. ✅ MinIO non pubblicato all'host; download via presigned URL su bucket privato.
5. ✅ Limite dimensione 35MB/file (Spring multipart).
6. ⚠️ **G**: nessuna allowlist tipi; content-type client-controlled → possibile
   stored-XSS su file HTML/SVG serviti via `/storage`. Da decidere.
7. ⚠️ **D/G**: `delete` non rimuove il binario MinIO → oggetti orfani.
8. ℹ️ `/files/upload/request-portal/{uuid}` pubblico, senza gate `FILE_ATTACHMENTS`
   (by design, rate-limited).
9. ℹ️ Nessuno storage su filesystem (solo MinIO/GCP).
10. ⚠️ Copertura test allegati minima (nessun test upload/download/authz/isolamento).

---

## Required Configuration

Per abilitare gli allegati in self-hosted (nessun codice):

```env
STORAGE_TYPE=MINIO
MINIO_ENDPOINT=http://minio:9000
MINIO_BUCKET=atlas-bucket
MINIO_ACCESS_KEY=<minio-user>
MINIO_SECRET_KEY=<minio-password-secret>
PUBLIC_MINIO_ENDPOINT=${PUBLIC_SERVER_URL}/storage
LICENSING_SELF_HOSTED_MODE=true   # concede FILE_ATTACHMENTS (MOD-001)
```

Più: piano BUSINESS (default self-hosted) e permesso ruolo `FILES`. Il servizio
`minio` + il proxy nginx `/storage/` sono già nella compose.

---

## Required Code Changes

**Nessuno per la funzionalità base.** MOD-004 = configuration + verification.

Modifiche **opzionali** (findings — richiedono decisione, non implementate qui):

- MOD-004b (sicurezza): allowlist tipi file + `Content-Disposition: attachment` /
  `X-Content-Type-Options: nosniff` per gli allegati serviti da `/storage`.
  Rischio: medio (stored-XSS). File: `nginx.conf` e/o validazione in
  `FileController`/`MinioService`.
- MOD-004c (lifecycle): cancellare l'oggetto MinIO in `FileService.delete` /
  `MinioService.delete`. Rischio: basso. File: `service/FileService.java`,
  `service/MinioService.java`.
- MOD-004d (test): suite allegati (upload/download/authz/isolamento/entitlement).

Ognuna sarebbe una MOD separata con file/classe/metodo, comportamento
attuale/desiderato, rischio, test e rollback — **da approvare**.

---

## Risks

| Rischio | Gravità | Nota |
|---|---|---|
| Stored-XSS via content-type client-controlled | Media | nessuna allowlist tipi; serviti same-origin via `/storage` |
| Binari orfani su delete | Bassa | crescita storage; nessun impatto di accesso |
| Presigned URL condivisibile entro 3h | Bassa | comportamento standard |
| Copertura test allegati minima | Media | regressioni non rilevate |
| MinIO non configurato → upload 500 | Bassa | gestito (`checkIfConfigured`) |

---

## Recommendation

**MOD-004 = configuration + verification only.** La funzionalità allegati è
completa; l'entitlement `FILE_ATTACHMENTS` è concesso da MOD-001; il gate
`PlanFeatures.FILE` è soddisfatto; l'isolamento tenant è applicato; MinIO è già
cablato in Docker. **Nessuna modifica al codice è necessaria** per l'uso base.

I findings di sicurezza/lifecycle (allowlist tipi/content-type, pulizia binario,
test) sono **decisioni separate** (MOD-004b/c/d), da approvare — **non**
implementate in questo audit.

---

## Decision Gate

**STOP.** Non implemento MOD-004, non modifico licensing/MinIO/DB/frontend/backend/
Docker, non procedo a MOD-005. Il responsabile tecnico deciderà se MOD-004 richiede
solo configurazione+verifica, una security review (findings G), o nessun ulteriore
intervento.

`Code changes: none.`
