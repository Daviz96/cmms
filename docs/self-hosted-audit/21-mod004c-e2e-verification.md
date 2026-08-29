# 21 — MOD-004C — Verifica End-to-End runtime (allegati / storage / nginx)

Verifica **runtime, solo lettura** del comportamento di MOD-004B attraverso lo
stack di storage reale. Nessuna modifica a codice, test o configurazione del repo.
`Code changes: none.`

Contesto/decisione a monte: [21 — decisione MOD-004B](21-mod004b-decision-and-next-step.md)
(PASS WITH FINDINGS, VF-01/VF-02 accettati). Fonti verificate:
[19-mod004b-security-lifecycle-implementation.md](19-mod004b-security-lifecycle-implementation.md),
[20-mod004b-verification.md](20-mod004b-verification.md).

---

## 1. Executive Summary

**Verdict: PASS.**

La verifica runtime chiude la **limitazione dichiarata nel doc 20** (la mitigazione
dipendeva, a runtime, dal fatto che MinIO onorasse `response-content-disposition`
firmato e che nginx `/storage` inoltrasse i query param firmati senza rimuovere il
`Content-Disposition` upstream — non esercitato dagli unit test con mock).

Contro **MinIO reale** (stessa immagine del compose) e **nginx reale** (montando il
`nginx.conf` del repo), con URL firmati generati dalla **stessa libreria
`io.minio` 8.6.0** e dallo **stesso codepath** di `MinioService`:

- allegato **non-immagine** → `Content-Disposition: attachment` (download), sia
  diretto su MinIO sia attraverso nginx;
- **immagine** → nessuna disposition (resta inline);
- `response-content-disposition` è **legato alla firma** SigV4: manometterlo →
  **403 SignatureDoesNotMatch**;
- nginx `/storage` inoltra i param firmati, **non rimuove** il `Content-Disposition`
  upstream e aggiunge `X-Content-Type-Options: nosniff` + `X-Frame-Options: DENY`;
- delete storage: oggetto presente → `removeObject` → assente (HTTP 404);
  **idempotente** su chiave già assente / mai esistita (nessuna eccezione).

Regressione: **targeted 6/6**, **suite completa 1445/0/0/0**, BUILD SUCCESS.

Nessun finding bloccante. Un'osservazione informativa (O-01: `nosniff` duplicato,
innocuo) e i findings ereditati VF-01/VF-02 (non bloccanti) restano invariati.

---

## 2. Scope

IN scope (runtime, read-only): comportamento HTTP dei presigned URL degli allegati
(disposition per tipo), binding della firma, pass-through/aggiunte di header a
livello nginx `/storage`, semantica di delete/idempotenza a livello object storage,
regressione unit.

OUT of scope (non eseguiti live — vedi §14): orchestrazione applicativa
`FileController → FileService` con auth+DB reali (l'immagine `api` del compose è la
build upstream prebuilt, **priva** delle modifiche MOD-004B); percorso GCP;
navigazione browser reale.

Nessuna modifica a: FileMapper, FileService, MinioService, GCPService,
StorageService, nginx.conf, docker-compose.yml, licensing, CompanyAudit,
autenticazione, multi-tenancy. L'harness runtime vive **fuori dal repo** (scratchpad)
→ `git status` del repo invariato (solo `docs/`).

---

## 3. Environment

