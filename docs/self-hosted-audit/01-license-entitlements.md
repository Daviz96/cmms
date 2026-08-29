# 01 — License Entitlements (Livello A)

Inventario completo del sistema di entitlement basato su Keygen.

---

## 1. Componenti

| Componente | File |
|---|---|
| Enum entitlement | [`dto/license/LicenseEntitlement.java`](../../api/src/main/java/com/grash/dto/license/LicenseEntitlement.java) |
| Service di validazione | [`service/LicenseService.java`](../../api/src/main/java/com/grash/service/LicenseService.java) |
| Stato licenza (DTO) | [`dto/license/LicensingState.java`](../../api/src/main/java/com/grash/dto/license/LicensingState.java) |
| Endpoint stato | [`controller/LicenseController.java`](../../api/src/main/java/com/grash/controller/LicenseController.java) → `GET /license/state` |
| Validazione file offline | [`utils/LicenseFileValidator.java`](../../api/src/main/java/com/grash/utils/LicenseFileValidator.java) |
| DTO Keygen | `dto/keygen/*`, `dto/license/*` |
| Limiti free-tier | [`utils/Consts.java`](../../api/src/main/java/com/grash/utils/Consts.java) |
| Frontend hook | [`hooks/useLicenseEntitlement.ts`](../../frontend/src/hooks/useLicenseEntitlement.ts) |
| Frontend slice | [`slices/license.ts`](../../frontend/src/slices/license.ts) |

---

## 2. Come funziona `hasEntitlement`

```java
// LicenseService.java:78
public boolean hasEntitlement(LicenseEntitlement entitlement) {
    LicensingState state = getLicensingState();
    return state.isValid() && state.getEntitlements().contains(entitlement.toString());
}
```

