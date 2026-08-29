# 11 — Modification Plan (proposta per la Fase 2)

> ⚠️ Questo è un **piano**, non modifiche applicate. Nessun codice è stato
> cambiato durante l'audit. Attendere approvazione prima di procedere.

---

## Strategia raccomandata: modalità self-hosted centralizzata

**Un solo punto di intervento** anziché rimuovere ~40 gate sparsi.

Idea: introdurre un flag di configurazione (es. `licensing.self-hosted-mode`,
env `SELF_HOSTED_UNLIMITED`, default `false`) che, quando `true`, fa restituire a
`LicenseService.getLicensingState()` uno stato `valid=true` con l'insieme
**completo** (o configurabile) di entitlement — **senza** contattare Keygen.

Perché è la scelta migliore:

- **Un unico file** da modificare (`LicenseService`), zero modifiche a
  service/controller/frontend.
- Il **frontend si allinea da solo**: `useLicenseEntitlement` legge
  `/license/state`, che rifletterà i nuovi entitlement.
- Il **Livello B** (`PlanFeatures`) è già aperto in self-hosted (piano BUSINESS).
- Il sistema di licensing **resta intatto** e riattivabile (flag `false`).
- **Nessun `return true` cieco**: lo stato è costruito esplicitamente e loggato.
- Facile da **testare** e da **sincronizzare** con l'upstream.

---

## MOD-001 — Modalità self-hosted centralizzata (CORE)

- **Feature:** sblocco entitlement per self-hosted (tutte le 🟢).
- **Files to modify:**
  - `api/src/main/java/com/grash/service/LicenseService.java` (aggiungere flag +
    ramo che costruisce `LicensingState` "self-hosted").
  - `api/src/main/resources/application.yml` (nuova proprietà
    `licensing.self-hosted-mode: ${SELF_HOSTED_UNLIMITED:false}`).
  - `.env.example` (documentare la variabile).
- **Current behavior:** senza `LICENSE_KEY`/license file →
  `valid=false`, entitlement vuoti → tutte le feature premium bloccate.
- **Desired behavior:** con flag attivo → `getLicensingState()` ritorna
  `valid=true`, `hasLicense=true`, `planName="Self-Hosted"`,
  `entitlements = <tutti i valori dell'enum>` (o lista da config), senza chiamare
  Keygen. Con flag disattivo → comportamento attuale invariato.
- **Punto di innesto suggerito:** inizio di `getLicensingState()` (prima dei
  controlli `hasLicenseKey/hasLicenseFile`), ritornare lo stato self-hosted.
- **Risk:** Basso (una diramazione, isolata, default off).
- **Dependencies:** nessuna per il core. Le feature che richiedono servizi
  esterni (LDAP/SMTP/storage) restano subordinate alla relativa configurazione.
- **Tests required:** vedi [12-test-plan.md](12-test-plan.md) (T-LIC-*).
- **Rollback:** impostare il flag a `false` (o revert del commit).

### Variante MOD-001b — lista entitlement configurabile

Invece di "tutti", accettare `SELF_HOSTED_ENTITLEMENTS` (CSV) per abilitare solo
un sottoinsieme (es. escludere `MULTI_INSTANCE`). Utile se si vuole granularità.
Se adottata, **allineare** anche `frontend/src/models/owns/license.ts` (correggere
`UNLIMITED_CHECKLIST` → `UNLIMITED_CHECKLISTS` e aggiungere gli entitlement
mancanti).

---

## MOD-002 — Limiti numerici illimitati

- **Feature:** Unlimited Assets/Users/Locations/Parts/PM/WO/Checklists/Meters.
- **Files to modify:** nessuno aggiuntivo se MOD-001 include gli entitlement
  `UNLIMITED_*` (i controlli in `Consts.usageBasedFreeLimits` si disattivano da
  soli quando `hasEntitlement(UNLIMITED_*)` è true).
- **Current:** limiti free (Assets 50, Users 5, …) attivi.
- **Desired:** con MOD-001 attivo, gli `UNLIMITED_*` sono presenti → limiti
  bypassati per design, senza toccare i service.
- **Risk:** Basso. **Rollback:** flag off.

> Alternativa (se si volesse un limite alto ma finito) — parametrizzare
> `usageBasedFreeLimits` da config. **Non raccomandata**: più invasiva di MOD-001.

---

## MOD-003 — LDAP / Active Directory

- **Feature:** autenticazione LDAP/AD + sync.
- **Files to modify:** nessuna modifica di codice necessaria (feature completa).
- **Config richiesta:** `LDAP_ENABLED=true`, `LDAP_URL`, `LDAP_BASE_DN`,
  attributi AD (`LDAP_ATTR_USERNAME=sAMAccountName`, `LDAP_OBJECT_CLASS=user`,
  filtro), service account, `LDAP_OU_ROLE_MAPPINGS`. Richiede l'entitlement `SSO`
  (fornito da MOD-001).
- **Dependencies:** server LDAP/AD raggiungibile (esterno).
- **Risk:** Medio (dipende da AD reale). **Rollback:** `LDAP_ENABLED=false`.
- **Tests:** T-LDAP-* (integrazione, idealmente con LDAP in container di test).

---

## MOD-004 — File attachments / storage

- **Feature:** allegati.
- **Files to modify:** nessuno (gate `FILE_ATTACHMENTS` sbloccato da MOD-001;
  `PlanFeatures.FILE` già ok).
- **Config richiesta:** `STORAGE_TYPE=MINIO` + credenziali MinIO
  (già in `docker-compose.yml`).
- **Risk:** Basso. **Rollback:** flag off / storage non configurato.

---

## MOD-005 — Email notifications

- **Feature:** email (inviti, notifiche, low stock).
- **Files to modify:** nessuno (non c'è entitlement dedicato; è env-gated).
- **Config richiesta:** `SMTP_*` e/o SendGrid, `INVITATION_VIA_EMAIL=true` se
  necessario.
- **Risk:** Basso. **Rollback:** rimuovere config SMTP.

---

## MOD-006 — Pulizia gate morti (opzionale)

- `PARTS_COST_TRACKING` e `ADVANCED_ANALYTICS` sono definiti ma non applicati.
  Non richiedono azione per lo sblocco. Opzionalmente documentare/decidere se
  applicarli o rimuoverli per chiarezza. **Priorità bassa.**

---

## Ordine di implementazione consigliato

1. **MOD-001** (core, sblocca la maggior parte delle feature) + relativi test.
2. **MOD-004** + **MOD-005** (storage + email: config, alto valore operativo).
3. **MOD-003** (LDAP/AD: config + AD reale).
4. Verifica end-to-end (limiti illimitati, WO history, PM/asset hierarchy).
5. MOD-006 (opzionale, cosmetico).

Ogni MOD → **commit separato** con messaggio che ne spiega scopo e reversibilità.
