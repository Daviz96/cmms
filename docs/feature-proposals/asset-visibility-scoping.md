# Proposta — Visibilità asset per utente (*asset scoping*)

> **Stato: IN CORSO — implementato (Opzione A) sul branch `feature/asset-visibility-scoping`, 2026-09-07. Da testare;
> non ancora mergiato in `self-hosted`.** Feature backend + frontend. Invasività: **BASSA** (nessuna migrazione DB,
> nessun cambio schema — riusa colonne e associazioni già esistenti). Ancorato al codice reale. Riepilogo di ciò che è
> stato implementato in **§10**.

## 1. Obiettivo / caso d'uso

Poter **decidere a chi mostrare quali asset**. Esempio richiesto: un **dipendente di magazzino** deve poter
**vedere** e **creare ordini di lavoro / annunci di avaria (richieste)** *solo* sugli **asset a lui assegnati**,
non su tutto il parco macchine dell'azienda.

## 2. Cosa c'è GIÀ nel codice (buona notizia: ~90% è pronto)

Il modello di autorizzazione supporta già la visibilità "own vs others" **e** l'assegnazione asset↔utente.

- **Modello permessi** ([Role.java](../../api/src/main/java/com/grash/model/Role.java):51-69): ogni ruolo ha 5 set di
  `PermissionEntity` — `createPermissions`, `viewPermissions`, `viewOtherPermissions`, `editOtherPermissions`,
  `deleteOtherPermissions`. Le entità includono `ASSETS`, `WORK_ORDERS`, `REQUESTS`
  ([PermissionEntity.java](../../api/src/main/java/com/grash/model/enums/PermissionEntity.java)).
  Semantica: `viewPermissions.contains(X)` = "vedo (almeno i miei)"; `viewOtherPermissions.contains(X)` = "vedo anche
  quelli altrui/tutti".
- **Assegnazione asset↔utente già modellata** ([Asset.java](../../api/src/main/java/com/grash/model/Asset.java)):
  `primaryUser` (:72-74), `assignedTo` (M:N `T_Asset_User_Associations`, :85-98), `teams` (M:N, :100-113).
  `Asset.getUsers()`/`isAssignedTo(user)` (:210-246) uniscono primaryUser + membri dei team + assignedTo.
- **Controllo di accesso al singolo asset già "assignment-aware"**
  ([Asset.canBeViewedBy](../../api/src/main/java/com/grash/model/Asset.java):258-261):
  ```java
  view ASSETS && ( viewOther ASSETS || createdBy == user || isAssignedTo(user) )
  ```
  Stessa identica regola per [WorkOrder.canBeViewedBy] e
  [Request.canBeViewedBy](../../api/src/main/java/com/grash/model/Request.java):74-77 (view own + assegnati).
- **Lista Work Order già filtrata** per own+assegnati quando manca `viewOther`
  ([WorkOrderService.getSearchCriteria](../../api/src/main/java/com/grash/service/WorkOrderService.java):583-625):
  filtro su `createdBy` **con alternative** `assignedTo` / `primaryUser` / `team`.
- **UI già presente**: pagina **Ruoli** con matrice permessi
  (`frontend/src/content/own/Settings/Roles/PermissionsMatrix.tsx`) e campi **primaryUser/assignedTo** nella scheda
  asset (`frontend/src/content/own/Assets/...`). Nessuna nuova schermata necessaria.

## 3. Comportamento reale (confermato su utente di test) e GAP

**Osservato (2026-09-07):** ruolo con `view ASSETS` = ON, `viewOther ASSETS` = OFF, `create WORK_ORDERS`/`REQUESTS` =
ON; assegnato a un utente. L'asset di test ha l'utente in *altri utenti* (assignedTo) e un *team* di cui è membro.
Risultato: l'utente **vede in lista TUTTI gli asset** dell'azienda, ma **apre i dettagli solo** di quello assegnato.
Causa: **lista** e **picker** non applicano il filtro di assegnazione; solo la **vista di dettaglio** usa
`canBeViewedBy`.

### Gap 1 — le superfici *lista/picker* asset ignorano l'assegnazione
Vanno corrette 3 superfici (la 4ª è già ok):

