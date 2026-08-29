# 20 — MOD-004B — Verifica finale

## Obiettivo
Verificare indipendentemente l'implementazione descritta in `docs/self-hosted-audit/19-mod004b-security-lifecycle-implementation.md`.

**Questa fase è solo verifica: non modificare codice, test o configurazione.** In caso di problema, documentarlo e fermarsi.

## Fonti
Leggere prima:
1. `CLAUDE.md`
2. `docs/self-hosted-audit/18-mod004-attachment-audit.md`
3. `docs/self-hosted-audit/19-mod004b-security-lifecycle-implementation.md`

Poi leggere solo il codice direttamente coinvolto.

## Verifiche obbligatorie

### 1. Stored-XSS
Ricostruire il percorso `FileMapper → generateSignedUrl(File) → MinioService → presigned URL → nginx /storage`.

Confermare:
- `attachment` solo per `OTHER`;
- `IMAGE` resta inline;
- `response-content-disposition` è realmente incluso nella firma;
- MinIO lo supporta nel percorso utilizzato;
- nginx non lo altera;
- `nosniff` e `X-Frame-Options: DENY` sono ancora presenti;
- non esiste un percorso alternativo che bypassi la protezione.

### 2. Lifecycle
Verificare il percorso:
`DELETE /files/{id} → autorizzazione → FileService.delete → storage(path) → storage(thumbnail) → DB`.

Controllare ordine, object key, null/blank, file inesistente, oggetto assente, errore storage e thumbnail.

Prestare particolare attenzione al comportamento documentato: **errore storage → log → metadati DB comunque eliminati**. Non modificarlo durante questa verifica; se crea un rischio di inconsistenza, segnalarlo come finding.

### 3. Authorization / multi-tenancy
Confermare che:
- `canBeDeletedBy` resta nel controller;
- `CompanyAudit.@PostLoad` non è stato modificato;
- `FileService.delete` non introduce bypass;
- company isolation e le eccezioni già previste restano invariate.

### 4. StorageService
Verificare `StorageService.delete(String path)` e tutti i suoi implementatori/call site, in particolare MinIO e GCP.

### 5. Test
Eseguire:
```bash
mvnw test -Dtest=FileServiceTest,MinioServiceTest
mvnw test
```
Riportare Tests run, Failures, Errors, Skipped e BUILD SUCCESS/FAILURE. Se un comando non è eseguibile, dichiararlo senza assegnare PASS.

### 6. Git
Controllare:
```bash
git status
git diff --stat
git diff
```
Confermare che le modifiche siano limitate a MOD-004B e che non siano stati modificati licensing, multi-tenancy, Docker, nginx o MOD successivi.

Non fare commit, push, reset o operazioni distruttive.

## Verdict

Usare una sola classificazione:

- **PASS** — implementazione verificata e conforme.
- **PASS WITH FINDINGS** — funzionalmente corretta con problemi residui non bloccanti.
- **FAIL** — correzione non funzionante, regressione o violazione di decisioni architetturali.

Per ogni finding indicare ID, severità, file/metodo, evidenza, impatto e azione consigliata.

## Output obbligatorio

Creare:
`docs/self-hosted-audit/20-mod004b-verification.md`

Sezioni:
1. Executive Summary
2. Scope
3. Fonti analizzate
4. Verifica stored-XSS
5. Verifica lifecycle
6. Authorization/multi-tenancy
7. StorageService/MinIO/GCP
8. Test
9. Git/diff
10. Findings
11. Limitazioni
12. Verdict

Non duplicare l'intera implementation.

## Anti-hallucination

Il documento di implementation è contesto, non prova. Confermare le affermazioni tramite codice, test, configurazione e diff secondo necessità. Se una verifica non è possibile, dichiararlo esplicitamente.

## STOP

Non procedere a MOD-005 e non correggere automaticamente eventuali problemi.

`Code changes: none.`
