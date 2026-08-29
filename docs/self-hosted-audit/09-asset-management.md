# 09 — Asset Management

Obiettivo futuro: gerarchia tipo
`Stabilimento → Reparto → Macchina → (Motore, Pompa, Quadro, Componenti)`.

---

## 1. Componenti

| Area | File |
|---|---|
| Asset service | [`service/AssetService.java`](../../api/src/main/java/com/grash/service/AssetService.java) |
| Asset entity | [`model/Asset.java`](../../api/src/main/java/com/grash/model/Asset.java) |
| Downtime | [`service/AssetDowntimeService.java`](../../api/src/main/java/com/grash/service/AssetDowntimeService.java) |
| Location | [`service/LocationService.java`](../../api/src/main/java/com/grash/service/LocationService.java) |

---

## 2. Gate rilevati

| Funzione | Gate | File | Note |
|---|---|---|---|
| Asset con parent (gerarchia) — create | `ASSET_HIERARCHY` | `AssetService.java:91` | throw |
| Asset con parent — patch | `ASSET_HIERARCHY` | `AssetService.java:128` | throw |
| Import asset con gerarchia | `ASSET_HIERARCHY` | `AssetService.java:488` | throw |
| Limite n. asset | `UNLIMITED_ASSETS` (free 50) | `AssetService.java:151` | |
| Scan NFC/barcode | `NFC_BARCODE` | `AssetService.java:325/331` | throw |
| Asset downtime | `ASSET_DOWNTIME` | `AssetDowntimeService.java:26` | |
| Limite n. location | `UNLIMITED_LOCATIONS` (free 10) | `LocationService.java:92` | |

```java
// AssetService.java:91 — gerarchia
if (asset.getParentAsset() != null && !licenseService.hasEntitlement(LicenseEntitlement.ASSET_HIERARCHY))
    throw new CustomException("You need a license to add a child asset to another asset.", ...);
```

---

## 3. Cosa è implementato

- **Asset CRUD**: completo.
- **Gerarchia parent/child**: il modello `Asset` supporta `parentAsset`; la
  logica di creazione/patch/import gerarchica esiste. Il **solo** vincolo è
  l'entitlement `ASSET_HIERARCHY`. La struttura multilivello richiesta
  (Stabilimento→…→Componenti) è rappresentabile con parent/child + Location.
- **Location**: entità e ordinamento (branch `location-sorting` a monte).
- **Downtime**: `AssetDowntime` con calcolo intervalli (`AssetService` usa
  `getDateDiff` tra downtime consecutivi).
- **Meters, attachments, parts, work order collegati, PM collegata**: presenti e
  associati all'asset.

Nessuna parte risulta non implementata: la gerarchia è pronta, è solo gated.

---

## 4. Classificazione

- Asset Hierarchy: 🟢 **UNLOCK_SIMPLE** (Alta).
- Asset Downtime, NFC, Unlimited Assets/Locations: 🟢 **UNLOCK_SIMPLE**.
- Asset CRUD base, Location base: ⚪ già disponibili.
