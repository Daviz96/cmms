# 20 — MOD-004B — Verifica finale

Verifica indipendente dell'implementazione descritta in
[19-mod004b-security-lifecycle-implementation.md](19-mod004b-security-lifecycle-implementation.md).
Fase **solo verifica**: nessuna modifica a codice, test o configurazione.
`Code changes: none.`

Ambiente: Windows 11, JDK 17 Temurin 17.0.20.1 (portable), Maven 3.8.6 (wrapper).
Il prompt originale di questa fase è conservato in
[../MOD-004B-verification-prompt.md](../MOD-004B-verification-prompt.md).

---

## 1. Executive Summary

**Verdict: PASS WITH FINDINGS.**

Entrambi i findings di MOD-004 risultano correttamente risolti e verificati dal
codice, dai test e dal diff:

- **Stored-XSS**: i presigned URL degli allegati **non-immagine** trasportano
  `response-content-disposition=attachment` (firmato); le **immagini** restano
  inline. La protezione è applicata sulla via canonica di serving degli allegati
  (`FileMapper → generateSignedUrl(File)`), non aggirabile dalle vie alternative
  (che servono solo artefatti generati dal server o immagini).
- **Lifecycle**: `FileService.delete` rimuove l'oggetto binario (+ thumbnail)
  **prima** dei metadati, in modo idempotente e best-effort, senza toccare
  autorizzazione o isolamento tenant.

Test: **6/6** dedicati, **1445/0/0/0** suite completa, BUILD SUCCESS.
Licensing, multi-tenancy (`CompanyAudit.@PostLoad`), nginx e Docker **non**
modificati da MOD-004B.

I due findings emessi (VF-01 binario orfano su errore storage, VF-02 overload
`String` senza disposition) sono **non bloccanti**, coincidono con le limitazioni
già documentate nell'implementation, non sono regressioni e non costituiscono un
bypass di sicurezza.

---

## 2. Scope

IN scope (verifica): via stored-XSS (`FileMapper → MinioService → presigned URL →
nginx /storage`), lifecycle delete (`DELETE /files/{id} → FileService.delete →
storage → DB`), `StorageService.delete` + implementatori MinIO/GCP,
authorization/multi-tenancy invariate, test mirati + regressione, stato Git.

OUT of scope (non toccati, solo confermati invariati): licensing
(`LicenseService`), `CompanyAudit.@PostLoad`, `nginx.conf`, `docker-compose.yml`,
MOD successivi. Nessuna modifica eseguita in questa fase.

---

## 3. Fonti analizzate

Documentazione:
[CLAUDE.md](../CLAUDE.md), [18-mod004-attachment-audit.md](18-mod004-attachment-audit.md),
[19-mod004b-security-lifecycle-implementation.md](19-mod004b-security-lifecycle-implementation.md).

Codice (letto integralmente al commit corrente):
`service/MinioService.java`, `service/GCPService.java`, `service/StorageService.java`,
`service/FileService.java`, `mapper/FileMapper.java`, `controller/FileController.java`,
`model/abstracts/CompanyAudit.java`, `model/enums/FileType.java`,
`service/WorkOrderService.java` (call site `uploadAndSign`), `nginx.conf`.

Test: `test/.../FileServiceTest.java`, `test/.../MinioServiceTest.java`.

Ricerca call site: tutti gli usi di `generateSignedUrl` / `uploadAndSign` /
`getSignedUrl` nel sorgente `api/src/main`.

---

## 4. Verifica stored-XSS

Percorso ricostruito e confermato dal codice:

```
FileMapper.getSignedUrl(File)                       (FileMapper.java:51-56)
  → StorageService.generateSignedUrl(File, minutes)
  → MinioService.generateSignedUrl(File)            (MinioService.java:169-171)
       responseHeaderOverrides(file)                (MinioService.java:203-209)
  → generateSignedUrl(path, minutes, overrides)     (MinioService.java:177-195)
       builder.extraQueryParams(overrides)          (firmato SigV4)
  → internalUrl.replace(endpoint, publicEndpoint)   (→ /storage)
  → nginx location /storage/                         (nginx.conf:71-86)
```

