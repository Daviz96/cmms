# 03 — Frontend Feature Gates

Il frontend (React + Redux, `frontend/src`) usa **due** meccanismi paralleli,
speculari ai livelli backend.

---

## 1. `useAuth().hasFeature(feature: PlanFeature)` — Livello B (subscription)

Definizione: [`contexts/JWTAuthContext.tsx:987`](../../frontend/src/contexts/JWTAuthContext.tsx#L987)

```ts
const hasFeature = (feature: PlanFeature) => {
  return state.company.subscription.subscriptionPlan.features.includes(feature);
};
```

Legge le feature del piano della company. Poiché in self-hosted la company ha il
piano **BUSINESS**, `hasFeature(...)` ritorna **true per ogni feature** →
i menu/pulsanti gate da `hasFeature` sono **già visibili**.

Esempi d'uso: sidebar (`item.planFeature`), Analytics, Imports, Inventory,
Purchase Orders, Checklists, Roles, Workflows, AddCost/AddTime/AddFile modali, ecc.
(vedi grep in fondo).

## 2. `useLicenseEntitlement(entitlement)` — Livello A (Keygen)

Definizione: [`hooks/useLicenseEntitlement.ts`](../../frontend/src/hooks/useLicenseEntitlement.ts)

```ts
export const useLicenseEntitlement = (entitlement: LicenseEntitlement) => {
  const licensingState = useSelector((state) => state.license.state);
  return hasLicenseEntitlement(licensingState, entitlement);
};
const hasLicenseEntitlement = (license, entitlement) =>
  license.valid && license.entitlements.some((e) => e === entitlement);
```

Fonte dati: slice [`slices/license.ts`](../../frontend/src/slices/license.ts) →
`getLicenseValidity()` → `GET /license/state`. Senza licenza `valid=false` →
ritorna **false** per ogni entitlement.

Dispatch iniziale in [`App.tsx:168`](../../frontend/src/App.tsx#L168) (`dispatch(getLicenseValidity())`).

Esempi d'uso:
- `App.tsx:113` → `useLicenseEntitlement('BRANDING')` (logo/favicon custom).
- `Settings/Integrations/index.tsx:19` → `API_ACCESS`.
- `WorkOrders/index.tsx:184` → `RESOURCE_PLANNING`.
- `hooks/useBrand.ts:28` → `BRANDING`.

---

## 3. Conseguenza per il self-hosted

| Meccanismo | Stato self-hosted | Effetto UI |
|---|---|---|
| `hasFeature` (Livello B) | ✅ true (BUSINESS) | Menu/pagine gate da subscription **visibili** |
| `useLicenseEntitlement` (Livello A) | ❌ false (no licenza) | Pulsanti/azioni gate da entitlement **nascosti/disabilitati**; le chiamate API verrebbero comunque respinte dal backend con 403 |

**Implicazione per la Fase 2:** se si sblocca il Livello A **solo lato backend**
(modalità self-hosted centralizzata che rende `/license/state` `valid=true` con
gli entitlement), allora **anche il frontend si sblocca automaticamente**, perché
`useLicenseEntitlement` legge lo stesso endpoint `/license/state`. Non serve
modificare i singoli componenti React.

Questo è un punto architetturale importante: il frontend **non** ha una lista
hardcoded di feature abilitate — si fida di `/license/state`. Quindi la modifica
centralizzata backend è sufficiente per allineare backend e frontend.

---

## 4. Menu/sidebar

[`layouts/ExtendedSidebarLayout/Sidebar/SidebarMenu/index.tsx:251,334`](../../frontend/src/layouts/ExtendedSidebarLayout/Sidebar/SidebarMenu/index.tsx#L251)

```ts
const { hasViewPermission, hasFeature, user, company } = useAuth();
// ...
? hasFeature(item.planFeature)   // gate voce di menu per PlanFeature
```

I menu sono gate principalmente da `hasFeature` (Livello B) e dai permessi
ruolo, non dagli entitlement Keygen → in self-hosted sono già mostrati.

---

## 5. Elenco file frontend con logica di feature-flag (riferimento)

```
App.tsx                                   (BRANDING)
hooks/useLicenseEntitlement.ts            (core)
hooks/useBrand.ts                         (BRANDING)
slices/license.ts                         (fetch /license/state)
contexts/JWTAuthContext.tsx               (hasFeature core)
content/own/Analytics/AnalyticsLayout.tsx
content/own/Assets/index.tsx, Assets/Show/AssetDetails.tsx
content/own/CompanyProfile/CompanyPlan.tsx, CompanyProfile/index.tsx
content/own/Files/index.tsx
content/own/Imports/index.tsx
content/own/Inventory/index.tsx, Inventory/Parts.tsx
content/own/Locations/index.tsx, Locations/LocationDetails.tsx
content/own/Meters/index.tsx
content/own/PreventiveMaintenance/index.tsx
content/own/PurchaseOrders/Create.tsx, PurchaseOrders/index.tsx
content/own/Settings/Checklists/index.tsx
content/own/Settings/Features/Request/ConfigureFields.tsx
content/own/Settings/Features/RequestPortal/components/RequestPortalTable.tsx
content/own/Settings/Features/Workflows/index.tsx
content/own/Settings/Features/WorkOrder/ConfigureFields.tsx
content/own/Settings/Integrations/index.tsx   (API_ACCESS)
content/own/Settings/Roles/index.tsx, Roles/PageHeader.tsx, Roles/RoleDetails.tsx
content/own/Subscription/Plans/index.tsx, Plans/PlanFeatures.tsx
content/own/WorkOrders/index.tsx (RESOURCE_PLANNING), WorkOrders/Details/*Modal.tsx
layouts/ExtendedSidebarLayout/Sidebar/SidebarMenu/index.tsx
```

> Nota: la app **mobile** (`mobile/src`) è un client separato (React
> Native/Expo) che consuma le stesse API; segue le stesse regole backend.
> Non analizzata in dettaglio in questo audit (fuori dalle priorità).