| Componente | Valore |
|---|---|
| OS / Docker | Windows 11, Docker 29.4.3 |
| MinIO | `minio/minio:RELEASE.2025-04-22T22-12-26Z` (**identica** al compose), bucket `atlas-bucket`, pubblicato su `localhost:19000` + alias di rete `minio` |
| nginx | `nginx:1.27-alpine` (identica al compose) con **`./nginx.conf` reale** montato `:ro` su `/etc/nginx/conf.d/default.conf`, pubblicato su `localhost:18080` |
| Risoluzione upstream | container busybox con alias `frontend`+`api` (solo per far partire nginx; esercitato **solo** `/storage`) |
| Firma URL | harness Java standalone con **`io.minio` 8.6.0** (stessa versione dell'app, dal classpath Maven del progetto), che replica `MinioService.responseHeaderOverrides(File)` + `generateSignedUrl(File)` incluso il replace endpoint→public-endpoint |
| JDK / build | Temurin 17.0.20.1 (portable), `mvnw.cmd` (Maven 3.8.6) |

Oggetti seed: `mod004c/report.html` (`text/html`, rappresenta `FileType.OTHER`),
`mod004c/pixel.png` (`image/png`, rappresenta `FileType.IMAGE`). `FileType` reale =
`{IMAGE, OTHER}` → entrambi i rami coperti. Payload innocuo (nessun XSS reale).

`nginx -t` sul config reale: *syntax is ok / test is successful*.

---

## 4. Upload non-image

Object `mod004c/report.html` caricato in MinIO via `putObject` (SDK `io.minio`),
`contentType=text/html`. Presigned GET generato con override
`response-content-disposition=attachment` (ramo `OTHER` di
`responseHeaderOverrides`). Verifica diretta su MinIO (`localhost:19000`):

```
HTTP/1.1 200 OK
Content-Type: text/html
Content-Disposition: attachment
```

✅ Il non-immagine è servito con `Content-Disposition: attachment` → il browser
scarica invece di renderizzare inline (HTML non eseguito su navigazione top-level).

---

## 5. Upload image

Object `mod004c/pixel.png` (`image/png`). Presigned GET generato **senza** override
(ramo `IMAGE`). Verifica diretta su MinIO:

```
HTTP/1.1 200 OK
Content-Type: image/png
(nessun header Content-Disposition)
```

✅ L'immagine resta inline → la preview continua a funzionare. Comportamento
per-tipo confermato coerente con l'enum `FileType`.

---

## 6. Presigned URL / signature

- **Inclusione nella firma**: l'URL del non-immagine contiene
  `response-content-disposition=attachment` **prima** dei parametri
  `X-Amz-*` e la firma è calcolata su quel canonical query string (SigV4,
  `X-Amz-SignedHeaders=host`, scope `.../us-east-1/s3/aws4_request`, `X-Amz-Expires=10800`
  = 180 min, come l'app).
- **Binding / anti-manomissione**: partendo dall'URL dell'immagine (firmato, senza
  disposition) e **aggiungendo** `&response-content-disposition=attachment` non
  firmato:

```
HTTP_STATUS=403
<Error><Code>SignatureDoesNotMatch</Code>...<Key>mod004c/pixel.png</Key>...
```

✅ `response-content-disposition` non è modificabile arbitrariamente nella query
string: qualsiasi alterazione dei parametri invalida la firma → 403. La decisione
attachment/inline è quindi **inchiodata lato server** al momento della firma
(backend), non manipolabile dal client.

---

## 7. nginx `/storage`

Stessi due object serviti **attraverso nginx reale** (`localhost:18080/storage/...`),
con URL firmato per l'host `minio:9000` e trasformato in `/storage` esattamente come
fa l'app (`internalUrl.replace(minioEndpoint, minioPublicEndpoint)`). nginx
`proxy_pass http://atlas_minio/` con `proxy_set_header Host minio:9000`.

Non-immagine via nginx:

```
HTTP/1.1 200 OK
Content-Type: text/html
Content-Disposition: attachment          ← upstream MinIO, non rimosso da nginx
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
```

Immagine via nginx:

```
HTTP/1.1 200 OK
Content-Type: image/png
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
(nessun Content-Disposition)
```

Confermato:
- ✅ i **query parameter firmati arrivano a MinIO** (la disposition ha effetto anche
  attraverso il proxy → la firma sopravvive al proxy_pass, prefix `/storage/`
  rimosso, path e Host coerenti con la firma);
- ✅ nginx **non rimuove** il `Content-Disposition` upstream;
- ✅ `X-Content-Type-Options: nosniff` e `X-Frame-Options: DENY` presenti
  (`nginx.conf:74-75`), invariati;
- difesa in profondità completa: attachment (non-immagine) + nosniff + X-Frame-Options.

Osservazione **O-01** (informativa): `X-Content-Type-Options: nosniff` compare
**due volte** nella risposta via nginx (MinIO lo emette di suo e nginx lo ri-aggiunge
con `add_header`). Valore identico → innocuo; nessuna azione. `X-Frame-Options: DENY`
compare una sola volta (solo nginx).

---

## 8. Delete lifecycle

Semantica `StorageService.delete` verificata a livello object storage con `io.minio`
(stessa `removeObject` di `MinioService.delete`):

```
BEFORE_PRESENT=true      (statObject: oggetto presente)
removeObject(report.html)
AFTER_PRESENT=false       (statObject: oggetto assente)
```

Conferma HTTP dopo la delete (presigned URL dello stesso object):

```
HTTP_STATUS=404
```

✅ L'oggetto binario viene effettivamente rimosso dallo storage. Coerente con
`FileService.delete` che rimuove lo storage (path + thumbnail) prima dei metadati.

---

## 9. Idempotency

```
SECOND_DELETE=NO_EXCEPTION           (removeObject su chiave già rimossa)
DELETE_NEVER_EXISTED=NO_EXCEPTION    (removeObject su chiave mai esistita)
```

✅ `removeObject` è idempotente: cancellare un oggetto assente **non lancia**. Questo
è esattamente ciò che `FileService.deleteStorageObjectQuietly` e gli unit test
assumono (nessun errore su path già assente); la cancellazione dei metadati non
verrebbe comunque bloccata (best-effort).

---

## 10. Storage failure

Il comportamento documentato *(errore storage reale → `log.warn` → metadati DB
comunque eliminati)* **non** è stato indotto live contro l'applicazione (richiederebbe
l'app reale + un guasto MinIO simulato durante una delete autenticata). È coperto
dall'unit test `FileServiceTest.delete_whenStorageFails_stillDeletesMetadata`
(doc 19/20), che verifica il best-effort con `doThrow` sullo storage. **Non
modificato** (come richiesto). Rischio residuo = VF-01 (binario orfano su errore
reale), già accettato nel doc 21 (decisione).

---

## 11. Browser verification

Nessun browser disponibile in ambiente. I comandi `curl -D` mostrano gli **header
esatti** che pilotano il comportamento del browser:

- non-immagine → `Content-Disposition: attachment` ⇒ il browser **scarica**;
- immagine → nessuna disposition ⇒ **visualizzazione inline**.

Il comportamento del browser è determinato da questi header di risposta (più
`nosniff` che impedisce il MIME sniffing), tutti verificati sopra. Verifica visuale
in un browser reale resta un passo manuale opzionale.

---

## 12. Regression tests

| Comando | Risultato |
|---|---|
| `mvnw test -Dtest=FileServiceTest,MinioServiceTest` | **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |
| `mvnw test` (suite completa) | **Tests run: 1445, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

Baseline attesa 1445 → **confermata**. Nessuna regressione.

---

## 13. Findings

| ID | Sev. | Area | Evidenza | Impatto | Azione |
|---|---|---|---|---|---|
| **O-01** | Info | nginx `/storage` | `X-Content-Type-Options: nosniff` duplicato (MinIO + `add_header` nginx) | Nessuno: valore identico, direttiva applicata | Nessuna. Eventuale `proxy_hide_header`/dedup sarebbe cosmetico e fuori scope (non modificare nginx). |
| **VF-01** | Low | `FileService` delete | (ereditato doc 20/21) errore storage reale → binario orfano | Igiene storage, nessun impatto accesso/sicurezza | Accettato/documentato. Invariato. |
| **VF-02** | Info | overload `String` signed URL | (ereditato doc 20) report/export/logo/thumbnail senza disposition | Non è il vettore stored-XSS (contenuto server-generato/immagini) | Nessuna. Invariato. |

Nessun finding di severità Media/Alta. Nessuna regressione. Nessun bypass.

---

## 14. Limitations

- **Orchestrazione applicativa non esercitata live**: l'immagine `api` del compose è
  la build upstream prebuilt `intelloop/atlas-cmms-backend`, **priva** delle
  modifiche MOD-004B; costruire l'immagine backend modificata era sproporzionato per
  questa verifica di runtime. Il seam applicativo `FileController → FileService →
  StorageService` è coperto dagli unit test (doc 19) e dalla verifica indipendente
  (doc 20); il seam runtime **storage+proxy** — l'unico non coperto dai mock — è
  stato verificato qui in modo fedele (stessa `io.minio` 8.6.0, stesso codepath di
  firma, `nginx.conf` reale, immagini MinIO/nginx identiche al compose).
- La firma è stata generata dall'harness (non dal processo Spring), ma con **la
  stessa libreria e gli stessi identici argomenti** (`extraQueryParams`, method,
  bucket, expiry, region) di `MinioService.generateSignedUrl`.
- Percorso **GCP** non verificato (self-hosted usa MinIO; GCP fuori scope).
- **Storage-failure** live non indotto (§10) — coperto da unit test.
- Nessuna verifica **browser** reale (§11) — header equivalenti verificati via curl.

---

## 15. Verdict

**PASS.**

Il comportamento runtime di MOD-004B è confermato end-to-end sul layer che gli unit
test non potevano coprire: MinIO onora la `response-content-disposition` firmata
(attachment per non-immagine, inline per immagine), il parametro è legato alla firma
(manomissione → 403), e nginx `/storage` inoltra i param firmati preservando il
`Content-Disposition` upstream e mantenendo `nosniff` + `X-Frame-Options: DENY`. Il
delete rimuove davvero il binario ed è idempotente. Regressione 6/6 e 1445/0/0/0
verdi. La limitazione di runtime dichiarata nel doc 20 è **chiusa**.

Findings solo non bloccanti (O-01 informativo; VF-01/VF-02 ereditati e accettati).

⏹️ **STOP** — non procedo a MOD-005 e non applico correzioni. La decisione successiva
spetta al responsabile tecnico dopo l'analisi di questo report.

`Code changes: none.`
