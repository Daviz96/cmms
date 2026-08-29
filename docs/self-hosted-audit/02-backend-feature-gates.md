# 02 — Backend Feature Gates

Mappa dei controlli lato server. Tre categorie: (A) entitlement Keygen,
(B) `PlanFeatures` sulla subscription, (C) limiti numerici.

---

## A. Gate per entitlement Keygen (`licenseService.hasEntitlement`)

Elenco `file:riga → entitlement` (fonte: grep su `api/src/main/java`):

```
AdditionalCostService.java:33      -> COST_TRACKING
ApiKeyAuthFilter.java:70           -> API_ACCESS        (+ PlanFeatures.API_ACCESS)
ApiKeyService.java:49              -> API_ACCESS        (+ PlanFeatures.API_ACCESS)
AssetDowntimeService.java:26       -> ASSET_DOWNTIME
AssetService.java:91               -> ASSET_HIERARCHY   (create, parent asset)
AssetService.java:128              -> ASSET_HIERARCHY   (patch, parent asset)
AssetService.java:151              -> UNLIMITED_ASSETS  (limite numerico)
AssetService.java:325              -> NFC_BARCODE       (scan)
AssetService.java:331              -> NFC_BARCODE       (scan)
AssetService.java:488              -> ASSET_HIERARCHY   (import con gerarchia)
BrandingService.java:47            -> BRANDING
ChecklistService.java:72           -> UNLIMITED_CHECKLISTS (limite numerico)
CustomerService.java:37            -> CUSTOMER_VENDOR
FieldConfigurationController.java:40 -> FIELD_CONFIGURATION (+ PlanFeatures.REQUEST_CONFIGURATION)
FileController.java:69             -> FILE_ATTACHMENTS  (+ PlanFeatures.FILE)
LaborService.java:39               -> TIME_TRACKING
LdapSecurityConfig.java:62         -> SSO               (throw IllegalStateException)
LocationService.java:92            -> UNLIMITED_LOCATIONS (limite numerico)
MeterService.java:69               -> UNLIMITED_METERS  (limite numerico)
PartService.java:132               -> UNLIMITED_PARTS   (limite numerico)
PartService.java:161               -> LOW_STOCK_ALERTS  (soft: invio notifica)
PreventiveMaintenanceService.java:172 -> UNLIMITED_PM_SCHEDULES (limite numerico)
PreventiveMaintenanceService.java:211 -> PM_CALENDAR
RelationService.java:72            -> WORK_ORDER_LINKING
RequestPortalService.java:38       -> REQUEST_PORTAL    (+ PlanFeatures.REQUEST_PORTAL)
RequestPortalService.java:88       -> REQUEST_PORTAL    (+ PlanFeatures.REQUEST_PORTAL)
RequestService.java:58             -> VOICE_NOTES
RequestService.java:76             -> VOICE_NOTES
RoleService.java:32                -> CUSTOM_ROLES
UserService.java:136               -> UNLIMITED_USERS   (limite numerico)
UserService.java:190               -> MULTI_INSTANCE    (2ª company)
VendorService.java:43              -> CUSTOMER_VENDOR
WebhookDispatchService.java:62     -> WEBHOOK           (+ PlanFeatures.WEBHOOK)
WebhookEndpointService.java:33     -> WEBHOOK           (+ PlanFeatures.WEBHOOK)
WebhookEndpointService.java:51     -> WEBHOOK           (+ PlanFeatures.WEBHOOK)
WorkOrderHistoryService.java:51    -> WORK_ORDER_HISTORY (ritorna lista vuota)
WorkOrderMeterTriggerService.java:37 -> CONDITION_BASED_PM (throw)
WorkOrderService.java:830          -> SIGNATURE_CAPTURE (+ PlanFeatures.SIGNATURE)
WorkflowController.java:69         -> WORKFLOW          (+ PlanFeatures.WORKFLOW; 1 gratis)
WorkloadController.java:84         -> RESOURCE_PLANNING (+ PlanFeatures.RESOURCE_PLANNING)
```

### Tipi di enforcement

- **Hard throw** (`CustomException`/`IllegalStateException`, HTTP 403): la
  maggioranza — blocca l'operazione se manca l'entitlement.
- **Soft skip** (ritorno silenzioso senza errore):
  - `WorkOrderHistoryService.findByWorkOrder` → ritorna `new ArrayList<>()`.
  - `WebhookDispatchService` → `return` (non invia webhook).
  - `PartService` low stock → non invia la notifica di scorta bassa.

---

## B. Gate per `PlanFeatures` sulla subscription (Livello B)

