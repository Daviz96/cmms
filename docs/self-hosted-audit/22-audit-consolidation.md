# 22 — Audit Consolidation & Gap Analysis

Fotografia consolidata dello stato dell'audit self-hosted Atlas CMMS. Fase **solo
analisi**: nessuna modifica a codice, configurazione, Docker, nginx, licensing,
frontend/backend. `Code changes: none.`

Fonti in ordine di priorità: [CLAUDE.md](../CLAUDE.md), audit/verification/
implementation più recenti (doc 18→21), architettura/decisioni, codice, test.
Le informazioni più recenti prevalgono.

---

## 1. Executive Summary

- **Completato e verificato**: MOD-001 (self-hosted licensing), MOD-002 (limiti
  `UNLIMITED_*`), MOD-003A (hardening/test LDAP), MOD-004 (audit allegati),
  MOD-004B (sicurezza+lifecycle allegati), MOD-004C (verifica runtime storage/proxy).
- **Stato test**: suite **1445/1445 PASS**; targeted allegati 6/6. Baseline storica
  1412 → 1430 (MOD-002) → 1439 (MOD-003A) → 1445 (MOD-004B); MOD-004C senza modifiche
  al codice.
- **Cosa resta**: nessuna feature prioritaria è ancora bloccata dal *licensing*. Il
  layer entitlement è interamente sbloccato in self-hosted (MOD-001 concede l'intero
  enum; il piano BUSINESS soddisfa `PlanFeatures`). Ciò che rimane è: (a) **verifica
  runtime a livello applicativo** dei mod dentro il processo reale (licensing +
  allegati), oggi coperta solo da unit test + ispezione + harness storage/proxy;
  (b) feature che dipendono da **servizi esterni** da configurare (LDAP/AD, OAuth2,
  MinIO, SMTP); (c) alcune **decisioni di prodotto** LDAP aperte.
- **Restrizioni licensing trovate**: due livelli — `LicenseEntitlement`
  (`hasEntitlement()`, throw lato backend) e `PlanFeatures` (piano). Entrambi aperti
  in self-hosted. **Il backend non contiene alcun check cloud/self-hosted** che neghi
  feature (nessun `CLOUD_VERSION`/`isCloudVersion` lato API); l'unico switch è
  `licensing.self-hosted-mode` (MOD-001). Il frontend ha check `CLOUD_VERSION`/
  `hasFeature`/`useLicenseEntitlement` (UX), non autoritativi.
- **Prossimo MOD raccomandato (uno)**: **verifica di integrazione runtime self-hosted
  con immagine backend buildata dai sorgenti** — validare *live* nel processo reale
  (app + Postgres + MinIO + nginx) che (1) la self-hosted mode risolve gli
  entitlement e (2) l'upload/download/delete allegati esibisce il comportamento
  MOD-004B end-to-end. Chiude i due maggiori gap di verifica in un solo intervento a
  basso rischio. Dettaglio in §14.

---

## 2. Audit Scope

IN scope: consolidamento stato MOD, findings, licensing audit dal codice,
classificazione feature A–F, gap di verifica, contraddizioni, priorità, singola
raccomandazione. OUT of scope: qualsiasi implementazione, modifica config/DB/Docker/
nginx/licensing, avvio di nuovi MOD. Consentiti solo lettura, ricerca, analisi ed
esecuzione di test esistenti (non modificati).

---

## 3. Documentation Sources

| Doc | Contenuto | Uso |
|---|---|---|
| `CLAUDE.md` | stato consolidato di progetto | fonte primaria di stato |
| `04-feature-matrix.md` | baseline feature/licensing (34 entitlement, 17 PlanFeatures) | licensing/feature |
| `13/14-mod001-*` | self-hosted licensing (impl+verifica) | MOD-001 |
| `15-mod002-verification.md` | limiti `UNLIMITED_*` | MOD-002 |
| `16-mod003-ldap-ad-audit.md`, `17-mod003a-implementation.md` | LDAP audit + hardening | MOD-003/003A |
| `18-mod004-attachment-audit.md` | audit allegati/storage | MOD-004 |
| `19-mod004b-…-implementation.md` | fix sicurezza+lifecycle | MOD-004B |
| `20-mod004b-verification.md` | verifica indipendente | MOD-004B |
| `21-mod004b-decision-and-next-step.md` | decisione + brief MOD-004C | decisione |
| `21-mod004c-e2e-verification.md` | verifica runtime storage/MinIO/nginx | MOD-004C (fonte primaria runtime) |

