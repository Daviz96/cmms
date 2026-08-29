# 12 — Test Plan

> Piano, non implementazione. In fase di audit **non** si creano test in massa.

---

## 1. Infrastruttura di test esistente (da riusare)

- **77 file** di test in `api/src/test` (JUnit 5).
- **Integration test con Testcontainers**:
  [`integration/AbstractTestContainer.java`](../../api/src/test/java/com/grash/integration/AbstractTestContainer.java),
  [`integration/AbstractIntegrationTest.java`](../../api/src/test/java/com/grash/integration/AbstractIntegrationTest.java)
  (es. `AssetIntegrationTest`, `UserIntegrationTest`, `WorkOrderIntegrationTest`,
  `RoleIntegrationTest`).
- **Controller test**: `AbstractControllerTest` + `AssetControllerTest`,
  `WorkOrderControllerTest`, `UserControllerTest`, `RoleControllerTest`,
  `SubscriptionControllerTest`.
- **Config test**: `LdapSecurityConfigTest`, `StorageServiceFactoryTest`,
  `MailServiceFactoryTest`, ecc.
- **Esistente sul licensing**:
  [`utils/LicenseFileValidatorTest.java`](../../api/src/test/java/com/grash/utils/LicenseFileValidatorTest.java)
  (solo validazione file, **non** il comportamento dei gate).

**Gap principale:** non esistono test che verifichino il comportamento di
`hasEntitlement` e dei gate nei service (limiti, throw/skip). Vanno aggiunti a
supporto di MOD-001.

---

## 2. Test da aggiungere per la Fase 2

### T-LIC — LicenseService / modalità self-hosted (MOD-001)

- `T-LIC-01` self-hosted-mode **off** + nessuna licenza → `valid=false`,
  entitlement vuoti (comportamento attuale invariato).
- `T-LIC-02` self-hosted-mode **on** → `valid=true`, `hasEntitlement(X)==true`
  per ogni `X` dell'enum, **senza** chiamate Keygen (mock `RestTemplate`: nessuna
  invocazione).
- `T-LIC-03` self-hosted-mode on → `planName`/`hasLicense` coerenti in
  `/license/state` (controller test).
- `T-LIC-04` (variante MOD-001b) lista `SELF_HOSTED_ENTITLEMENTS` parziale →
  solo gli entitlement elencati risultano attivi.

### T-LIMIT — Limiti numerici (`usageBasedFreeLimits`)

Per Asset (modello per gli altri: User, Location, Part, PM, WO, Checklist, Meter):

- `T-LIMIT-ASSET-01` conteggio `<= limite` senza entitlement → creazione OK.
- `T-LIMIT-ASSET-02` conteggio `> limite` senza entitlement → `CustomException`
  403 "Free Limit reached".
- `T-LIMIT-ASSET-03` con `UNLIMITED_ASSETS` (self-hosted-mode on) → nessun limite.

### T-GATE — Gate entitlement per feature (parametrico)

Test parametrico su ciascun gate 🟢 (esempi):

- `T-GATE-WOHISTORY` senza `WORK_ORDER_HISTORY` → `findByWorkOrder` ritorna lista
  vuota; con entitlement → ritorna revisioni Envers (autore/timestamp).
- `T-GATE-ROLE` creazione ruolo `USER_CREATED` senza `CUSTOM_ROLES` → 403; con →
  OK.
- `T-GATE-HIERARCHY` creazione asset con `parentAsset` senza `ASSET_HIERARCHY` →
  403; con → OK.
- `T-GATE-CONDPM` `WorkOrderMeterTrigger.create` senza `CONDITION_BASED_PM` →
  403; con → OK.
- `T-GATE-FILE` upload senza `FILE_ATTACHMENTS` → 403; con entitlement +
  `PlanFeatures.FILE` → OK (storage mockato).
- `T-GATE-WEBHOOK` / `T-GATE-API` verifica del **doppio** gate (entitlement +
  `PlanFeatures`).

### T-LDAP — LDAP/AD (MOD-003)

- `T-LDAP-01` `ldap.enabled=true` **senza** entitlement `SSO` → il bean
  `contextSource` lancia `IllegalStateException` (estende `LdapSecurityConfigTest`).
- `T-LDAP-02` con `SSO` → contesto LDAP inizializzato.
- `T-LDAP-03` (integrazione) login LDAP contro un LDAP in container → token
  emesso, utente creato/aggiornato.
- `T-LDAP-04` mapping OU→ruolo: utente in OU X → ruolo atteso.

### T-SUB — Subscription/PlanFeatures (regressione)

- `T-SUB-01` nuova azienda self-hosted → piano `BUSINESS`, `endsOn=null`.
- `T-SUB-02` `SubscriptionEndJob` **non** schedulato quando `endsOn=null`
  (nessun downgrade in self-hosted).

---

## 3. Come eseguire

```bash
cd api
./mvnw.cmd test                      # tutta la suite (Windows)
./mvnw.cmd test -Dtest=LicenseServiceTest   # singola classe
```

Gli integration test usano Testcontainers (richiedono Docker attivo).

---

## 4. Criteri di accettazione Fase 2

1. Con `SELF_HOSTED_UNLIMITED=false` **tutti i test esistenti** restano verdi
   (nessuna regressione).
2. Con `SELF_HOSTED_UNLIMITED=true` i test T-LIC/T-LIMIT/T-GATE dimostrano lo
   sblocco senza chiamate Keygen.
3. Nessun controllo **non** legato alla licenza (permessi ruolo, multi-tenant,
   rate limit) risulta indebolito.
