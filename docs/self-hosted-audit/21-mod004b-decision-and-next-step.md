# 21 — MOD-004B — Decisione tecnica e prossimo passo

## Decisione

La verifica indipendente di MOD-004B è:

**PASS WITH FINDINGS**

L'implementazione è approvata per il perimetro attuale. Sono stati verificati:
- stored-XSS mitigato sulla via canonica degli allegati;
- lifecycle storage → DB;
- autorizzazione e multi-tenancy invariati;
- 6/6 test dedicati;
- 1445/1445 test complessivi;
- nessuna modifica a licensing, nginx o Docker attribuibile a MOD-004B.

Fonte primaria:
`docs/self-hosted-audit/20-mod004b-verification.md`

## Findings accettati

### VF-01 — Low
Su errore reale dello storage, il codice registra il warning e procede alla cancellazione dei metadati DB. Può quindi rimanere un oggetto orfano.

**Decisione:** non modificare ora. È una scelta best-effort già documentata. Eventuali metriche, retry o job di riconciliazione saranno valutati separatamente.

### VF-02 — Info
L'overload `generateSignedUrl(String, long)` non applica `Content-Disposition: attachment`, ma i call site verificati servono report/export generati dal server, logo o thumbnail.

**Decisione:** nessuna modifica.

---

# MOD-004C — End-to-End Attachment Security Verification

MOD-004B è funzionalmente approvato. Prima di MOD-005 eseguire una verifica runtime end-to-end, senza modificare il codice.

## Obiettivo

Dimostrare che il comportamento verificato con codice e unit test funziona anche attraverso:

```text
upload
→ DB metadata
→ MinIO
→ presigned URL
→ nginx /storage
→ HTTP response
```

e:

```text
delete
→ object storage
→ thumbnail
→ DB metadata
```

## Verifiche

### 1. File non-image
Verificare upload, object MinIO, URL firmato e risposta HTTP.

Controllare:
- `Content-Disposition: attachment`;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`.

Usare un file innocuo, senza payload XSS reale.

### 2. Immagine
Verificare che una JPG/PNG rimanga visualizzabile inline e non venga forzata ad `attachment`.

### 3. Firma
Verificare che `response-content-disposition` sia realmente parte della firma e non possa essere modificato arbitrariamente nella query string.

### 4. nginx
Verificare `/storage` e confermare che:
- i query parameter arrivino a MinIO;
- `Content-Disposition` upstream arrivi al client;
- nginx non lo rimuova;
- gli header di sicurezza restino presenti.

### 5. Delete
Prima del delete verificare:
```text
DB record = presente
MinIO object = presente
thumbnail = presente, se prevista
```

Dopo:
```text
DB record = assente
MinIO object = assente
thumbnail = assente
```

### 6. Idempotenza
Verificare il comportamento quando l'object storage non contiene già l'object.

### 7. Errore storage
Se simulabile in sicurezza, verificare il comportamento documentato:
```text
storage error → log warning → metadata DB eliminati
```
Non correggere il comportamento.

### 8. Browser
Se possibile:
- non-image → download;
- image → visualizzazione inline.

### 9. Regressione
Eseguire, se l'ambiente lo consente:
```bash
mvnw test -Dtest=FileServiceTest,MinioServiceTest
mvnw test
```

Confrontare con la baseline:
```text
1445 tests
0 failures
0 errors
0 skipped
```

## Regole operative

Questa fase è **solo verifica**.

Non modificare:
- FileMapper;
- FileService;
- MinioService;
- GCPService;
- StorageService;
- nginx;
- Docker Compose;
- licensing;
- CompanyAudit;
- autenticazione;
- multi-tenancy.

Se emerge un problema:
1. documentarlo;
2. fornire riproduzione ed evidenza;
3. indicare impatto;
4. non correggerlo automaticamente;
5. fermarsi.

## Output

Creare:

```text
docs/self-hosted-audit/21-mod004c-e2e-verification.md
```

con:
1. Executive Summary
2. Scope
3. Environment
4. Upload non-image
5. Upload image
6. Presigned URL/signature
7. nginx `/storage`
8. Delete lifecycle
9. Idempotency
10. Storage failure
11. Browser verification
12. Regression tests
13. Findings
14. Limitations
15. Verdict

Verdict ammessi:
- PASS
- PASS WITH FINDINGS
- FAIL

## STOP

Al termine **non procedere automaticamente a MOD-005**.

Restituire il verification report. La decisione successiva verrà presa dopo la sua analisi.

Non inventare requisiti o comportamenti non documentati.