Codice riletto in questa fase: `LicenseService`, `LicenseEntitlement`,
`PlanFeatures`, `Consts.usageBasedFreeLimits`, `FileController`, `FileService`,
`MinioService`, `GCPService`, `StorageService`, `FileMapper`, `CompanyAudit`.
Frontend: grep dei gate (`CLOUD_VERSION`/`hasFeature`/`useLicenseEntitlement`).

---

## 4. MOD Status Matrix

| MOD | Obiettivo | Stato | Test | Verification | Findings | Rischio residuo |
|---|---|---|---|---|---|---|
| **MOD-001** | Self-hosted licensing centralizzato (concede entitlement senza Keygen) | **PASS** | unit (incl. `LicenseServiceTest`), inclusa in 1445 | doc 14 (verificato) + codice riletto | — | Effetto runtime nel processo reale non validato live (solo unit/ispezione) |
| **MOD-002** | Sblocco 8 limiti `UNLIMITED_*` in self-hosted | **PASS** | 1430 baseline, incl. suite | doc 15 | — | Nessuno (bypass via short-circuit entitlement, no code change) |
| **MOD-003** | Audit LDAP/AD | **PASS** (audit) | — | doc 16 | LDAP mono-company, no memberOf, no StartTLS, ecc. (decisioni aperte) | Vedi §12 known issues |
| **MOD-003A** | Hardening/logging/test LDAP | **PASS** | 1439 | doc 17 | — | Solo decisioni di prodotto aperte (§12) |
| **MOD-004** | Audit allegati/storage/MinIO | **PASS** (audit) | — | doc 18 | G (stored-XSS), D/G (orfani), portale pubblico, no allowlist | Risolti da MOD-004B (tranne accettati) |
| **MOD-004B** | Mitigazione stored-XSS + lifecycle delete | **PASS WITH FINDINGS** | targeted 6/6; suite 1445 | doc 19 (impl) + doc 20 (indip.) | VF-01 (Low, accettato), VF-02 (Info, accettato) | VF-01 binario orfano su errore storage reale |
| **MOD-004C** | Verifica runtime storage/proxy | **PASS** | targeted 6/6; suite 1445 (no code change) | doc 21 (runtime live) | O-01 (Info) | App-level `FileController→FileService` e browser non validati live |

Stati usati: solo `PASS`, `PASS WITH FINDINGS` (nessun MOD attualmente in `IN
PROGRESS/PARTIAL/NOT VERIFIED/OPEN/BLOCKED`).

---

## 5. Findings Consolidation

Categorie: `Accepted`, `Open`, `Resolved`, `Informational`.

| ID | Severity | Componente | Descrizione | Stato | Impatto | Decisione | Azione futura |
|---|---|---|---|---|---|---|---|
| MOD004-G | Medium→ | allegati/`/storage` | stored-XSS via content-type client-controlled su file HTML/SVG | **Resolved** (MOD-004B) | esecuzione contenuto attivo same-origin | mitigato (disposition attachment + nosniff/XFO) | — |
| MOD004-D/G | Low | `FileService.delete` | binario MinIO non cancellato → orfani | **Resolved** (MOD-004B lifecycle) | crescita storage | delete storage+thumb prima dei metadati | — |
| **VF-01** | Low | `FileService.deleteStorageObjectQuietly` | su errore storage reale il binario resta orfano (best-effort) | **Accepted** | igiene storage; nessun impatto accesso/sicurezza | accettato (doc 21) | opzionale: metrica/alert o job riconciliazione |
| **VF-02** | Info | overload `generateSignedUrl(String)` | report/export/logo/thumbnail senza `Content-Disposition` | **Accepted** | non è il vettore stored-XSS (contenuto server-generato/immagini) | nessuna | rivalutare solo se un export conterrà HTML utente |
| **O-01** | Info | nginx `/storage` | `X-Content-Type-Options: nosniff` duplicato (MinIO+nginx) | **Informational** | nessuno (valore identico) | nessuna | eventuale dedup cosmetico (fuori scope) |
| PORTAL | Info | `/files/upload/request-portal/{uuid}` | upload pubblico senza gate `FILE_ATTACHMENTS`, rate-limited per IP | **Accepted** (by design) | intake pubblico richieste | invariato | — |
| ALLOWLIST | Info | upload allegati | nessuna allowlist di tipi file/MIME | **Accepted** | neutralizzato via disposition+nosniff | come da brief MOD-004B | — |
| PRESIGN-TTL | Info | presigned URL | URL scaricabile entro 3h da chiunque lo detenga | **Informational** | comportamento standard S3/MinIO | invariato | — |
| LDAP-SYNC-DEF | Low | `application.yml` vs `docker-compose` | default sync divergenti (yml true / compose false) | **Open** (documentato) | possibile confusione operativa | lasciato a config esplicita (MOD-003A) | decisione operatore |
| LDAP-CREDS | Info | env Docker LDAP | credenziali service-account visibili via `docker inspect` | **Informational** | esposizione se host compromesso | trattare come secret | gestione secret a livello deploy |
| DEAD-GATES | Info | `ADVANCED_ANALYTICS`, `PARTS_COST_TRACKING` | entitlement mai applicati (gate morti) | **Informational** | nessuno (feature già disponibile) | nessuna | — |

