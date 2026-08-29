# 07 — Preventive Maintenance & Meters

---

## 1. Componenti

| Area | File |
|---|---|
| PM service | [`service/PreventiveMaintenanceService.java`](../../api/src/main/java/com/grash/service/PreventiveMaintenanceService.java) |
| Schedule / ricorrenza | [`service/ScheduleService.java`](../../api/src/main/java/com/grash/service/ScheduleService.java) |
| Job creazione WO | [`job/WorkOrderCreationJob.java`](../../api/src/main/java/com/grash/job/WorkOrderCreationJob.java) |
| Job notifiche PM | [`job/PreventiveMaintenanceNotificationJob.java`](../../api/src/main/java/com/grash/job/PreventiveMaintenanceNotificationJob.java) |
| Meter service | [`service/MeterService.java`](../../api/src/main/java/com/grash/service/MeterService.java) |
| Reading controller | [`controller/ReadingController.java`](../../api/src/main/java/com/grash/controller/ReadingController.java) |
| Trigger su meter | [`service/WorkOrderMeterTriggerService.java`](../../api/src/main/java/com/grash/service/WorkOrderMeterTriggerService.java) |

---

## 2. Gate rilevati

| Funzione | Gate | File | Note |
|---|---|---|---|
| PM base (uso feature) | `PlanFeatures.PREVENTIVE_MAINTENANCE` | `PreventiveMaintenanceService.java:70/90` | ⚪ soddisfatto (BUSINESS) |
| Limite n. PM schedule | `UNLIMITED_PM_SCHEDULES` (free 10) | `PreventiveMaintenanceService.java:172` | 🟢 |
| Vista calendario PM | `PM_CALENDAR` | `PreventiveMaintenanceService.java:211` | 🟢 |
| Meter base (uso feature) | `PlanFeatures.METER` | `MeterController.java:109`, `ReadingController.java:96` | ⚪ soddisfatto |
| Limite n. meter | `UNLIMITED_METERS` (free 10) | `MeterService.java:69` | 🟢 |
| Condition/meter-based PM (trigger) | `CONDITION_BASED_PM` | `WorkOrderMeterTriggerService.java:37` | 🟢 (throw) |

```java
// WorkOrderMeterTriggerService.java:36
public WorkOrderMeterTrigger create(WorkOrderMeterTrigger workOrderMeterTrigger, Company company) {
    if (!licenseService.hasEntitlement(LicenseEntitlement.CONDITION_BASED_PM))
        throw new CustomException("You need a license to create a meter trigger", HttpStatus.FORBIDDEN);
    ...
}
```

---

## 3. Cosa è implementato

- **PM schedule + ricorrenza**: `ScheduleService` gestisce `startsOn`/`endsOn`,
  trigger, notifiche (Quartz). Le PM generano work order automaticamente via
  `WorkOrderCreationJob`.
- **Calendar**: dati esposti al frontend (PM index/calendar); gate solo da
  `PM_CALENDAR`.
- **Meter trigger (condition-based)**: entità `WorkOrderMeterTrigger`,
  valutazione sui reading; crea WO quando la condizione è soddisfatta. Gate
  `CONDITION_BASED_PM`.
- **Checklist / asset association / parts / notifications**: presenti e
  collegati a PM/WO.

Tutte le funzioni PM/meter risultano **implementate**; i blocchi sono
entitlement (Livello A) o limiti numerici (Livello C). Il gate `PlanFeatures`
(Livello B) è già aperto.

---

## 4. Classificazione

- PM base, Meters base, Import: ⚪ **ALREADY_AVAILABLE**.
- PM Calendar, Condition-based PM, Unlimited PM/Meters: 🟢 **UNLOCK_SIMPLE**.
- Nessuna funzione 🔴/🟠.