| Requisito del brief | Esito | Evidenza |
|---|---|---|
| `attachment` solo per `OTHER` | ✅ | `responseHeaderOverrides`: `if (file != null && file.getType() != FileType.IMAGE)` → `response-content-disposition=attachment`; `FileType` = `{IMAGE, OTHER}` (enum a 2 valori) |
| `IMAGE` resta inline | ✅ | per `IMAGE` la mappa è vuota → nessuna override; test `generateSignedUrl_image_staysInline` |
| `response-content-disposition` realmente nella firma | ✅ | `builder.extraQueryParams(extraQueryParams)` (MinioService.java:185); gli extra query param SigV4 sono parte della stringa firmata; test cattura `GetPresignedObjectUrlArgs.extraQueryParams()` e verifica `containsEntry(...)` |
| MinIO supporta l'override nel percorso usato | ✅ (per codice) | `response-content-disposition` è un response-header override S3/MinIO standard sul presigned GET; unit test verifica la presenza nell'arg, non il round-trip live (vedi §11) |
| nginx non lo altera | ✅ | `location /storage/` usa `proxy_pass http://atlas_minio/` di default → query string preservata; `add_header` **aggiunge** header di risposta, non rimuove il `Content-Disposition` upstream di MinIO |
| `nosniff` e `X-Frame-Options: DENY` ancora presenti | ✅ | `nginx.conf:74-75` (`add_header X-Frame-Options "DENY" always;` + `X-Content-Type-Options "nosniff" always;`) — invariati |
| Nessun percorso alternativo che bypassi la protezione | ✅ | vedi analisi call site sotto (VF-02) |

**Analisi call site (no-bypass).** La via che serve gli allegati caricati
dall'utente è **solo** `FileMapper.getSignedUrl(File)` (usata da
`toShowDto`/`toMiniDto`/`toThumbnailDto`, cioè `FileShowDTO.url` ecc.), che usa
l'overload `File` → disposition applicata. Gli altri usi dell'overload `String`
(senza disposition) **non** servono contenuto attivo caricato dall'utente:

- `WorkOrderService.generateReport:1039` → `uploadAndSign` di un **PDF generato dal
  server** (`Work Order Report.pdf`), metodo `@Deprecated`;
- `AsyncExportService` (8 call site) → `uploadAndSign` di **export generati dal
  server** (fogli/CSV/PDF);
- `RequestPortalMapper:46` → logo aziendale (immagine);
- `FileMapper.getThumbnailUrl:74,105` → **thumbnail** (sempre JPG) → inline corretto.