Nota: Low/Info **non** vengono trasformati automaticamente in nuovi MOD.

---

## 6. Licensing Audit

Meccanismo reale (verificato nel codice):

**Livello A — Entitlement.** `LicenseService.hasEntitlement(LicenseEntitlement)`
→ `getLicensingState().isValid() && entitlements.contains(code)`. In self-hosted
(`licensing.self-hosted-mode=true`, MOD-001) `getLicensingState()` **corto-circuita
in cima** e restituisce `buildSelfHostedLicensingState()`: `valid=true`,
`hasLicense=true`, `planName="Self-Hosted"`, **entitlements = intero enum**
(34 valori), `usersCount=Integer.MAX_VALUE`, **senza contattare Keygen**. Il gate
backend tipico è `if (!hasEntitlement(X)) throw 403`.

**Livello B — Plan.** `PlanFeatures` (17 valori) da
`company.subscription.subscriptionPlan.features`. Controllato in vari controller
(es. `FileController:77` richiede `PlanFeatures.FILE`) e nel frontend
(`hasFeature`). In self-hosted il piano **BUSINESS** (assegnato per doc 13 —
*non ri-verificato nel codice in questa fase, vedi §15*) soddisfa i `PlanFeatures`.

**Limiti numerici free-tier.** `Consts.usageBasedFreeLimits` (8 voci: checklists 10,
assets 50, parts 100, locations 10, PM 10, active WO 30, meters 10, users 5).
Applicati finché manca il rispettivo `UNLIMITED_*`; con l'entitlement concesso il
controllo va in short-circuit (MOD-002, nessuna modifica ai limiti).

**Check cloud/self-hosted.**
- Backend: **nessun** `CLOUD_VERSION`/`isCloudVersion` (grep 0 risultati). Nessun
  endpoint nega funzionalità in base a "cloud vs self-hosted"; l'unico switch è
  `licensing.self-hosted-mode`.
- Frontend: `CLOUD_VERSION`/`hasFeature`/`useLicenseEntitlement` presenti in ~44 file
  (billing/registrazione/Paddle e gating UX). **Non autoritativi**: il backend è la
  sorgente di verità. Controlli duplicati frontend/backend esistono per UX (es.
  `Files/index.tsx` + `AddFileModal.tsx` su `PlanFeature.FILE`), coerenti col gate
  backend.

**Dipendenze esterne non attivate dall'entitlement.** Concedere l'entitlement **non**
avvia il servizio: LDAP/AD, OAuth2, MinIO, SMTP restano gated dai propri flag di
configurazione (coerente col commento in `LicenseService` e con la feature matrix
classe 🟡).

Per ogni restrizione (sintesi; dettaglio per-feature nella tabella §7 e in doc 04):
entry point = controller/service della feature; punto del check = `hasEntitlement`
e/o `PlanFeatures.contains`; comportamento con config attuale (self-hosted) =
**consentito**; possibilità tecnica = già implementata (nessun codice commerciale
mancante).

---

## 7. Feature Restriction Classification (A–F)

Legenda richiesta: **A** solo UI · **B** backend licensing · **C** frontend+backend ·
**D** feature realmente assente · **E** dipendenza esterna · **F** non determinato.

Poiché in self-hosted **tutti** gli entitlement sono concessi e il piano BUSINESS
soddisfa i `PlanFeatures`, le restrizioni B/C sono **di fatto già sbloccate**; la
classe indica *dove sarebbe il blocco* in modalità commerciale.

