# 19 — MOD-004B Implementation: Attachment security & lifecycle

Implementazione mirata dei due findings dell'audit MOD-004 sugli allegati:
mitigazione stored-XSS e lifecycle di cancellazione MinIO. Nessuna modifica al
licensing né al modello multi-tenant. Nessun commit/push.

Ambiente: Windows 11, JDK 17 Temurin 17.0.20.1 (portable), Maven 3.8.6 (wrapper),
Docker 29.4.3. Riferimento: [18-mod004-attachment-audit.md](18-mod004-attachment-audit.md).

---

## Stato

**PASS** — entrambi i findings risolti, test dedicati 6/6, suite completa
1445/0/0/0. Licensing e multi-tenancy invariati.

---

## Problema originale

Dall'audit MOD-004 (findings G/D):

1. **Stored-XSS**: il `Content-Type` degli allegati è client-controlled e i file
   sono serviti tramite presigned URL su `/storage` (stessa origine). Un file
   HTML/SVG poteva essere reso inline dal browser in caso di navigazione
   top-level → esecuzione di contenuto attivo same-origin.
2. **Lifecycle**: `FileService.delete()` eliminava solo il record PostgreSQL, non
   l'oggetto binario in MinIO → oggetti orfani.

---

## Codice coinvolto (verificato prima della modifica)

- [`controller/FileController.java`](../../api/src/main/java/com/grash/controller/FileController.java) — `DELETE /files/{id}` → `canBeDeletedBy` → `fileService.delete(id)` (unico chiamante).
- [`service/FileService.java`](../../api/src/main/java/com/grash/service/FileService.java) — `delete(id)` faceva solo `deleteById`.
- [`service/MinioService.java`](../../api/src/main/java/com/grash/service/MinioService.java) — `generateSignedUrl` senza disposition; nessun `delete`.
- [`service/GCPService.java`](../../api/src/main/java/com/grash/service/GCPService.java) — analogo, nessun `delete`.
- [`service/StorageService.java`](../../api/src/main/java/com/grash/service/StorageService.java) — interfaccia senza `delete`.
- [`mapper/FileMapper.java`](../../api/src/main/java/com/grash/mapper/FileMapper.java) — `getSignedUrl` usava l'overload `String path`.
- [`nginx.conf`](../../nginx.conf) — location `/storage/` **già** con `X-Content-Type-Options: nosniff` e `X-Frame-Options: DENY` (righe 74-75).
- `FileType` = `{IMAGE, OTHER}` (nessun altro tipo).

---

## Soluzione adottata

### 1. Stored-XSS (backend, mirata)

Nel presigned URL degli allegati **non-immagine** viene aggiunto
`response-content-disposition=attachment`, così MinIO restituisce
`Content-Disposition: attachment` e il browser **scarica** il file invece di
renderizzarlo inline (HTML/SVG non eseguiti su navigazione top-level). Le
**immagini** restano inline → preview invariata.

- `MinioService.generateSignedUrl(File)` applica `responseHeaderOverrides(file)`.
- `FileMapper.getSignedUrl` usa l'overload `File` (non più il `path`), così la
  decisione per-tipo scatta.
- **nginx non modificato**: la location `/storage/` ha già `nosniff` +
  `X-Frame-Options: DENY`; la disposition backend chiude l'unico gap residuo
  (navigazione top-level). Difesa in profondità: nosniff (no MIME sniffing) +
  X-Frame-Options DENY (no iframe/object) + Content-Disposition attachment
  (no rendering top-level dei non-immagine).

### 2. Lifecycle MinIO

`FileService.delete(id)` ora, nell'ordine del brief
(`autorizzazione → oggetto storage → metadati DB`):

```
findById(id)            [@PostLoad company check → 403 se altra company]
  → delete storage (path)
  → delete storage (thumbnailPath, se presente)
deleteById(id)          [metadati]
```

- Aggiunto `StorageService.delete(String path)` (idempotente su oggetto assente),
  implementato in `MinioService` (`removeObject`) e `GCPService`
  (`storage.delete(BlobId)`).

---

## File modificati

| File | Modifica |
|---|---|
| `service/StorageService.java` | + `void delete(String filePath)` (interfaccia) |
| `service/MinioService.java` | + `delete` (removeObject), + `responseHeaderOverrides`, `generateSignedUrl(File)` con disposition per non-immagine |
| `service/GCPService.java` | + `delete` (`storage.delete(BlobId)`) |
| `service/FileService.java` | `delete(id)` rimuove storage (path+thumbnail) poi metadati; + dip. `StorageServiceFactory`, `@Slf4j` |
| `mapper/FileMapper.java` | `getSignedUrl` usa l'overload `File` |
| *(nuovi)* `test/.../FileServiceTest.java`, `test/.../MinioServiceTest.java` | test |

Diff: ~+107 righe di codice applicativo su 5 file + 2 test. **nginx.conf,
licensing, multi-tenant, Docker: non modificati.**

---

## Comportamento precedente → nuovo

| Aspetto | Prima | Dopo |
|---|---|---|
| Presigned URL non-immagine | nessuna disposition → possibile render inline top-level | `Content-Disposition: attachment` → download |
| Presigned URL immagine | inline | inline (invariato) |
| Delete file | solo metadati DB; binario orfano | binario (+thumbnail) rimosso, poi metadati |
| Oggetto storage assente su delete | n/a | no-op idempotente |
| Errore storage su delete | n/a | loggato; metadati comunque rimossi |