Nessuno di questi è il vettore stored-XSS (contenuto HTML/SVG caricato
dall'utente): quel contenuto passa esclusivamente dalla via `File` protetta.

Difesa in profondità confermata: `Content-Disposition: attachment` (no rendering
top-level dei non-immagine) + `nosniff` (no MIME sniffing) + `X-Frame-Options:
DENY` (no iframe/object).

---

## 5. Verifica lifecycle

Percorso confermato:

```
DELETE /files/{id}                                   (FileController.java:174-188)
  → whoami → findById → canBeDeletedBy(user)         [403/404 se non autorizzato/assente]
  → fileService.delete(id)                           (FileService.java:39-49)
       findById(id).ifPresent:
         deleteStorageObjectQuietly(path)            (storage)
         deleteStorageObjectQuietly(thumbnailPath)   (storage)
       deleteById(id)                                (metadati)
```

| Caso | Comportamento verificato | Evidenza |
|---|---|---|
| Ordine storage → DB | binario, poi thumbnail, poi `deleteById` | test `delete_removesStorageObjectsThenMetadata` con `InOrder(storageService, fileRepository)` |
| Object key | usa `file.getPath()` e `file.getThumbnailPath()` (le key MinIO reali) | `FileService.java:45-46` |
| `path`/`thumbnailPath` null/blank | saltati (no chiamata storage) | `deleteStorageObjectQuietly`: `if (path == null \|\| path.isBlank()) return;` |
| File inesistente | nessuna chiamata storage; `deleteById` comunque eseguito | `findById(...).ifPresent(...)` + `deleteById` fuori dall'`ifPresent`; test `delete_whenFileAbsent_deletesByIdWithoutStorageCall` + `verifyNoInteractions(storageServiceFactory)` |
| Oggetto assente su storage | no-op idempotente | MinIO `removeObject` (commento + semantica S3); GCP `storage.delete` ritorna false; interfaccia documenta l'idempotenza |
| Errore storage reale | loggato (`log.warn`), metadati comunque rimossi | `deleteStorageObjectQuietly` `catch (Exception)`; test `delete_whenStorageFails_stillDeletesMetadata` |
| Thumbnail | rimossa con seconda chiamata `delete` | test verifica `delete("folder/uuid_thumb")` |

**Comportamento documentato "errore storage → log → metadati eliminati"**:
confermato e **non modificato** (come richiesto). Rischio di inconsistenza →
registrato come finding **VF-01** (binario orfano su errore reale). Impatto solo
di igiene storage, nessun impatto di accesso/sicurezza (vedi §10).

---

## 6. Authorization / multi-tenancy

| Requisito | Esito | Evidenza |
|---|---|---|
| `canBeDeletedBy` resta nel controller | ✅ | `FileController.java:182` (a monte di `fileService.delete`) |
| `CompanyAudit.@PostLoad` non modificato | ✅ | letto `model/abstracts/CompanyAudit.java`: check company 403 invariato; **non** presente nel diff Git |
| `FileService.delete` non introduce bypass | ✅ | carica via `findById` (riattiva `@PostLoad`); non tocca ruoli/permessi/company; commento esplicito |
| Company isolation / eccezioni invariate | ✅ | super-admin + super-account relations invariati in `CompanyAudit` |

Nota metodologica: `@PostLoad` è a livello JPA e non è riproducibile in unit test
puro; la verifica è per ispezione del codice (nessuna riga di autorizzazione/tenant
toccata) + assenza dal diff.

---

## 7. StorageService / MinIO / GCP

- **Interfaccia** `StorageService.delete(String filePath)` presente e documentata
  come idempotente (`StorageService.java:42-48`).
- **MinIO** `delete` → `RemoveObjectArgs`/`removeObject`, `checkIfConfigured()`
  prima; su errore lancia `CustomException` (catturata a monte da
  `deleteStorageObjectQuietly`). Idempotente su key assente (semantica S3).
- **GCP** `delete` → `storage.delete(BlobId.of(bucket, filePath))`,
  `checkIfConfigured()` prima; ritorna false (no-op) su oggetto assente.
- Entrambi gli implementatori concreti realizzano il nuovo metodo dell'interfaccia
  → nessun implementatore lasciato astratto (build compila; vedi §8).
- `generateSignedUrl(File)` di MinIO applica gli override; quello di GCP delega
  all'overload `String` (le immagini/loghi GCP restano inline — coerente, e GCP
  non è lo storage self-hosted di default).

---

## 8. Test

Comandi eseguiti (JDK 17 portable, `mvnw.cmd`):

| Comando | Risultato |
|---|---|
| `mvnw test -Dtest=FileServiceTest,MinioServiceTest` | `MinioServiceTest` 3/0/0/0, `FileServiceTest` 3/0/0/0 → **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |
| `mvnw test` (suite completa) | **Tests run: 1445, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

Baseline attesa post-MOD-004B: 1445 → **confermata** (1439 pre-004B + 6). Nessun
test preesistente rotto, nessun test skippato.

Nota: in una prima esecuzione dei soli test mirati in modalità `-q` è comparso uno
stack trace `TimeoutExtension`; **non** si è ripresentato nelle esecuzioni pulite
(6/6 e 1445/1445 con BUILD SUCCESS ed exit code 0). Entrambe le classi di test
usano solo mock Mockito (nessun I/O di rete/MinIO reale), quindi l'evento è
attribuibile a rumore della singola run e non a instabilità dei test.

---

## 9. Git / diff

`git status --short` — modifiche tracciate (cumulate MOD-001/003A/004B) + test e
`docs/` non tracciati. Diff-stat: **184 inserimenti / 13 rimozioni su 10 file**.

