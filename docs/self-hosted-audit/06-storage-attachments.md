# 06 — Storage & Allegati

Deployment aziendale: **MinIO**.

---

## 1. Flusso upload/download

```
Frontend upload
   ↓
POST /files/upload  (FileController)
   ↓  gate: hasEntitlement(FILE_ATTACHMENTS)  +  PlanFeatures.FILE  +  permesso FILES
   ↓
StorageServiceFactory.getStorageService()  →  MinioService | GCPService
   ↓
MinIO bucket  (o GCP bucket)
   ↓
File entity (metadata) salvata via FileService  →  PostgreSQL
   ↓
download: generateSignedUrl / download()  →  Frontend
```

---

## 2. Il gate (doppio)

[`FileController.java:69`](../../api/src/main/java/com/grash/controller/FileController.java#L69):

```java
if (!licenseService.hasEntitlement(LicenseEntitlement.FILE_ATTACHMENTS))
    throw new CustomException("You need a license to add a file", HttpStatus.FORBIDDEN);
```

E, più sotto ([:77](../../api/src/main/java/com/grash/controller/FileController.java#L77)):

```java
if (isBypass || (user.getRole().getCreatePermissions().contains(PermissionEntity.FILES) &&
        user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.FILE))) { ... }
```

- **Livello A** `FILE_ATTACHMENTS`: bloccante in self-hosted senza licenza.
- **Livello B** `PlanFeatures.FILE`: soddisfatto (BUSINESS).
- **Permesso ruolo** `PermissionEntity.FILES`: gestito dai ruoli, non dalla licenza.

C'è anche un `bypass` (parametro nascosto) e un **rate limiter**
(`rateLimiterService.tryConsumeFileUpload`). Esiste un endpoint upload dedicato
al **request portal** (`/upload/request-portal/{uuid}`).

---

## 3. Storage backend (config-driven, non licenza)

- Enum: [`model/enums/StorageType.java`](../../api/src/main/java/com/grash/model/enums/StorageType.java) → `GCP`, `MINIO`.
- Factory: [`factory/StorageServiceFactory.java`](../../api/src/main/java/com/grash/factory/StorageServiceFactory.java) — sceglie in base a `storage.type` (default → MinIO).
- Implementazioni: [`service/MinioService.java`](../../api/src/main/java/com/grash/service/MinioService.java), [`service/GCPService.java`](../../api/src/main/java/com/grash/service/GCPService.java) (interfaccia [`StorageService.java`](../../api/src/main/java/com/grash/service/StorageService.java)).

`MinioService` gestisce: creazione bucket se assente, `upload` (MultipartFile /
byte[] / uploadAt), `exists`, `download`, `generateSignedUrl` (URL firmati con
scadenza), delete.

### Config MinIO (application.yml:134-141)

| Proprietà | Env |
|---|---|
| `storage.type` | `STORAGE_TYPE` (→ `MINIO`) |
| `storage.minio.endpoint` | `MINIO_ENDPOINT` |
| `storage.minio.bucket` | `MINIO_BUCKET` |
| `storage.minio.access-key` | `MINIO_ACCESS_KEY` |
| `storage.minio.secret-key` | `MINIO_SECRET_KEY` |
| `storage.minio.public-endpoint` | `PUBLIC_MINIO_ENDPOINT` |

> **Nessun gate licenza sullo storage backend**: la scelta MinIO/GCP e le
> credenziali sono pura configurazione. `docker-compose.yml` in root include il
> servizio MinIO.

---

## 4. Metadati e associazioni

- Entity `File` (metadata: nome, path, `FileType`, `hidden`, task associata).
- Mapper `FileMapper`, service `FileService`.
- Associazione con asset/work order/task tramite i rispettivi service.

---

## 5. Classificazione

🟡 **UNLOCK_PLUS_MODIFICATION** per il gate `FILE_ATTACHMENTS` (sbloccabile a
livello entitlement), con **dipendenza esterna** = storage MinIO/GCP/filesystem
configurato. Lo storage backend in sé è ⚪ già disponibile (config). Il gate
`PlanFeatures.FILE` è già soddisfatto in self-hosted.