---

## Decisioni tecniche

1. **Disposition via presigned URL (non nginx blanket)**: scelto l'approccio
   backend per-tipo perché testabile e perché un `Content-Disposition: attachment`
   globale su nginx forzerebbe il download anche in navigazione top-level delle
   immagini. (Nota: i tag `<img>` ignorano comunque la disposition per i
   subresource, ma la scelta backend è più precisa e verificabile.)
2. **Solo `OTHER` forzato ad attachment; `IMAGE` inline**: coerente con l'enum
   `FileType` reale (solo IMAGE/OTHER) e con la preview immagini documentata.
3. **Ordine delete = storage → DB**: come da diagramma del brief.
4. **`StorageService.delete` idempotente**: MinIO `removeObject` e GCP
   `storage.delete` non lanciano per oggetto assente.
5. **Best-effort sullo storage**: la cancellazione dei metadati non viene mai
   bloccata da un errore storage (nessun blocco per l'utente; comportamento
   documentato).

---

## Gestione degli errori

- **Oggetto assente**: no-op (idempotente) — nessun errore.
- **Errore storage reale** (es. MinIO down): `MinioService.delete` lancia
  `CustomException`; `FileService.deleteStorageObjectQuietly` lo **cattura e
  logga** (`log.warn`), poi procede con la cancellazione dei metadati. In questo
  caso il binario resta orfano (raro), ma non peggio del comportamento precedente
  (che lasciava sempre orfano il binario). Documentato.
- **`path`/`thumbnailPath` null/blank**: saltati.
- **File inesistente** (`findById` vuoto): nessuna chiamata storage; `deleteById`
  procede (idempotente).

---

## Test aggiunti

`service/FileServiceTest.java` (3) e `service/MinioServiceTest.java` (3):

| Test | Verifica |
|---|---|
| `FileServiceTest.delete_removesStorageObjectsThenMetadata` | ordine: storage(path) → storage(thumbnail) → deleteById (InOrder) |
| `FileServiceTest.delete_whenStorageFails_stillDeletesMetadata` | errore storage → metadati comunque rimossi |
| `FileServiceTest.delete_whenFileAbsent_deletesByIdWithoutStorageCall` | file assente → nessuna chiamata storage |
| `MinioServiceTest.responseHeaderOverrides_forcesAttachmentForNonImageOnly` | OTHER→attachment; IMAGE→nessuna override |
| `MinioServiceTest.generateSignedUrl_nonImage_appliesAttachmentDisposition` | args presigned contengono `response-content-disposition=attachment` |
| `MinioServiceTest.generateSignedUrl_image_staysInline` | immagini senza disposition |

### Verifica multi-tenant

`CompanyAudit.@PostLoad` **non** modificato. `FileService.delete` carica il file
via `findById` (che riattiva `@PostLoad`) e l'autorizzazione `canBeDeletedBy`
resta nel controller a monte → la nuova logica **non aggira** l'autorizzazione né
l'isolamento company. (Il `@PostLoad` è JPA-level, non riproducibile in unit test
puro; nessuna riga di autorizzazione/tenant è stata toccata.)

---

## Risultati dei test

| Comando | Esito |
|---|---|
| `mvnw test -Dtest=FileServiceTest,MinioServiceTest` | **6/6** (0 fail, 0 err) |
| `mvnw test` (suite completa) | **Tests run: 1445, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

Baseline MOD-003A: **1439** → ora **1445** = **+6** (i test MOD-004B). Nessun test
preesistente rotto. Nessun test non eseguito.

`docker compose config` non ri-verificato perché MOD-004B **non** ha toccato
`docker-compose.yml`/`nginx.conf` (invariati).

---

## Limitazioni residue

- La mitigazione dipende dal fatto che MinIO onori `response-content-disposition`
  (comportamento standard S3/MinIO) e che il proxy `/storage` inoltri i query
  param firmati (già così).
- Nessuna **allowlist** di tipi file introdotta: si è preferita la neutralizzazione
  del rendering (attachment + nosniff) senza limitare i tipi supportati, come da
  brief ("non introdurre una allowlist arbitraria senza verificare i tipi
  supportati").
- In caso di errore storage reale durante il delete, il binario può restare
  orfano (loggato) — accettato e documentato.

---

## Finding ancora aperti

- Copertura test end-to-end degli allegati (upload/download autorizzati via
  Testcontainers+MinIO) resta un'estensione possibile (MOD-004d), **non**
  richiesta da MOD-004B.
- `/files/upload/request-portal/{uuid}` pubblico senza gate `FILE_ATTACHMENTS`
  (by design) — invariato, segnalato in MOD-004.

---

## Riepilogo

- ✅ Finding stored-XSS mitigato (disposition attachment per non-immagine +
  nosniff/X-Frame-Options già presenti).
- ✅ Lifecycle MinIO su delete implementato (binario+thumbnail, idempotente,
  best-effort).
- ✅ Test 6/6; suite 1445/0/0/0.
- ✅ Nessuna modifica a licensing, multi-tenancy, nginx, Docker.
- ⏹️ **STOP**: non procedo a MOD-005. La decisione sul passo successivo spetta al
  responsabile tecnico.

`Code changes: limited to MOD-004B scope (StorageService, MinioService, GCPService,
FileService, FileMapper + 2 test).`
