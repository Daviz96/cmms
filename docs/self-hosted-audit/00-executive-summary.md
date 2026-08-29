# 00 — Executive Summary

**Repository:** fork locale di Atlas CMMS (`Grashjs/cmms`)
**Scopo:** installazione self-hosted interna aziendale
**Fase:** AUDIT (solo analisi/documentazione — nessuna modifica al codice)
**Data audit:** 2026-08-25

---

## Sintesi in una frase

Quasi tutte le funzionalità "premium" di Atlas CMMS sono **già completamente
implementate nel codice sorgente AGPLv3** e bloccate da un unico meccanismo
centrale (`LicenseService.hasEntitlement()`, basato su licenza Keygen); il
secondo sistema di gating (`PlanFeatures` sulla subscription) risulta **di fatto
già aperto** in modalità self-hosted, perché ogni nuova azienda riceve il piano
`BUSINESS` senza scadenza.

---

## Architettura del gating (3 livelli)

| Livello | Nome | Sorgente di verità | Stato in self-hosted (senza licenza) |
|---|---|---|---|
| A | **License Entitlements** (`LicenseEntitlement`) | Keygen API / license file, via `LicenseService` | ❌ **CHIUSO** — tutti gli entitlement `false` |
| B | **Subscription Plan Features** (`PlanFeatures`) | Colonna DB `SubscriptionPlan.features` | ✅ **APERTO** — nuova azienda = piano `BUSINESS` (tutte le feature), nessuna scadenza |
| C | **Limiti numerici free** (`usageBasedFreeLimits`) | Costante Java `Consts` | ⚠️ **ATTIVO** — applicato solo se manca l'entitlement `UNLIMITED_*` corrispondente |

**Conseguenza pratica:** il gate realmente vincolante per il self-hosted è il
**Livello A (Keygen)**. Sbloccare il Livello A (in modo centralizzato e pulito)
rende disponibili quasi tutte le funzionalità prioritarie, perché il Livello B è
già soddisfatto e il Livello C dipende dagli entitlement `UNLIMITED_*` del
Livello A.

Dettagli: [01-license-entitlements.md](01-license-entitlements.md),
[02-backend-feature-gates.md](02-backend-feature-gates.md).

---

## Punti chiave scoperti durante l'audit

1. **`LicenseService.hasEntitlement(e)`** ritorna
   `state.isValid() && state.getEntitlements().contains(e.toString())`.
   Senza `LICENSE_KEY` né license file, lo stato è `valid=false` → **ogni**
   entitlement è negato.
   File: [`api/.../service/LicenseService.java:78`](../../api/src/main/java/com/grash/service/LicenseService.java#L78).

2. **Doppio gating su alcune feature.** WEBHOOK, API_ACCESS, REQUEST_PORTAL,
   WORKFLOW, SIGNATURE, FILE richiedono **sia** l'entitlement Keygen **sia** la
   `PlanFeatures` sulla subscription. In self-hosted la parte `PlanFeatures` è
   soddisfatta dal piano BUSINESS, quindi resta bloccante solo Keygen.

3. **Nuova azienda → piano BUSINESS senza scadenza (self-hosted).**
   [`UserService.signup`](../../api/src/main/java/com/grash/service/UserService.java#L192)
   assegna `BUSINESS` con `endsOn=null` quando `cloud-version=false`. Il job di
   downgrade [`SubscriptionEndJob`](../../api/src/main/java/com/grash/job/SubscriptionEndJob.java)
   viene schedulato **solo** se `endsOn != null`
   ([`SubscriptionService.java:119`](../../api/src/main/java/com/grash/service/SubscriptionService.java#L119)),
   quindi in self-hosted non scade mai.

4. **Entitlement definiti ma mai applicati (gate morti):**
   `PARTS_COST_TRACKING` e `ADVANCED_ANALYTICS` esistono nell'enum e nella lista
   frontend ma **non hanno alcun controllo `hasEntitlement()`** nel backend →
   già disponibili.

5. **Nessuna funzionalità prioritaria risulta "non implementata".** Tutte le
   feature dell'elenco prioritario esistono nel codice AGPL. Le uniche dipendenze
   reali sono **esterne/di configurazione** (server LDAP/AD, provider OAuth2,
   MinIO, SMTP), non componenti commerciali mancanti.

---

## Classificazione sintetica (dettaglio in [04-feature-matrix.md](04-feature-matrix.md))

- 🟢 **UNLOCK_SIMPLE** (implementate, bloccate solo da entitlement): la
  stragrande maggioranza — WO History, Custom Roles, Asset Hierarchy, Asset
  Downtime, Time/Cost Tracking, WO Linking, Signature, PM Calendar, Condition-based
  PM, NFC, Voice Notes, Customer/Vendor, Field Config, Low Stock, Webhook,
  API, Request Portal, Resource Planning, Multi-instance, Branding, e tutti gli
  `UNLIMITED_*`.
- 🟡 **UNLOCK_PLUS_MODIFICATION**: SSO/LDAP e File Attachments — sbloccabili come
  gate, ma richiedono **configurazione + dipendenza esterna** (server LDAP/AD;
  storage MinIO/GCP/locale).
- ⚪ **ALREADY_AVAILABLE**: PM base, Checklist base, Meters base, Analytics base,
  Import CSV, Purchase Orders, ruoli in lettura, email notifications
  (tutte gate solo da `PlanFeatures`, già soddisfatte), più i gate morti
  `PARTS_COST_TRACKING`/`ADVANCED_ANALYTICS`.
- 🔴 **NOT_IMPLEMENTED**: nessuna feature prioritaria.

---

## Raccomandazione architetturale (per la Fase 2 — da approvare)

Introdurre una **modalità self-hosted centralizzata** invece di rimuovere i
singoli gate o forzare `return true` dentro `hasEntitlement()`:

> Un flag di configurazione (es. `licensing.self-hosted-mode=true`) fa sì che
> `LicenseService.getLicensingState()` restituisca uno stato `valid=true` con
> l'insieme completo (o configurabile) di entitlement, **senza** contattare
> Keygen e **senza** toccare i singoli service/controller.

Vantaggi: un solo punto di modifica, comportamento prevedibile e testabile,
sistema di licensing intatto per l'eventuale build cloud, facilità di
sincronizzazione con l'upstream. Piano dettagliato in
[11-modification-plan.md](11-modification-plan.md).

> ⚠️ **Nota legale:** il codice è AGPLv3 (in alternativa alla licenza
> commerciale INTELLOOP LLC). L'AGPL concede il diritto di modificare il sorgente
> per uso interno; se in futuro la build viene esposta in rete a terzi, valgono
> gli obblighi AGPL (offerta del codice sorgente). Vedi
> [10-security-considerations.md](10-security-considerations.md).