| # | Endpoint | Metodo | Oggi | Serve |
|---|---|---|---|---|
| 1 | `GET /assets/children/{id}` + `/paginated` (**la pagina lista/albero**) | `findChildren`/`findChildrenPaginated` ([AssetService](../../api/src/main/java/com/grash/service/AssetService.java):361-383) | per `ROLE_CLIENT` a root ritorna **tutti** gli asset dell'azienda, controlla **solo** `view ASSETS` | filtrare per assegnazione se `!viewOther` |
| 2 | `POST /assets/search` (ricerca piatta) | `getSearchCriteria` ([:311-322](../../api/src/main/java/com/grash/service/AssetService.java)) | se `!viewOther` filtra **solo `createdBy`** | filtro assegnazione completo |
| 3 | `GET /assets/mini` (**picker** nei form WO/richiesta) | `findMini` ([:453-458](../../api/src/main/java/com/grash/service/AssetService.java)) | ritorna **tutti** gli asset dell'azienda | filtrare per assegnazione se `!viewOther` |
| 4 | `GET /assets/{id}` (dettaglio) | `checkAccessToAssetId`→`canBeViewedBy` | ✅ già corretto | — |

### Gap 2 — *(difesa in profondità)* la **creazione** WO/richiesta non valida l'asset
- WO create ([WorkOrderController.create](../../api/src/main/java/com/grash/controller/WorkOrderController.java):118-126
  → `workOrderService.createByUser`) verifica il ruolo ma **non** chiama `asset.canBeViewedBy(user)` sull'asset agganciato.
- Request create ([RequestController.create](../../api/src/main/java/com/grash/controller/RequestController.java):171-181)
  verifica `createPermissions.contains(REQUESTS)` ma **non** valida l'asset.
- (Gli endpoint di *lettura* per-asset invece lo fanno già, es. `WorkOrderController.getByAsset` :92.)