File specifici **MOD-004B** (coerenti con l'implementation):

```
mapper/FileMapper.java        |  4 +/-   (overload File)
service/FileService.java      | 22 +     (delete storage+thumb, StorageServiceFactory, @Slf4j)
service/GCPService.java       |  7 +     (delete)
service/MinioService.java     | 58 +     (delete, responseHeaderOverrides, disposition)
service/StorageService.java   |  8 +     (delete nell'interfaccia)
+ test: FileServiceTest.java, MinioServiceTest.java (untracked)
```

Fuori scope MOD-004B, **confermati non modificati da questo modulo**:

- `nginx.conf` — **assente dal diff** (non modificato affatto);
- `CompanyAudit.java` — **assente dal diff**;
- `LicenseService.java` (+59), `application.yml` (+3), `docker-compose.yml` (+1) —
  appartengono a **MOD-001**; `LdapSecurityConfig.java` (+8) e parte di
  `.env.example` a **MOD-003A**; nessuno alterato da MOD-004B.

Nessun commit/push/reset eseguito.

---

## 10. Findings

| ID | Sev. | File / metodo | Evidenza | Impatto | Azione consigliata |
|---|---|---|---|---|---|
| **VF-01** | Low | `FileService.deleteStorageObjectQuietly` / `delete` | `catch (Exception)` → `log.warn` → prosegue con `deleteById`; `MinioService.delete` lancia su errore reale | Su errore storage reale (es. MinIO down) il binario resta mentre il record DB è rimosso → **oggetto orfano**. Nessun impatto di accesso/sicurezza: object key UUID non indovinabile, bucket privato, nessun puntatore DB residuo. Inconsistenza di sola igiene storage, non di integrità dati né di sicurezza. | **Accettabile as-is** (best-effort, già documentato). Opzionale futuro: metrica/alert sul `warn` o job di riconciliazione. **Non** modificare in verifica. |
| **VF-02** | Info | `StorageService.uploadAndSign` (default); `WorkOrderService.generateReport:1039`; `AsyncExportService` (×8); `RequestPortalMapper:46`; `FileMapper.getThumbnailUrl` | usano l'overload `generateSignedUrl(String, long)` (override vuote) | Nessun `Content-Disposition: attachment` su questi URL, ma servono **artefatti generati dal server** (report/export PDF/CSV) o **immagini** (logo/thumbnail), non HTML/SVG caricato dall'utente → **non** è il vettore stored-XSS. La via allegati-utente è protetta. | Nessuna azione per MOD-004B. Rivalutare solo se in futuro un export potrà contenere HTML controllato dall'utente. |

Nessun finding di severità Media/Alta. Nessun finding bloccante. Nessuna
regressione. Nessun bypass di autorizzazione/tenant/licensing.

---

## 11. Limitazioni della verifica

- La mitigazione dipende, a runtime, dal fatto che **MinIO onori**
  `response-content-disposition` (comportamento standard S3/MinIO) e che il proxy
  `/storage` **inoltri** i query param firmati senza rimuovere il
  `Content-Disposition` upstream. Verificato per **ispezione** di codice e
  `nginx.conf`, **non** con un round-trip live contro un MinIO reale (gli unit
  test usano mock).
- `@PostLoad` (isolamento tenant) è JPA-level: verificato per ispezione, non
  esercitato dagli unit test puri.
- Test end-to-end degli allegati (Testcontainers + MinIO reale:
  upload→download→disposition→delete) restano un'estensione possibile (MOD-004d),
  non richiesta da MOD-004B.
- La verifica non ha eseguito `docker compose config`: MOD-004B non tocca
  `docker-compose.yml`/`nginx.conf` (confermato dal diff).

---

## 12. Verdict

**PASS WITH FINDINGS.**

Implementazione MOD-004B verificata e conforme al brief: stored-XSS mitigato sulla
via canonica degli allegati (disposition attachment per non-immagine, immagini
inline, `nosniff` + `X-Frame-Options: DENY` presenti), lifecycle di delete
corretto (storage→DB, idempotente, best-effort), autorizzazione e multi-tenancy
invariate, test 6/6 e suite 1445/0/0/0 verdi, diff limitato allo scope MOD-004B.

I due findings (VF-01 Low, VF-02 Info) sono **non bloccanti**, coincidono con le
limitazioni già dichiarate nell'implementation, non introducono regressioni né
bypass di sicurezza.

⏹️ **STOP** — non procedo a MOD-005 e non applico correzioni automatiche. La
decisione (approvare / trattare VF-01/VF-02 / estendere con MOD-004d) spetta al
responsabile tecnico.

`Code changes: none.`
