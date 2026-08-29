# 08 — Work Orders & Work Order History

---

## 1. Work Order — gate rilevati

| Funzione | Gate | File |
|---|---|---|
| Limite WO attivi | `UNLIMITED_ACTIVE_WORK_ORDERS` (free 30) | `WorkOrderService.java:184` |
| Firma al completamento | `SIGNATURE_CAPTURE` + `PlanFeatures.SIGNATURE` | `WorkOrderService.java:830/850/1077` |
| Collegamento tra WO | `WORK_ORDER_LINKING` | `RelationService.java:72` |
| Time tracking (labor) | `TIME_TRACKING` + `PlanFeatures.ADDITIONAL_TIME` | `LaborService.java:39`, `LaborController.java:115` |
| Cost tracking | `COST_TRACKING` + `PlanFeatures.ADDITIONAL_COST` | `AdditionalCostService.java:33`, `AdditionalCostController.java:70` |

```java
// WorkOrderService.java:184 — limite WO attivi
Integer threshold = usageBasedFreeLimits.get(LicenseEntitlement.UNLIMITED_ACTIVE_WORK_ORDERS);
if (!licenseService.hasEntitlement(LicenseEntitlement.UNLIMITED_ACTIVE_WORK_ORDERS)
        && workOrderRepository.hasMoreActiveThan(company.getId(), threshold.longValue() - 1 ...))
    throw new CustomException("You need a license to add a new work order. Free Limit of " + threshold + " ...", ...);
```

---

## 2. Work Order History — analisi dedicata (sezione 9 del prompt)

**Meccanismo:** Hibernate **Envers**. Le revisioni sono registrate su tabelle di
audit indipendentemente dalla licenza.

Componenti:

- [`model/WorkOrder.java`](../../api/src/main/java/com/grash/model/WorkOrder.java) e [`model/abstracts/WorkOrderBase.java`](../../api/src/main/java/com/grash/model/abstracts/WorkOrderBase.java) — `@Audited`.
- [`model/envers/RevInfo.java`](../../api/src/main/java/com/grash/model/envers/RevInfo.java) — entità revisione (timestamp).
- [`model/envers/UserRevisionListener.java`](../../api/src/main/java/com/grash/model/envers/UserRevisionListener.java) — associa la revisione all'**utente**.
- [`model/envers/WorkOrderAud.java`](../../api/src/main/java/com/grash/model/envers/WorkOrderAud.java) / [`WorkOrderAudId.java`](../../api/src/main/java/com/grash/model/envers/WorkOrderAudId.java) — record di audit del work order.
- [`service/WorkOrderHistoryService.java`](../../api/src/main/java/com/grash/service/WorkOrderHistoryService.java) — legge lo storico.

**Il gate** ([`WorkOrderHistoryService.java:51`](../../api/src/main/java/com/grash/service/WorkOrderHistoryService.java#L51)):

```java
public Collection<WorkOrderHistory> findByWorkOrder(Long id) {
    if (!licenseService.hasEntitlement(LicenseEntitlement.WORK_ORDER_HISTORY)) return new ArrayList<>();
    WorkOrder workOrder = workOrderRepository.findById(id).orElseThrow();
    return workOrderAudRepository.findByIdAndRevtype(id, 1).stream().map(...)  // storico, autore, timestamp
}
```

**Osservazioni chiave:**

- L'audit trail (revisioni, autore, timestamp) è **sempre registrato** da Envers,
  anche senza licenza — non si perde nulla mentre il gate è attivo.
- Il gate è **soft**: senza entitlement l'endpoint ritorna semplicemente una
  lista vuota (nessun errore).
- Diff/nome della modifica sono costruiti da `workOrderAud.getSummary(...)`.

**Conclusione:** è una funzione **completa** protetta solo dall'entitlement. Una
volta sbloccato `WORK_ORDER_HISTORY`, lo storico completo (comprese le revisioni
già accumulate) diventa immediatamente visibile.

---

## 3. Classificazione

- Work Order History: 🟢 **UNLOCK_SIMPLE** (Alta priorità).
- Signature, WO Linking, Time/Cost tracking, Unlimited active WO: 🟢 **UNLOCK_SIMPLE**.
- WO base, creazione, stati, assegnazioni: ⚪ già disponibili.