→ Una volta filtrato il picker (#3) il caso normale è coperto, ma un utente ristretto potrebbe comunque agganciare
**per id** un asset fuori scope → il backend va blindato lo stesso (anche nei patch che cambiano asset).

## 4. Proposta consigliata — **Opzione A: "assegnazione = visibilità"** (riuso del modello esistente)

Coerente con l'esempio dell'utente e col resto dell'app (i Work Order funzionano già così).

### 4.1 Backend (nessuna migrazione, nessun cambio schema)
**Principio guida:** *lista e picker devono restituire esattamente gli asset per cui `Asset.canBeViewedBy(user)` è
vero* → `viewOther ASSETS` **oppure** `createdBy == user` **oppure** `isAssignedTo` (= `primaryUser` ∪ membri dei
`teams` ∪ `assignedTo`). Così lista, picker e dettaglio diventano coerenti.

1. **Filtro riusabile "asset assegnati".** Un helper che, per `!viewOther ASSETS`, produce il filtro come già fa
   `WorkOrderService.getSearchCriteria`:603-625: `createdBy` **eq** con `alternatives` `primaryUser` (eq),
   `assignedTo` (inm, M:N) e i **`teams`** dell'utente (in, via `teamService.findByUser`).
   > ⚠️ Sul modello **Asset il campo team si chiama `teams`** (plurale, M:N) — non `team` come nel Work Order. Da usare
   > il nome giusto nella Specification. `createdBy` è tenuto per coerenza con `canBeViewedBy` (di fatto ininfluente per
   > un ruolo senza `create ASSETS`).
2. **`getSearchCriteria` (#2):** sostituire il ramo `filterCreatedBy` col filtro completo del punto 1.
3. **Lista/albero (#1):** per un utente `!viewOther ASSETS` **non** usare l'albero gerarchico ma restituire una
   **lista piatta** filtrata per assegnazione (stessa logica del punto 1). Motivo: se l'utente è assegnato a un asset
   *figlio* ma non al *padre*, l'albero (che parte dai root con `parentAsset == null`) **non** lo mostrerebbe → la
   lista piatta evita il problema e mostra esattamente gli asset visibili. Gli admin (`viewOther`) continuano a vedere
   l'albero completo.
4. **Picker `findMini` (#3):** stesso filtro quando `!viewOther ASSETS` → i form WO/richiesta mostrano solo asset
   visibili.
5. **Validazione in creazione/patch (Gap 2):** in WO/Request create e nei patch che impostano/cambiano asset, se
   l'asset è presente e `!asset.canBeViewedBy(user)` → `403` (riusa `assetService.checkAccessToAssetId`).

### 4.2 Configurazione (nessun codice) — come si ottiene lo scenario
1. **Ruolo "Magazziniere"** (pagina Ruoli): `view` ON su `ASSETS`, `WORK_ORDERS`, `REQUESTS`; `create` ON su
   `WORK_ORDERS`, `REQUESTS`; **`viewOther` OFF** su `ASSETS`, `WORK_ORDERS`, `REQUESTS`.
2. **Assegnare gli asset** all'utente (dalla scheda asset: `primaryUser`, oppure `assignedTo`, oppure un `team` di cui
   è membro).
3. **Risultato:** l'utente vede in lista **solo** gli asset assegnati, e può creare WO/richieste **solo** su quelli.

### 4.3 Frontend
- **Pagina Assets (lista):** rende un **albero** (endpoint `/assets/children`). Per utenti `!viewOther ASSETS` il
  backend (#3) restituirà una **lista piatta**: verificare che la vista regga elementi senza gerarchia (nessun
  espandi/figli) oppure, per questi utenti, passare all'endpoint `/assets/search`. Piccolo adeguamento in
  `content/own/Assets/index.tsx`.
- **Picker asset** nei form WO/richiesta: usa `/assets/mini` → una volta corretto `findMini` (#4) mostra solo gli
  asset visibili, **senza** modifiche frontend.
- **Matrice permessi** ruoli: già presente (`Settings/Roles/PermissionsMatrix.tsx`) → verificare solo che le label
  siano chiare (es. "Vedere tutti gli asset" per `viewOther ASSETS`).
- i18n: eventuali stringhe nuove.

## 5. Varianti (se serve granularità diversa)

- **Opzione B — scoping per *location*.** Assegnare l'utente a una **Location**
  (`T_Location_User_Associations`; `Location.canBeViewedBy` è già assignment-aware) e far "ereditare" agli asset la
  visibilità della loro location. Ideale se "magazzino" = una location: assegni la persona alla location e vede tutti
  i suoi asset senza assegnarli uno per uno. Richiede estendere il filtro asset (e `canBeViewedBy`) per includere la
  location assegnata. Invasività bassa/media. **Ottima come complemento all'Opzione A.**
- **Opzione C — ACL esplicita per-asset (*lista di condivisione*).** Un campo separato "visibile a: utenti/team",
  distinto dall'assegnazione operativa. Massima granularità, ma **nuovo modello + migrazione DB + nuova UI** →
  invasività **ALTA**. Sconsigliata salvo requisiti che l'assegnazione non copre.

## 6. Invasività, rischi, effetti collaterali

- **Opzione A:** backend ~2 modifiche piccole (nessun cambio schema), frontend solo verifica. Rebuild **backend +
  frontend** → deploy (swap `api`+`frontend`, `pull`, `up -d`, `restart nginx`). **Rischio: basso.**
- Gli **admin** (con `viewOther`) **non** sono impattati: continuano a vedere tutto.
- **Attenzione (fase 2):** altri oggetti collegati agli asset — *preventive maintenance*, *meter/letture*, *parti* —
  hanno i propri filtri di visibilità: verificare che non "trapelino" nomi/dati di asset fuori scope. Fuori dallo
  scope di questa prima fase; da elencare e valutare separatamente.

## 7. Piano di implementazione (quando approvato)

1. Backend: **helper filtro assegnazione** (§4.1.1) + applicarlo a `getSearchCriteria` (#2), lista/albero → piatta
   (#3), `findMini` (#3-picker). Test unit/integration.
2. Backend: **validazione asset** in create/patch WO+richiesta (Gap 2) → 403 fuori scope. Test.
3. Frontend: adeguare pagina Assets a lista piatta per ruoli ristretti (§4.3) + label matrice permessi + i18n.
4. Rebuild + deploy `api`+`frontend` (runbook `dev-docs/deploy-*`), `restart nginx`.
5. Config ruolo "Magazziniere" + assegnazione asset. **Test end-to-end** (§8).

## 8. Test di accettazione (attesi)

Utente `U` con ruolo "Magazziniere", assegnato all'**Asset X**, non assegnato all'**Asset Y**:
- ✅ `U` vede in **lista solo X** (non tutti gli asset), riconosciuto tramite `primaryUser` **o** `assignedTo` (altri
  utenti) **o** membro di un `team` assegnato all'asset; **non** vede **Y**.
- ✅ il **picker asset** nel form WO/richiesta mostra **solo X** (non Y).
- ✅ `U` crea WO e richiesta su **X**; le ritrova nelle proprie liste.
- ✅ create WO/richiesta su **Y** (forzato per id) → **403**.
- ✅ un **admin** (`viewOther`) continua a vedere sia X sia Y (albero completo) e tutti i WO/richieste.

## 9. Domande aperte / decisioni

1. **"Assegnazione = visibilità" (A)** basta, o serve anche lo **scoping per location (B)**?
2. Un utente ristretto che **crea** un WO/richiesta ne diventa `createdBy` → lo vedrà comunque. Accettabile? (Sì, coerente.)
3. Nascondere anche **PM / meter / parti** collegati ad asset fuori scope? → **fase 2**.
4. Creare un ruolo **"Magazziniere" predefinito** di serie, o lasciare che l'admin lo configuri?

---

## 10. Stato implementazione (branch `feature/asset-visibility-scoping`)

Implementata l'**Opzione A**. Modifiche:

**Backend** ([AssetService.java](../../api/src/main/java/com/grash/service/AssetService.java)):
- `isRestrictedToAssignedAssets(user)` = `ROLE_CLIENT` **e** senza `viewOther ASSETS`.
- `assignedAssetsFilter(user)` = `FilterField` `createdBy eq` + alternative `primaryUser eq`, `assignedTo inm`,
  `teams inm` (team dell'utente) → OR (stessa forma del Work Order).
- `findAssignedAssets(user, pageable)` = pagina piatta filtrata (company + assegnazione).
- `getSearchCriteria` (#2): usa il filtro assegnazione se ristretto (non più solo `createdBy`).
- `findChildren` / `findChildrenPaginated` (#1): se ristretto → lista/pagina **piatta** assegnata (niente albero).
- `findMini` (#3, picker WO/richiesta): se ristretto → solo asset assegnati (+ eventuale `location`).

**Backend — create (Gap 2):** validazione `assetService.checkAccessToAssetId(assetId, user)` (→ 403) in
[WorkOrderController.create](../../api/src/main/java/com/grash/controller/WorkOrderController.java) e
[RequestController.create](../../api/src/main/java/com/grash/controller/RequestController.java).

**Frontend** ([Assets/index.tsx](../../frontend/src/content/own/Assets/index.tsx)): per utenti senza `viewOther ASSETS`
la vista predefinita è **lista piatta** (`view='list'`), l'albero è nascosto (`onQueryChange`/`onResetFilters`
non tornano più a `hierarchy`).

**Non ancora fatto / follow-up:** validazione asset anche nei **patch** WO/richiesta che cambiano asset (fase 2);
scoping di PM/meter/parti collegati (fase 2). **Test end-to-end** ancora da eseguire (§8) prima del merge in `self-hosted`.

### Riferimenti codice
- Permessi: [Role.java](../../api/src/main/java/com/grash/model/Role.java):51-69 ·
  [PermissionEntity.java](../../api/src/main/java/com/grash/model/enums/PermissionEntity.java)
- Asset: [Asset.java](../../api/src/main/java/com/grash/model/Asset.java) — assegnazioni :72-113, `getUsers`/`isAssignedTo`/`canBeViewedBy` :210-261
- Filtro lista asset (Gap 1): [AssetService.getSearchCriteria](../../api/src/main/java/com/grash/service/AssetService.java):311-322
- Pattern di riferimento (WO): [WorkOrderService.getSearchCriteria](../../api/src/main/java/com/grash/service/WorkOrderService.java):583-625
- Create paths (Gap 2): [WorkOrderController.create](../../api/src/main/java/com/grash/controller/WorkOrderController.java):118 ·
  [RequestController.create](../../api/src/main/java/com/grash/controller/RequestController.java):171
- Controlli di lettura già assignment-aware: WorkOrderController:92 · [Request.canBeViewedBy](../../api/src/main/java/com/grash/model/Request.java):74