Enum: [`model/enums/PlanFeatures.java`](../../api/src/main/java/com/grash/model/enums/PlanFeatures.java)
(18 valori). Salvato in `SubscriptionPlan.features`
([`model/SubscriptionPlan.java:42`](../../api/src/main/java/com/grash/model/SubscriptionPlan.java#L42)).

Punti che controllano `...getSubscriptionPlan().getFeatures().contains(PlanFeatures.X)`:

```
AdditionalCostController.java:70   -> ADDITIONAL_COST
ChecklistController.java:66        -> CHECKLIST
FieldConfigurationController.java:53 -> REQUEST_CONFIGURATION
FileController.java:77             -> FILE
ImportController.java:52..138      -> IMPORT_CSV (x6)
LaborController.java:115           -> ADDITIONAL_TIME
MeterController.java:109           -> METER
PurchaseOrderController.java:100   -> PURCHASE_ORDER
ReadingController.java:96          -> METER
RoleController.java:68             -> ROLE
WorkflowController.java:69         -> WORKFLOW
WorkloadController.java:90         -> RESOURCE_PLANNING
User.java:146 (canReadAnalytics)   -> ANALYTICS
ApiKeyAuthFilter.java:70           -> API_ACCESS
ApiKeyService.java:48              -> API_ACCESS
AsyncImportService.java:29..139    -> IMPORT_CSV (x6)
PreventiveMaintenanceService.java:70/90 -> PREVENTIVE_MAINTENANCE
RequestPortalService.java:38/88    -> REQUEST_PORTAL
WebhookDispatchService.java:62     -> WEBHOOK
WebhookEndpointService.java:36/54  -> WEBHOOK
WorkOrderService.java:850/1077     -> SIGNATURE
```

### Perché il Livello B è "aperto" in self-hosted

Piani seed in [`ApplicationInitializer.initializeSubscriptionPlans()`](../../api/src/main/java/com/grash/ApplicationInitializer.java#L79):

| Piano | Feature |
|---|---|
| `FREE` | *(nessuna)* |
| `STARTER` | PREVENTIVE_MAINTENANCE, CHECKLIST, FILE, METER, ADDITIONAL_COST, ADDITIONAL_TIME |
| `PROFESSIONAL` | + REQUEST_CONFIGURATION, SIGNATURE, ANALYTICS, IMPORT_CSV, REQUEST_PORTAL |
| `BUSINESS` | **tutte** (`PlanFeatures.values()`) |

Assegnazione al signup ([`UserService.java:192-197`](../../api/src/main/java/com/grash/service/UserService.java#L192)):

```java
Subscription subscription = Subscription.builder()
        .usersCount(300).monthly(cloudVersion)
        .startsOn(new Date())
        .endsOn(cloudVersion ? Helper.incrementDays(new Date(), 15) : null)
        .subscriptionPlan(subscriptionPlanService.findByCode("BUSINESS").get())
        .build();
```

⇒ In self-hosted (`cloud-version=false`): piano **BUSINESS**, `endsOn=null`.
Il downgrade automatico non parte mai perché
[`SubscriptionService.java:119`](../../api/src/main/java/com/grash/service/SubscriptionService.java#L119)
schedula `SubscriptionEndJob` solo se `endsOn != null`.

---

## C. Limiti numerici free-tier (`Consts.usageBasedFreeLimits`)

[`utils/Consts.java:52`](../../api/src/main/java/com/grash/utils/Consts.java#L52):

| Entitlement `UNLIMITED_*` | Limite free | Service | Metodo repository |
|---|---|---|---|
| `UNLIMITED_ASSETS` | 50 | `AssetService:151` | `assetRepository.hasMoreThan` |
| `UNLIMITED_LOCATIONS` | 10 | `LocationService:92` | |
| `UNLIMITED_PARTS` | 100 | `PartService:132` | |
| `UNLIMITED_PM_SCHEDULES` | 10 | `PreventiveMaintenanceService:172` | |
| `UNLIMITED_ACTIVE_WORK_ORDERS` | 30 | `WorkOrderService:184` | `workOrderRepository.hasMoreActiveThan` |
| `UNLIMITED_CHECKLISTS` | 10 | `ChecklistService:72` | |
| `UNLIMITED_METERS` | 10 | `MeterService:69` | |
| `UNLIMITED_USERS` | 5 | `UserService:136` | |

Pattern tipico (esempio `AssetService`):

```java
// AssetService.java:151
Integer threshold = usageBasedFreeLimits.get(LicenseEntitlement.UNLIMITED_ASSETS);
if (!licenseService.hasEntitlement(LicenseEntitlement.UNLIMITED_ASSETS)
        && assetRepository.hasMoreThan(company.getId(), threshold.longValue() - 1 ...))
    throw new CustomException("You need a license to add a new asset. Free Limit reached: " + threshold, ...);
```

Il limite si applica **solo** in assenza dell'entitlement `UNLIMITED_*`
corrispondente.

---

## Flusso di verifica canonico (per ogni feature)

```
Frontend (hasFeature / useLicenseEntitlement)
   ↓
API (controller)
   ↓
Service
   ↓  hasEntitlement(...)  [Livello A]  +  getFeatures().contains(...) [Livello B]  +  usageBasedFreeLimits [Livello C]
   ↓
Repository / DB
```
