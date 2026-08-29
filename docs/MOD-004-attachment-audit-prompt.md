# 18 — MOD-004 Audit Instructions
## File Attachments / Storage / MinIO

**Project:** Atlas CMMS — self-hosted fork  
**Module:** MOD-004  
**Phase:** Audit + verification + implementation planning ONLY  
**Status:** NOT APPROVED FOR CODE MODIFICATION  
**Previous module:** MOD-003A — PASS  
**Date:** 2026-08-26

## 1. Role

You are the coding agent responsible for analysing the current Atlas CMMS repository and producing the technical evidence required for the next decision.

Do **not** implement MOD-004 in this task. The technical owner will review the report and decide whether implementation or only configuration is required.

Treat the current checkout and the latest project documentation as authoritative.

## 2. Objective

Determine whether Atlas CMMS file attachments are already fully implemented and whether they can be used in the self-hosted deployment without further licensing/code changes.

Trace the complete path:

```text
Frontend → API → Controller → Service → Attachment model
        → Storage abstraction → MinIO/filesystem
        → PostgreSQL metadata
```

Determine:

1. whether attachment functionality exists end-to-end;
2. whether it is protected by `FILE_ATTACHMENTS` or another entitlement;
3. whether MOD-001 already makes the relevant entitlement available in self-hosted mode;
4. whether backend or frontend contains additional gates;
5. whether MinIO is correctly integrated;
6. where metadata and binary data are stored;
7. how upload/download/delete authorization works;
8. whether company/tenant isolation is preserved;
9. file size/type/security restrictions;
10. attachment relationships;
11. existing test coverage;
12. exact self-hosted deployment configuration;
13. whether code modification is actually necessary.

## 3. Mandatory source hierarchy

Read first:

```text
CLAUDE.md
docs/self-hosted-audit/04-feature-matrix.md
docs/self-hosted-audit/06-storage-attachments.md
docs/self-hosted-audit/11-modification-plan.md
docs/self-hosted-audit/12-test-plan.md
docs/self-hosted-audit/13-mod001-implementation.md
docs/self-hosted-audit/14-mod001-verification.md
docs/self-hosted-audit/15-mod002-verification.md
docs/self-hosted-audit/17-mod003a-implementation.md
```

If a newer audit document exists, use it as the primary source.

If documentation and code disagree:

1. document the discrepancy;
2. determine actual behavior from code/tests;
3. do not silently modify anything;
4. report the impact.

## 4. Scope

### IN SCOPE

- attachment implementation;
- licensing gates;
- MinIO/storage configuration;
- database metadata;
- upload/download/delete;
- authorization;
- company/tenant isolation;
- file validation;
- size limits;
- attachment relationships;
- frontend gates;
- existing tests;
- Docker configuration;
- self-hosted configuration;
- security analysis;
- implementation plan if changes are required.

### OUT OF SCOPE

Do NOT:

- modify licensing architecture;
- change `LicenseService`, `LicensingState` or `hasEntitlement()`;
- alter `FILE_ATTACHMENTS` semantics;
- redesign MinIO;
- migrate the database;
- change production Docker configuration;
- modify unrelated modules;
- implement MOD-005 or MOD-006;
- refactor unrelated code;
- add dependencies merely for convenience.

**Do not modify code during this audit.**

## 5. Repository state

Run:

```bash
git status --short
git branch --show-current
git log -5 --oneline --decorate
```

Do not clean, reset, checkout, commit or push anything. Preserve all local changes.

## 6. Licensing audit

Search the attachment path for:

```text
FILE_ATTACHMENTS
hasEntitlement(
hasLicense(
LicenseService
LicensingState
LicenseEntitlement
PlanFeatures
```

Also search indirect feature checks.

Produce:

| Feature | Gate | Backend | Frontend | Self-hosted state | Action |
|---|---|---|---|---|---|
| Upload | ... | ... | ... | ... | ... |
| Download | ... | ... | ... | ... | ... |
| Delete | ... | ... | ... | ... | ... |
| Listing | ... | ... | ... | ... | ... |
| Preview | ... | ... | ... | ... | ... |

Do not assume an entitlement means the feature is blocked. Follow:

```text
entitlement → licensing state → backend gate → frontend gate → feature
```

If MOD-001 already enables the entitlement, demonstrate this from current code/tests rather than changing licensing.

## 7. Backend attachment audit

Locate all attachment-related entities, repositories, services, controllers and DTOs.

Search:

```text
Attachment
attachments
File
files
upload
download
delete
storage
StorageService
Minio
S3
bucket
object
multipart
```

For important components record:

```text
file
class
method
responsibility
authorization check
company/tenant check
licensing check
storage interaction
database interaction
```

Reconstruct actual flows for upload, download and delete.

### Upload

```text
request → controller → validation → authorization
→ service → metadata persistence → binary storage
```

### Download

```text
request → authorization → metadata lookup
→ storage lookup → response
```

### Delete

```text
request → authorization → metadata lookup
→ binary deletion → metadata deletion/soft delete
```

If reality differs, document the real flow.

## 8. Database audit

Identify:

- attachment entity/model and table;
- primary/foreign keys;
- company/tenant relation;
- linked entity;
- filename/original filename;
- MIME type;
- size;
- storage key/path;
- timestamps;
- uploader/owner;
- soft-delete state.

Determine whether binary data is stored in PostgreSQL or externally.

Verify, rather than assume:

```text
PostgreSQL → metadata
MinIO → binary object
```

Do not modify migrations/schema.

## 9. MinIO/storage audit

Inspect actual application and Compose configuration.

Verify the real names and usage of:

```text
STORAGE_TYPE
MINIO_ENDPOINT
MINIO_BUCKET
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
```

