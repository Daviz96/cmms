# 04 — Feature Matrix

Legenda classificazione:

- 🟢 **UNLOCK_SIMPLE** — feature completa, bloccata solo da entitlement.
- 🟡 **UNLOCK_PLUS_MODIFICATION** — feature esiste ma richiede config +
  dipendenza esterna (non un componente commerciale mancante).
- 🟠 **SIGNIFICANT_MODIFICATION** — parzialmente implementata / dipende da servizi
  commerciali.
- 🔴 **NOT_IMPLEMENTED** — non realmente implementata.
- ⚪ **ALREADY_AVAILABLE** — già disponibile senza interventi in self-hosted.

`Backend` = gate entitlement lato server. `Plan` = anche gate `PlanFeatures`.
`Frontend` = gate lato UI. `Ext` = dipendenza esterna reale.

| Feature | Entitlement (Livello A) | Plan (Livello B) | Frontend | DB / Model | Ext | Classe | Prio | File chiave |
|---|---|---|---|---|---|---|---|---|
| Unlimited Assets | `UNLIMITED_ASSETS` (limite 50) | — | — | Asset | — | 🟢 | Alta | `AssetService.java:151` |
| Unlimited Users | `UNLIMITED_USERS` (limite 5) | — | — | User/Subscription | — | 🟢 | Alta | `UserService.java:136` |
| Unlimited Locations | `UNLIMITED_LOCATIONS` (limite 10) | — | — | Location | — | 🟢 | Alta | `LocationService.java:92` |
| Unlimited Parts | `UNLIMITED_PARTS` (limite 100) | — | — | Part | — | 🟢 | Alta | `PartService.java:132` |
| Unlimited PM Schedules | `UNLIMITED_PM_SCHEDULES` (limite 10) | — | — | PreventiveMaintenance | — | 🟢 | Alta | `PreventiveMaintenanceService.java:172` |
| Unlimited Active WO | `UNLIMITED_ACTIVE_WORK_ORDERS` (limite 30) | — | — | WorkOrder | — | 🟢 | Alta | `WorkOrderService.java:184` |
| Unlimited Checklists | `UNLIMITED_CHECKLISTS` (limite 10) | — | — | Checklist | — | 🟢 | Media | `ChecklistService.java:72` |
| Unlimited Meters | `UNLIMITED_METERS` (limite 10) | — | — | Meter | — | 🟢 | Media | `MeterService.java:69` |
| Asset Hierarchy | `ASSET_HIERARCHY` | — | Assets UI | Asset.parentAsset | — | 🟢 | Alta | `AssetService.java:91/128/488` |
| Custom Roles | `CUSTOM_ROLES` | `ROLE` | Roles UI | Role (USER_CREATED) | — | 🟢 | Alta | `RoleService.java:32` |
| Custom Permissions | *(via ruoli)* | `ROLE` | Roles UI | Role permissions | — | 🟢 | Alta | `RoleController.java:68` |
| Work Order History | `WORK_ORDER_HISTORY` | — | WO Details | Envers `WorkOrderAud` | — | 🟢 | Alta | `WorkOrderHistoryService.java:51` |
| File Attachments | `FILE_ATTACHMENTS` | `FILE` | Upload UI | File + storage | ✅ MinIO/GCP/FS | 🟡 | Alta | `FileController.java:69/77` |
| Checklists | *(base)* | `CHECKLIST` | Checklists | Checklist | — | ⚪ | Media | `ChecklistController.java:66` |
| Parts / Inventory | *(base)* | — | Inventory | Part | — | ⚪ | Media | — |
| PM Schedules | *(base)* | `PREVENTIVE_MAINTENANCE` | PM | Schedule | — | ⚪ | Alta | `PreventiveMaintenanceService.java:70` |
| PM Calendar view | `PM_CALENDAR` | — | PM calendar | Schedule | — | 🟢 | Media | `PreventiveMaintenanceService.java:211` |
| Meters (base) | *(base)* | `METER` | Meters | Meter/Reading | — | ⚪ | Media | `MeterController.java:109`, `ReadingController.java:96` |
| Meter-based / Condition PM | `CONDITION_BASED_PM` | — | Meters/Triggers | WorkOrderMeterTrigger | — | 🟢 | Alta | `WorkOrderMeterTriggerService.java:37` |
| Asset Downtime | `ASSET_DOWNTIME` | — | Asset UI | AssetDowntime | — | 🟢 | Media | `AssetDowntimeService.java:26` |
| Work Order Linking | `WORK_ORDER_LINKING` | — | WO UI | Relation | — | 🟢 | Media | `RelationService.java:72` |
| Labor / Time Tracking | `TIME_TRACKING` | `ADDITIONAL_TIME` | AddTime modal | Labor | — | 🟢 | Alta | `LaborService.java:39`, `LaborController.java:115` |
| Cost Tracking | `COST_TRACKING` | `ADDITIONAL_COST` | AddCost modal | AdditionalCost | — | 🟢 | Alta | `AdditionalCostService.java:33` |
| Signature Capture | `SIGNATURE_CAPTURE` | `SIGNATURE` | WO complete | WorkOrder.signature | — | 🟢 | Media | `WorkOrderService.java:830` |
| Customers | `CUSTOMER_VENDOR` | — | Customers | Customer | — | 🟢 | Media | `CustomerService.java:37` |
| Vendors | `CUSTOMER_VENDOR` | — | Vendors | Vendor | — | 🟢 | Media | `VendorService.java:43` |
| Request Portal | `REQUEST_PORTAL` | `REQUEST_PORTAL` | Request Portal | RequestPortal | — | 🟢 | Media | `RequestPortalService.java:38/88` |
| Field Configuration | `FIELD_CONFIGURATION` | `REQUEST_CONFIGURATION` | Configure Fields | FieldConfiguration | — | 🟢 | Media | `FieldConfigurationController.java:40/53` |
| Voice Notes | `VOICE_NOTES` | — | Request audio | Request.audioDescription | — | 🟢 | Bassa | `RequestService.java:58/76` |
| NFC / Barcode | `NFC_BARCODE` | — | Asset scan | Asset barcode/nfc | — | 🟢 | Bassa | `AssetService.java:325/331` |
| Low Stock Alerts | `LOW_STOCK_ALERTS` | — | — | Part.minQuantity | (SMTP per email) | 🟢 | Bassa | `PartService.java:161` |
| Notifications | *(base)* | — | Notifiche UI | Notification | — | ⚪ | Media | — |
| Email Notifications | *(config)* | — | — | — | ✅ SMTP/SendGrid | 🟡 | Alta | env `SMTP_*` / `INVITATION_VIA_EMAIL` |
| API (API keys) | `API_ACCESS` | `API_ACCESS` | Integrations | ApiKey | — | 🟢 | Media | `ApiKeyAuthFilter.java:70` |
| Webhooks | `WEBHOOK` | `WEBHOOK` | Integrations | WebhookEndpoint | (endpoint esterni) | 🟢 | Media | `WebhookDispatchService.java:62` |
| Workflow | `WORKFLOW` | `WORKFLOW` | Workflows | Workflow | — | 🟢 | Media | `WorkflowController.java:69` |
| Resource Planning | `RESOURCE_PLANNING` | `RESOURCE_PLANNING` | Workload | — | — | 🟢 | Bassa | `WorkloadController.java:84/90` |
| Branding / White-label | `BRANDING` | — | App logo/theme | — | (env LOGO/COLORS) | 🟢 | Bassa | `BrandingService.java:47`, `App.tsx:113` |
| Multi-instance (più company) | `MULTI_INSTANCE` | — | — | Company | — | 🟢 | Bassa | `UserService.java:190` |
| Import CSV | *(base)* | `IMPORT_CSV` | Imports | — | — | ⚪ | Media | `ImportController.java`, `AsyncImportService.java` |
| Purchase Orders | *(base)* | `PURCHASE_ORDER` | Purchase Orders | PurchaseOrder | — | ⚪ | Media | `PurchaseOrderController.java:100` |
| Analytics (base) | *(base)* | `ANALYTICS` | Analytics | — | — | ⚪ | Media | `User.java:146` |
| Advanced Analytics | `ADVANCED_ANALYTICS` **(mai applicato)** | `ANALYTICS` | Analytics | — | — | ⚪ | Bassa | *(gate morto)* |
| Parts Cost Tracking | `PARTS_COST_TRACKING` **(mai applicato)** | — | Parts | Part.cost | — | ⚪ | Bassa | *(gate morto)* |
| LDAP | `SSO` | — | login LDAP | User (ldap) | ✅ server LDAP/AD | 🟡 | Alta | `LdapSecurityConfig.java:62`, `LdapService.java` |
| SSO / OAuth2 | `enable-sso` (config) / `SSO` | — | OAuth2 flow | — | ✅ provider OAuth2 | 🟡 | Alta | `OAuth2AuthenticationSuccessHandler.java` |

---

## Note di lettura

- **"limite N"** indica un limite numerico free-tier attivo finché manca
  l'entitlement `UNLIMITED_*` (vedi
  [02-backend-feature-gates.md](02-backend-feature-gates.md) sez. C).
- Le feature ⚪ **ALREADY_AVAILABLE** lo sono perché in self-hosted il piano
  BUSINESS soddisfa il gate `PlanFeatures` e non esiste un gate entitlement
  aggiuntivo.
- Le feature 🟡 richiedono **configurazione + servizio esterno**, non codice
  commerciale: sono sbloccabili ma vanno predisposti LDAP/AD, OAuth2, MinIO, SMTP.
- **Nessuna feature 🔴 / 🟠** tra quelle prioritarie: il blocco è essenzialmente
  commerciale (entitlement), non tecnico.