`getLicensingState()` ([`LicenseService.java:56`](../../api/src/main/java/com/grash/service/LicenseService.java#L56)):

1. Se la cache (12h) è valida → usa la cache.
2. Se **non** c'è né `license-key` né `license-file-path` →
   `clearCacheAndReturnInvalid()` → `hasLicense=false, valid=false`.
3. Se c'è un license file → `validateAndCacheLicenseFile()` (decrittazione
   offline con chiave pubblica Keygen `KEYGEN_PUBLIC_KEY`).
4. Altrimenti → `validateAndCacheLicenseKey()` (chiamata REST a
   `https://api.keygen.sh/v1/accounts/{accountId}/licenses/actions/validate-key`,
   poi fetch entitlement).

**Limite anti-abuso:** max `DAILY_REQUEST_LIMIT = 20` chiamate Keygen/giorno,
tracciate in tabella `KeygenRequestTracker`.

**Esito in self-hosted senza licenza:** `valid=false` ⇒ `hasEntitlement()`
ritorna **sempre false** per qualunque entitlement.

---

## 3. Configurazione (application.yml)

| Proprietà | Env var | Default | Nota |
|---|---|---|---|
| `license-key` | `LICENSE_KEY` | *(vuoto)* | Chiave licenza Keygen |
| `license-file-path` | `LICENSE_FILE_PATH` | *(vuoto)* | Percorso license file offline |
| `license-fingerprint-required` | `LICENSE_FINGERPRINT_REQUIRED` | `true` | Vincola la licenza a un fingerprint macchina |
| `keygen.account-id` | — | `1ca3e517-f3d8-473f-a45c-81069900acb7` (hardcoded) | Account Keygen del vendor |
| `keygen.product-token` | `KEYGEN_PRODUCT_TOKEN` | *(vuoto)* | |

Riferimento: [`api/src/main/resources/application.yml:157-159`](../../api/src/main/resources/application.yml#L157).

---

## 4. Inventario completo entitlement (enum `LicenseEntitlement`, 33 valori)

| # | Entitlement | Applicato nel backend? | Punto di enforcement | Classe |
|---|---|---|---|---|
| 1 | `SSO` | ✅ | `LdapSecurityConfig.contextSource` (throw); `LicenseService.isSSOEnabled` | 🟡 |
| 2 | `WORK_ORDER_HISTORY` | ✅ | `WorkOrderHistoryService.findByWorkOrder` (ritorna lista vuota) | 🟢 |
| 3 | `WORKFLOW` | ✅ | `WorkflowController:69` (+ `PlanFeatures.WORKFLOW`; 1 workflow gratis) | 🟢 |
| 4 | `MULTI_INSTANCE` | ✅ | `UserService:190` (2ª company) | 🟢 |
| 5 | `WEBHOOK` | ✅ | `WebhookDispatchService:62`, `WebhookEndpointService:33/51` (+ `PlanFeatures.WEBHOOK`) | 🟢 |
| 6 | `BRANDING` | ✅ | `BrandingService:47`; frontend `useBrand`/`App.tsx` | 🟢 |
| 7 | `NFC_BARCODE` | ✅ | `AssetService:325/331` (scan) | 🟢 |
| 8 | `CUSTOM_ROLES` | ✅ | `RoleService:32` (ruoli `USER_CREATED`) | 🟢 |
| 9 | `FILE_ATTACHMENTS` | ✅ | `FileController:69` (+ `PlanFeatures.FILE`) | 🟡 |
| 10 | `TIME_TRACKING` | ✅ | `LaborService:39` | 🟢 |
| 11 | `COST_TRACKING` | ✅ | `AdditionalCostService:33` | 🟢 |
| 12 | `WORK_ORDER_LINKING` | ✅ | `RelationService:72` | 🟢 |
| 13 | `SIGNATURE_CAPTURE` | ✅ | `WorkOrderService:830` (+ `PlanFeatures.SIGNATURE`) | 🟢 |
| 14 | `PM_CALENDAR` | ✅ | `PreventiveMaintenanceService:211` | 🟢 |
| 15 | `CONDITION_BASED_PM` | ✅ | `WorkOrderMeterTriggerService:37` (throw) | 🟢 |
| 16 | `ASSET_HIERARCHY` | ✅ | `AssetService:91/128/488` (parent asset/import) | 🟢 |
| 17 | `ASSET_DOWNTIME` | ✅ | `AssetDowntimeService:26` | 🟢 |
| 18 | `LOW_STOCK_ALERTS` | ✅ | `PartService:161` (soft: invia notifica) | 🟢 |
| 19 | `PARTS_COST_TRACKING` | ❌ **mai usato** | — (solo enum + lista frontend) | ⚪ |
| 20 | `CUSTOMER_VENDOR` | ✅ | `CustomerService:37`, `VendorService:43` | 🟢 |
| 21 | `FIELD_CONFIGURATION` | ✅ | `FieldConfigurationController:40` (+ `PlanFeatures.REQUEST_CONFIGURATION`) | 🟢 |
| 22 | `VOICE_NOTES` | ✅ | `RequestService:58/76` (audioDescription) | 🟢 |
| 23 | `ADVANCED_ANALYTICS` | ❌ **mai usato** | — (analytics gate da `PlanFeatures.ANALYTICS`) | ⚪ |
| 24 | `API_ACCESS` | ✅ | `ApiKeyAuthFilter:70`, `ApiKeyService:49` (+ `PlanFeatures.API_ACCESS`) | 🟢 |
| 25 | `UNLIMITED_ASSETS` | ✅ | `AssetService:151` (limite free 50) | 🟢 |
| 26 | `UNLIMITED_LOCATIONS` | ✅ | `LocationService:92` (limite free 10) | 🟢 |
| 27 | `UNLIMITED_PARTS` | ✅ | `PartService:132` (limite free 100) | 🟢 |
| 28 | `UNLIMITED_PM_SCHEDULES` | ✅ | `PreventiveMaintenanceService:172` (limite free 10) | 🟢 |
| 29 | `UNLIMITED_ACTIVE_WORK_ORDERS` | ✅ | `WorkOrderService:184` (limite free 30) | 🟢 |
| 30 | `UNLIMITED_CHECKLISTS` | ✅ | `ChecklistService:72` (limite free 10) | 🟢 |
| 31 | `UNLIMITED_METERS` | ✅ | `MeterService:69` (limite free 10) | 🟢 |
| 32 | `UNLIMITED_USERS` | ✅ | `UserService:136` (limite free 5) | 🟢 |
| 33 | `REQUEST_PORTAL` | ✅ | `RequestPortalService:38/88` (+ `PlanFeatures.REQUEST_PORTAL`) | 🟢 |
| 34 | `RESOURCE_PLANNING` | ✅ | `WorkloadController:84` (+ `PlanFeatures.RESOURCE_PLANNING`) | 🟢 |

> Nota: l'enum contiene 33 costanti; la tabella elenca 34 righe perché
> `RESOURCE_PLANNING` è l'ultimo valore — ricontrollare l'enum sorgente come
> riferimento canonico. Le classi (🟢🟡⚪) sono spiegate in
> [04-feature-matrix.md](04-feature-matrix.md).

---

## 5. Discrepanze frontend/backend rilevate

La lista frontend [`models/owns/license.ts`](../../frontend/src/models/owns/license.ts)
**non coincide** con l'enum backend:

- Frontend usa `UNLIMITED_CHECKLIST` (singolare); backend usa
  `UNLIMITED_CHECKLISTS` (plurale). → un eventuale entitlement checklist non
  verrebbe riconosciuto lato frontend.
- Frontend **non elenca** `UNLIMITED_USERS`, `REQUEST_PORTAL`,
  `FIELD_CONFIGURATION`, `CUSTOMER_VENDOR` (verificare l'elenco esatto).

Da tenere presente per la Fase 2 se si sceglie una modalità basata su lista
entitlement esplicita.

---

## 6. Integrazione Paddle (billing)

Paddle (`service/PaddleService.java`, `controller/WebhookController.java`,
`controller/PaddleController.java`) gestisce **solo l'acquisto/rinnovo** delle
licenze e la creazione delle subscription (cloud e self-hosted checkout). Non è
un gate funzionale: è protetto da `cloud-version` e non incide sulle feature una
volta assegnato il piano. In self-hosted senza Paddle configurato non viene mai
invocato.