Determine:

- whether MinIO is mandatory or optional;
- filesystem support;
- bucket initialization;
- API/frontend access path;
- signed URLs;
- authorization before download;
- whether MinIO is externally exposed;
- credential handling;
- persistent volume usage.

Do not modify Compose.

## 10. File validation and security

Determine from code:

- maximum file size;
- allowed types/extensions;
- MIME validation;
- filename sanitization;
- path traversal protection;
- object-key generation;
- overwrite behavior;
- executable/archive/SVG handling;
- download authorization;
- delete authorization;
- cross-company object isolation.

Pay particular attention to:

```text
../
absolute paths
user-controlled object keys
client-controlled Content-Type
direct storage URLs
cross-company access
```

Report missing controls; do not fix them during this audit.

## 11. Authorization and company isolation

Trace:

```text
current user
↓
company
↓
parent entity
↓
attachment
↓
storage object
```

Determine the actual authorization mechanism.

Verify whether a user from company A could access an attachment belonging to company B.

Do not assume database relationships alone enforce authorization.

## 12. Frontend audit

Find attachment UI and determine:

- upload/download controls;
- entitlement checks;
- license-state usage;
- client-side size/type checks;
- error handling;
- whether controls are hidden or disabled.

Do not change frontend code.

## 13. Tests

Search existing tests for:

```text
Attachment
File
Minio
Storage
FILE_ATTACHMENTS
upload
download
delete
```

Classify unit/integration/controller/security/storage tests.

Do not create a large suite during the audit. Produce a missing-coverage plan covering, where applicable:

```text
upload allowed/rejected
download authorized/unauthorized
delete authorized/unauthorized
company isolation
missing object
invalid file
oversized file
storage failure
entitlement enabled/disabled
```

If existing Testcontainers/MinIO test infrastructure exists, identify it.

## 14. Docker/deployment

Inspect:

```text
docker-compose.yml
.env.example
application.yml
storage configuration
MinIO configuration
```

Determine exact self-hosted configuration, including:

- persistent MinIO volume;
- bucket;
- credentials;
- internal/external endpoint;
- API → MinIO connectivity;
- whether MinIO is published;
- secret exposure.

Do not change production configuration.

## 15. Classification

Classify every attachment capability as:

```text
A — implemented and self-hosted available
B — implemented but licensing-gated and expected to be solved by MOD-001
C — implemented but requires deployment configuration
D — partially implemented; code change required
E — not implemented
F — external dependency required
G — security issue requiring separate decision
```

Do not classify something as B merely because a commercial entitlement exists.

## 16. Modification decision

If:

```text
implementation complete
+
self-hosted entitlement active
+
storage configuration sufficient
+
authorization correct
```

recommend:

```text
MOD-004 = configuration + verification only
```

If a code change is required, specify:

```text
exact file
exact class
exact method
current behavior
desired behavior
risk
tests
rollback
```

Do not implement it yet.

If there is a security/architecture problem, stop and classify it as a decision requiring approval.

## 17. Required output

Create:

```text
docs/self-hosted-audit/18-mod004-attachment-audit.md
```

Use:

```markdown
# 18 — MOD-004 Attachment / Storage Audit
## Executive Summary
## Audit Scope
## Current Architecture
## Licensing Analysis
## Backend Attachment Flow
## Frontend Attachment Flow
## Database / Metadata
## MinIO / Storage
## Upload
## Download
## Delete
## Authorization
## Company / Tenant Isolation
## File Validation and Security
## Existing Tests
## Missing Test Coverage
## Docker / Deployment Configuration
## Feature Classification
## Findings
## Required Configuration
## Required Code Changes
## Risks
## Recommendation
## Decision Gate
```

Use exact file/class/method references wherever possible. Avoid vague conclusions.

## 18. Completion criteria

The report must answer:

1. Where is attachment functionality implemented?
2. What database records represent attachments?
3. Where are binary files stored?
4. How does Atlas communicate with MinIO?
5. What entitlement controls attachments?
6. Does self-hosted licensing enable it?
7. Is there a second backend/frontend gate?
8. Can users access only authorized attachments?
9. Is company/tenant isolation enforced?
10. What are size/type restrictions?
11. What tests exist?
12. What tests are missing?
13. What Docker/env configuration is required?
14. Is code modification necessary?
15. If yes, exactly what should change?

## 19. Final decision gate

**STOP after the audit.**

Do not implement MOD-004, modify licensing, modify MinIO/database/frontend/backend/Docker, or proceed to MOD-005.

The technical owner will review:

```text
docs/self-hosted-audit/18-mod004-attachment-audit.md
```

and decide whether MOD-004 requires configuration, implementation, security review, or no further action.

## 20. Context management

Do not load the whole repository.

Use:

```text
CLAUDE.md
↓
04-feature-matrix
↓
06-storage-attachments
↓
11-modification-plan
↓
licensing docs
↓
attachment source search
↓
tests
↓
Docker/storage configuration
```

Do not inspect LDAP, email, PM, work orders or asset-management internals unless an attachment dependency requires it.

## 21. Anti-hallucination

MUST NOT invent:

- APIs;
- storage behavior;
- database relationships;
- entitlement behavior;
- security guarantees;
- file restrictions;
- MinIO configuration;
- frontend behavior.

Use:

```text
documentation → code → tests → configuration → report as unverified
```

Never guess.

## 22. Project rule

The self-hosted licensing architecture is approved:

```text
LicenseService → LicensingState → hasEntitlement()
```

Do not replace it with service-level bypasses.

If `FILE_ATTACHMENTS` is already enabled by the existing self-hosted entitlement policy, preserve that architecture.

MOD-004 exists to determine the actual attachment/storage state, not to redesign licensing.