| Feature (da doc 04) | Classe | Stato self-hosted | Note |
|---|---|---|---|
| Limiti `UNLIMITED_*` (assets/users/locations/parts/PM/WO/checklists/meters) | **B** | Sbloccato (MOD-002) | solo gate backend, nessun frontend |
| Asset Hierarchy, Custom Roles/Permissions, WO History, PM Calendar, Condition-based PM, Asset Downtime, WO Linking, Time/Cost Tracking, Signature, Customer/Vendor, Request Portal, Field Config, Voice Notes, NFC/Barcode, Low Stock, Workflow, Webhook, API access, Resource Planning, Branding, Multi-instance | **C** | Sbloccato (MOD-001 + BUSINESS) | gate entitlement + spesso PlanFeatures + UI |
| Checklists, Parts, PM base, Meters base, Notifications, Import CSV, Purchase Orders, Analytics base | **C** (via `PlanFeatures`) | Sbloccato (BUSINESS) | ⚪ già disponibili |
| File Attachments | **C + E** | Sbloccato (MOD-001+BUSINESS) + **richiede MinIO/GCP** | sicurezza/lifecycle: MOD-004B/C |
| Email notifications / Invitations | **E** | Richiede SMTP/SendGrid | solo configurazione |
| LDAP / AD | **E** (gate entitlement `SSO`) | Richiede server LDAP/AD | impl completa (MOD-003/003A) |
| SSO / OAuth2 | **E** (config `enable-sso` + `SSO`) | Richiede provider OAuth2 | path separato |
| Storage su filesystem | **D** | **Non implementato** (solo MINIO/GCP) | vedi contraddizione §11 |
| `ADVANCED_ANALYTICS`, `PARTS_COST_TRACKING` | — (gate morto) | Disponibile | entitlement mai applicato |
| Assegnazione piano BUSINESS in self-hosted (codice) | **RISOLTO (MOD-005)** | BUSINESS + `FILE` verificati a runtime (doc 23) | signup→BUSINESS(17 feature) |

Nessuna feature prioritaria in classe **D** o realmente bloccata dal licensing.

---

## 8. Architecture Impact

Classificazione degli interventi *potenziali* (non proposte di implementazione):

| Intervento potenziale | Tipo | Note |
|---|---|---|
| Verifica runtime self-hosted con immagine backend buildata | Infrastructure (build) + verification | nessun code change; chiude gap §10 |
| Test integrazione allegati (Testcontainers+MinIO) | Cross-layer (test-only) | aggiunge test, nessun cambBehavior |
| VF-01 metriche/riconciliazione orfani | Local change (service) | Low; opzionale |
| Riconciliazione default LDAP sync yml↔compose | Infrastructure/config | decisione operatore |
| Storage filesystem | Data/flow + new StorageService impl | Feature realmente assente (D); solo se richiesto |
| LDAP memberOf/StartTLS/truststore/multi-company | Security-sensitive + cross-layer | decisioni di prodotto aperte |

---

## 9. Security / Multi-tenancy Review

Nessun bypass introdotto dai MOD verificati:

- **Tenant isolation**: `CompanyAudit.@PostLoad` (403 cross-company su ogni load)
  invariato e assente dai diff MOD-004B/004C (verificato doc 20).
- **Authorization**: gate allegati (`FILE_ATTACHMENTS` + `PlanFeatures.FILE` + perm
  `FILES`), `canBeViewedBy/EditedBy/DeletedBy` invariati; `FileService.delete` carica
  via `findById` (riattiva `@PostLoad`) e non aggira l'autorizzazione a monte.
- **Licensing enforcement**: nessun `return true`/bypass; self-hosted mode è uno
  switch centralizzato in `LicenseService`, non un bypass sparso nei service.
- **File access**: presigned URL su bucket privato rilasciati post-autorizzazione;
  object key UUID non indovinabile; stored-XSS neutralizzato (MOD-004B/C).
- **Audit logging**: invariato.

Potenziali bypass da sorvegliare in interventi futuri (registrati, non modificati):
qualsiasi mapping ruolo da attributo LDAP non controllato (memberOf) sarebbe
security-sensitive; lo storage filesystem dovrebbe preservare la sanitizzazione
object-key. Nessun bypass attuale rilevato.

---

## 10. Test & Verification Status

