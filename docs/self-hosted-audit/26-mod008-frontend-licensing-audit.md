# MOD-008 — Frontend Licensing & Feature-Gate Audit

Audit mirato del **frontend web** (React/TypeScript) del fork Atlas per determinare se
esistono restrizioni commerciali/licensing lato UI che impedirebbero l'uso di
funzionalità già sbloccate e verificate nel backend self-hosted. **Solo analisi:
nessuna modifica al frontend o ad altro.** Il mobile è fuori scope.

`Code changes: NONE.`

---

## 1. Objective

Stabilire se il frontend ufficiale contiene feature gate / licensing / plan /
Cloud-vs-Self-hosted checks che bloccano feature disponibili nel backend, distinguendo
le **restrizioni commerciali** dai **normali controlli di autorizzazione** e dal
**comportamento di deployment**. Produrre una decisione tecnica documentata, non
un'implementazione.

---

## 2. Sources Reviewed

`CLAUDE.md`; [22-audit-consolidation.md](22-audit-consolidation.md);
[23-mod005-runtime-integration-verification.md](23-mod005-runtime-integration-verification.md);
[24-mod006-deployment-alignment.md](24-mod006-deployment-alignment.md);
[25-mod007-documentation-baseline.md](25-mod007-documentation-baseline.md).
Codice frontend: `config.ts`, `hooks/useLicenseEntitlement.ts`, `slices/license.ts`,
`contexts/JWTAuthContext.tsx` (`hasFeature`/permessi), `content/own/Files/index.tsx`,
`content/own/components/FeatureErrorMessage.tsx`, `SidebarMenu/{index.tsx,items.ts}`,
`App.tsx`, `CompanyProfile/CompanyPlan.tsx`; ricerca pattern licensing/plan/cloud su
tutto `frontend/src`.

---

## 3. Frontend Architecture Relevant to Audit

Tre meccanismi di gating (tutti alimentati dal backend o dalla config di deployment):

| Meccanismo | Definizione | Sorgente del dato |
|---|---|---|
| Entitlement (Livello A) | `useLicenseEntitlement(e)` → `license.valid && entitlements.includes(e)` | Redux `state.license.state` ← `GET /api/license/state` (backend) |
| Plan (Livello B) | `hasFeature(f)` → `company.subscription.subscriptionPlan.features.includes(f)` | `state.company` (backend, da `/auth/me`/company) |
| Permission | `hasViewPermission`/`hasCreatePermission`/… | ruolo utente (backend) |
| Cloud/Self-hosted | `isCloudVersion = CLOUD_VERSION==='true'` | env di deployment (frontend) |

Nessun meccanismo valida licenze lato client: lo stato licensing è **letto dal backend**.

---

## 4. Licensing Checks

TEST: il frontend contatta Keygen o valida licenze lato client? · EXPECTED: no ·
ACTUAL: ricerca `keygen|Keygen|KEYGEN|api.keygen|licenses/actions` in `frontend/src`
→ **0 risultati**. L'unico percorso licensing è `slices/license.ts`:
`getLicenseValidity()` → `api.get('license/state')` (endpoint backend). `initialState`
= `{valid:false, entitlements:[]}` finché il backend non risponde. RESULT: **PASS** —
nessuna dipendenza commerciale/Keygen nel frontend; licensing 100% backend-driven.
EVIDENCE: `slices/license.ts:37-40`, grep Keygen vuoto.