| MOD | Unit | Integration | Runtime | Manuale | Baseline | Gap |
|---|---|---|---|---|---|---|
| MOD-001 | ✅ (`LicenseServiceTest`) | parziale (suite) | ❌ non live | — | in 1445 | effetto self-hosted nel processo reale non validato live |
| MOD-002 | ✅ | ✅ suite | — | — | 1430 | — |
| MOD-003A | ✅ (`LdapServiceTest` 9) | — | ❌ (nessun server LDAP) | — | 1439 | flusso LDAP live non esercitato |
| MOD-004B | ✅ (6) | — | — | — | 1445 | storage-failure solo unit |
| MOD-004C | ✅ (6) | — | ✅ storage/proxy live | curl | 1445 | vedi sotto |

MOD-004C (riepilogo richiesto):
```text
Targeted: 6/6 PASS
Full suite: 1445/1445 PASS
Runtime storage/proxy: PASS
Browser reale: non verificato
Full FileController → FileService live: non verificato
```

Gap dichiarati con scope sufficiente **non** sono failure: MOD-004C copriva
intenzionalmente il seam storage/proxy (l'unico non coperto dai mock).

---

## 11. Contradictions / Obsolete Information

| Vecchia informazione | Nuova informazione | Fonte più recente | Attuale |
|---|---|---|---|
| Feature matrix (doc 04) elenca File Attachments Ext = "MinIO/GCP/**FS**" | Audit codice: `StorageType={GCP,MINIO}`, **nessun** filesystem | doc 18 + codice | Filesystem **non** implementato (classe D) |
| Brief MOD-004 ipotizzava "MinIO/filesystem" | Solo MinIO o GCP | doc 18/19 + codice | Solo MinIO/GCP |
| CLAUDE.md "Current focus" storico (MOD-003A) | MOD-004C PASS | doc 21 + CLAUDE.md aggiornato | MOD-004C è l'ultimo stato |

Nessun conflitto tra codice e ultima verifica. Default LDAP sync divergenti
(yml↔compose) sono un fatto documentato, non una contraddizione di decisione (in
compose i default sono `false`, che prevalgono operativamente).

---

## 12. Known Issues (supportati)

- **Security**: LDAP service-account creds visibili via `docker inspect`
  (Info, gestione secret a deploy). Nessun bypass tenant/authz aperto.
- **Licensing**: nessun blocco residuo per feature prioritarie; assegnazione piano
  BUSINESS in self-hosted non ri-verificata nel codice in questa fase (§15).
- **Functional**: storage filesystem assente (D); OU→ruoli solo default; `memberOf`
  non mappato; ruoli LDAP applicati al login, non in sync.
- **Reliability**: VF-01 binari orfani su errore storage reale (Low, accettato);
  default LDAP sync divergenti (Open).
- **Infrastructure**: immagini `api`/`frontend` del compose sono **prebuilt upstream**
  (`intelloop/atlas-cmms-*`) → **non** contengono le modifiche dei MOD; un deploy
  self-hosted delle modifiche richiede una build locale dell'immagine backend.
- **Documentation**: doc 04 (FS storage) superata da doc 18 (§11).
- **Testing**: nessun test integrazione a livello app per allegati; flusso LDAP e
  self-hosted licensing non esercitati live.

---

## 13. Prioritized Gaps

| Prio | Problema/feature | Motivazione | Impatto utente | Impatto tecnico | Rischio | Dipendenze | MOD candidato |
|---|---|---|---|---|---|---|---|
| ~~P1~~ **DONE** | Modifiche non validate live nel processo reale | risolto: backend buildato dai sorgenti verificato a runtime | — | — | — | — | **MOD-005/006 (doc 23/24)** |
| **P2** | Assenza test integrazione allegati automatici | regressioni non rilevate a livello app | indiretto | Testcontainers+MinIO | Basso | infra test | MOD-004d |
| ~~P2~~ **DONE** | Assegnazione piano BUSINESS non ri-verificata | risolto: BUSINESS+`FILE` verificati a runtime | — | — | — | — | **MOD-005 (doc 23)** |
| **P3** | Default LDAP sync divergenti (yml↔compose) | confusione operativa | operatori LDAP | config | Basso | decisione | mini-decisione |
| **P3** | VF-01 orfani su errore storage | igiene storage | trascurabile | metrica/job | Basso | — | opzionale |
| **P3** | Storage filesystem assente | alternativa a MinIO | chi non vuole MinIO | nuova impl `StorageService` | Medio | — | solo se richiesto |

---

## 14. Recommended Next MOD

**Raccomandazione principale (una): MOD-005 — Verifica di integrazione runtime
self-hosted con immagine backend buildata dai sorgenti.**

- **Problema risolto**: chiude il gap P1 e i gap "app-level non live" di MOD-004C e
  "effetto runtime non validato" di MOD-001. Oggi nessuna modifica è stata eseguita
  *dentro il processo applicativo reale* contro infrastruttura reale.
- **Cosa verificherebbe** (solo verifica, no nuove feature): build dell'immagine
  backend dai sorgenti (con le modifiche MOD-001/002/004B), avvio stack self-hosted
  (`LICENSING_SELF_HOSTED_MODE=true`, Postgres+MinIO+nginx), poi live: (1)
  `getLicensingState()` risolve gli entitlement e il log mostra `SELF_HOSTED`; (2)
  upload→download→delete allegati via `FileController` reale esibisce disposition
  attachment/inline e lifecycle di cancellazione; (3) isolamento company su un
  secondo tenant.
- **Priorità**: P1. **Componenti**: build backend, docker-compose (uso, non
  modifica), MinIO, nginx, licensing (osservazione), allegati.
- **Rischi**: bassi (verifica); attenzione a non committare secret e a non modificare
  codice/config; la build immagine è attività infrastrutturale, non un code change.
- **Documenti da leggere prima**: [13/14-mod001](13-mod001-implementation.md),
  [19](19-mod004b-security-lifecycle-implementation.md),
  [21-mod004c](21-mod004c-e2e-verification.md), `docker-compose.yml`, `nginx.conf`.

**Alternative (max 2, non prioritarie):**
1. **MOD-004d** — test integrazione allegati automatizzati (Testcontainers+MinIO) in
   suite: assicurazione ripetibile senza build immagine, ma non valida licensing né
   il deploy reale.
2. **Mini-decisione LDAP sync defaults** — allineare/documentare i default
   yml↔compose: piccola, ma è una decisione di prodotto, non una verifica.

La decisione finale resta al responsabile tecnico.

---

## 15. Open Questions (UNKNOWN / DA VERIFICARE)

> Aggiornamento MOD-007: i punti 1 e 2 sono stati **RISOLTI** da MOD-005/006 (vedi
> doc 23/24). Mantenuti qui come storico con l'esito.

1. **Assegnazione piano BUSINESS in self-hosted**: ~~doc 13 lo afferma; percorso di
   codice non ri-verificato~~ → **RISOLTO (MOD-005, doc 23)**: `UserService.signup`
   assegna il piano `BUSINESS` e la company ottiene tutte le 17 `PlanFeatures` incl.
   `FILE` (verificato a runtime da DB + upload). Il gate Livello B è soddisfatto.
2. **Strategia di deploy**: ~~immagini prebuilt vs build locale~~ → **RISOLTO
   (MOD-006, doc 24)**: il compose ufficiale ora **builda il backend dai sorgenti**
   (`build: ./api`, `atlas-cmms-backend:local`), non l'immagine upstream.
3. **Scope self-hosted per servizi esterni**: SMTP/OAuth2 sono in-scope per il
   rollout self-hosted o rinviati? → `DA VERIFICARE`.
4. **Storage filesystem**: richiesto dal progetto o MinIO/GCP è sufficiente? →
   `UNKNOWN` (determina se la classe D va affrontata).

---

## 16. Conclusion

Il perimetro **licensing-unlock** dell'obiettivo self-hosted è, allo stato,
sostanzialmente **completo e verificato a livello di codice/unit**: MOD-001 concede
gli entitlement, MOD-002 sblocca i limiti, MOD-003A mette in sicurezza LDAP, e la
catena MOD-004/004B/004C rende gli allegati sicuri e ne verifica il runtime
storage/proxy. **Nessuna feature prioritaria resta bloccata dal licensing**; ciò che
rimane è configurazione di servizi esterni (E), poche feature realmente assenti (D,
non prioritarie), decisioni di prodotto LDAP e — soprattutto — la **validazione
runtime a livello applicativo** delle modifiche, oggi non ancora eseguita perché lo
stack compose usa immagini upstream prebuilt.

Raccomandazione: **MOD-005 (verifica di integrazione runtime self-hosted con immagine
backend buildata)**.

⏹️ **STOP** — non creo MOD-005, non implemento, non modifico licensing, non applico
fix. La decisione spetta al responsabile tecnico.

`Code changes: none.`