TEST: gli entitlement gate sono aperti in self-hosted? · EXPECTED: sì (backend concede
l'intero enum, MOD-001) · ACTUAL: `useLicenseEntitlement` usato per `BRANDING`
(`App.tsx:113`, `useBrand.ts:28`), `RESOURCE_PLANNING` (`WorkOrders/index.tsx:185`),
`API_ACCESS` (`Settings/Integrations/index.tsx:19`). Poiché in self-hosted
`license.valid=true` e `entitlements` = intero enum (verificato in MOD-005: nessun
Keygen, SELF_HOSTED), questi ritornano **true** → gate aperti. RESULT: **PASS**.

---

## 5. Feature Gates

TEST: i gate `PlanFeature` bloccano feature in self-hosted? · EXPECTED: no (BUSINESS ha
tutte le 17 feature) · ACTUAL: `hasFeature(f)` (36 file) verifica l'appartenenza di `f`
alle feature del piano. Pattern tipico (`Files/index.tsx`):

```text
if (hasFeature(FILE))  -> render (poi permission check -> PermissionErrorMessage)
else                   -> <FeatureErrorMessage message='upgrade_files' />
```

In self-hosted il signup assegna **BUSINESS** con **tutte** le `PlanFeatures`
(MOD-005, verificato a runtime: 17 feature incl. `FILE`) → `hasFeature(*)` = true →
il ramo `FeatureErrorMessage`/"upgrade_" **non** viene mai raggiunto. RESULT: **PASS**
— i gate esistono ma sono **soddisfatti** dal piano self-hosted.

Nota: `FeatureErrorMessage` è un messaggio "upgrade" mostrato solo quando il piano non
ha la feature; il link punta a `/app/subscription/plans` (cloud) o a `homeUrl/pricing`
(self-hosted). Irrilevante in self-hosted perché non innescato.

---

## 6. Cloud/Self-hosted Checks

TEST: esiste un check che **nasconde** una feature quando NON è cloud? · EXPECTED: no ·
ACTUAL: ricerca `!isCloudVersion` / `!IS_ORIGINAL_CLOUD` → **0 risultati**. Tutti gli
usi di `isCloudVersion` sono **additivi-per-cloud** (mostrano elementi solo se cloud):

| Uso | File | Effetto in self-hosted (isCloudVersion=false) |
|---|---|---|
| Banner trial/upgrade | `SidebarMenu/index.tsx:266` | non mostrato |
| Link billing/piani (Paddle) | `CompanyProfile/CompanyPlan.tsx`, `Subscription/Plans` | non mostrato / link va a `homeUrl` |
| Intercom support | `ExtendedSidebarLayout/index.tsx:20` | disattivato |
| Analytics/tracking, redirect | `App.tsx:77,184,191` | disattivati |
| Extra registrazione | `Register/Cover:278` | non mostrati |
| Target link upgrade | `FeatureErrorMessage.tsx:26` | link esterno `homeUrl` |

`items.ts` (voci di menu): **nessun** gate `isCloudVersion`/`hasFeature`/`entitlement`
→ la navigazione non è nascosta in self-hosted. RESULT: **PASS** — `isCloudVersion`
governa solo **billing/marketing/support cloud** (Class D deployment behavior), non
nasconde funzionalità.

---

## 7. Plan/Permission Checks

I controlli permesso (`hasViewPermission`, `hasCreatePermission`, `hasEditPermission`,
`hasDeletePermission`) sono **autorizzazione legittima** basata sul ruolo (Class B),
non restrizioni commerciali: replicano il modello permessi backend e non vanno rimossi.
In self-hosted l'owner (ruolo "Administrator", `ROLE_CLIENT`) ha i permessi del proprio
ruolo; l'accesso alle feature dipende da permessi + piano (aperto) come nel backend.

---

## 8. Backend ↔ Frontend Feature Comparison

```text
BACKEND FEATURE AVAILABLE (self-hosted)  →  FRONTEND VISIBLE?  →  FRONTEND USABLE?
```

| Feature (verificata backend) | Gate frontend | Visibile self-hosted? | Usabile? |
|---|---|---|---|
| Allegati / upload (`FILE_ATTACHMENTS`+`FILE`) | `hasFeature(FILE)` | ✅ (BUSINESS ha FILE) | ✅ (MOD-005: upload 200 via UI-equivalent path) |
| Entitlement enum (SSO, RESOURCE_PLANNING, API_ACCESS, BRANDING, …) | `useLicenseEntitlement` | ✅ (backend concede tutto) | ✅ |
| Piani/PlanFeatures (BUSINESS) | `hasFeature` | ✅ (tutte le 17) | ✅ |
| Tenant/company isolation | n/a UI (enforced backend `@PostLoad`) | — | corretto (403 cross-company, MOD-005) |

Nessuna feature disponibile nel backend risulta irraggiungibile dalla UI in self-hosted:
i gate frontend leggono lo **stesso stato backend** che i MOD precedenti hanno reso aperto.

---

## 9. Runtime Verification

Stato: **non ri-eseguito un nuovo stack** (§11: non creare un ambiente se non
necessario; lo stack isolato MOD-005/006 è stato smontato). Evidenza runtime già
disponibile e sufficiente:

- MOD-005/006 hanno servito il **frontend reale** attraverso nginx (`GET /` → 200);
- lo **stato licensing** che alimenta i gate frontend è stato verificato a runtime in
  MOD-005 (backend SELF_HOSTED, `valid=true`, entitlement pieni, **BUSINESS+FILE**),
  e l'upload allegati (gate `hasFeature(FILE)` lato UI ↔ gate backend) ha risposto 200.

TEST: la feature "allegati" è usabile end-to-end con lo stato self-hosted? · EXPECTED:
sì · ACTUAL: MOD-005 upload/download/delete 200 con BUSINESS+FILE · RESULT: **PASS
(via MOD-005)**. Una verifica UI dedicata (login→dashboard→click) è opzionale e non
ri-eseguita per non creare un nuovo ambiente; l'analisi statica è conclusiva.

---

## 10. Findings

| ID | Classe | Descrizione | Blocca self-hosted? |
|---|---|---|---|
| F-01 | C (gate commerciale, **satisfied**) | `useLicenseEntitlement` (BRANDING/RESOURCE_PLANNING/API_ACCESS) e `hasFeature(*)` gate commerciali | **No** — aperti dal backend (entitlement pieni + BUSINESS) |
| F-02 | D (Cloud/Self-hosted) | `isCloudVersion` accende billing/Paddle/pricing/trial-banner/Intercom/tracking | **No** — spenti in self-hosted; non nascondono feature |
| F-03 | B (authorization) | `has*Permission` controlli di ruolo | **No** — autorizzazione legittima, da non rimuovere |
| — | A | Nessun gate commerciale frontend che blocchi feature backend-disponibili | — |

Nessun riferimento Keygen/commerciale eseguito nel frontend. Nessun `!isCloudVersion`
feature-hiding. Nessun blocco che richieda modifiche al frontend per il self-hosted.

---

## 11. Classification

Per la domanda centrale del MOD ("il frontend blocca feature già disponibili nel
backend?"): **A — Nessuna restrizione bloccante**. I gate presenti sono:

- **C (commerciali) ma SATISFIED** dal licensing self-hosted (MOD-001): entitlement +
  BUSINESS plan → aperti;
- **D (Cloud/Self-hosted)**: solo billing/marketing/support cloud, correttamente
  disattivati in self-hosted;
- **B (authorization)**: legittimi, da preservare.

---

## 12. Recommended Next Action

**Nessuna modifica al frontend è necessaria per il self-hosted.** Il frontend è
utilizzabile così com'è: i suoi gate leggono lo stato backend che i MOD precedenti
hanno reso aperto. Non rimuovere i gate commerciali/permessi (servono in cloud e per
l'autorizzazione). Eventuale verifica UI manuale (login→dashboard→allegati) resta
un'opzione di conferma, non un requisito.

(Il **mobile** resta da analizzare in un MOD separato — fuori scope qui.)

---

## 13. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-008 (frontend audit CLEAN) alla Current Project State, alla
Documentation Map e alla Documentation Workflow; registrata la decisione approvata
"frontend self-hosted non richiede modifiche (gate backend-driven, aperti)".
```

---

## 14. Final Verdict

```text
CLAUDE.md updated: YES
Code changes: NONE
Frontend status: CLEAN
Final verdict: PASS
```

Il frontend web **non presenta restrizioni commerciali bloccanti** per il self-hosted:
nessun Keygen/validazione client, gate entitlement/plan alimentati dal backend (aperti
con MOD-001 + piano BUSINESS), check `isCloudVersion` limitati a billing/marketing cloud
(spenti in self-hosted, senza nascondere feature), permessi = autorizzazione legittima.
**Non è richiesta alcuna modifica al frontend.**

⏹️ **STOP** — non implemento correzioni frontend, non avvio MOD-009, non analizzo il
mobile, non modifico Docker/nginx/backend, non implemento CFG-02. La decisione
successiva spetta al responsabile tecnico dopo la revisione del report.
