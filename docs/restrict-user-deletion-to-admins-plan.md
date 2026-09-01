# Piano — Eliminazione utenti **solo agli admin** (blocco auto-eliminazione)

> **Stato: PROPOSTA / da approvare** (documento richiesto 2026-09-01). Non ancora implementato.
> Feature **frontend + backend**. Invasività: **BASSA** (poche righe, nessuna migrazione DB).

## 1. Obiettivo

Impedire che un utente qualsiasi possa **auto-eliminare** il proprio account (operazione pericolosa).
L'eliminazione degli utenti deve essere consentita **solo agli admin** (chi ha il permesso
`PEOPLE_AND_TEAMS` in *edit-others*, oppure `ROLE_SUPER_ADMIN`).

## 2. Situazione attuale (due percorsi di eliminazione distinti!)

### 2.1 Auto-eliminazione — `DELETE /auth` → `AuthController.deleteAccount` ⚠️ PERICOLOSO
- `frontend/src/content/own/UserProfile/index.tsx` (pulsante "Delete account", riga ~68-75) →
  `onDeleteAccount` → `api.deletes('auth')` → **`DELETE /auth`**.
- `api/.../controller/AuthController.java:221-228`:
  ```java
  @DeleteMapping("")
  @PreAuthorize("permitAll()")          // ← qualsiasi utente autenticato
  public SuccessResponse deleteAccount(@CurrentUser User user) {
      if (user.isOwnsCompany())
          companyService.delete(user.getCompany().getId());   // ← CANCELLA L'INTERA COMPANY
      else userRepository.delete(user);                        // ← HARD delete dell'utente
      ...
  }
  ```
- **Rischi:** è un **hard delete** (non soft). Se l'utente **possiede la company**, un click cancella
  **tutta l'organizzazione** (utenti, asset, work order, tutto). Protetto solo da un `window.confirm` nel browser.
- (È anche la causa reale del Bug 1: dopo l'hard-delete, il `logout` provava a salvare la riga inesistente →
  409. Già mitigato in v1.0.3, ma l'endpoint resta pericoloso.)

### 2.2 Eliminazione di un altro utente (admin) — `PATCH /users/soft-delete/{id}` → `softDeleteUser` ✅ soft
- `api/.../controller/UserController.java:150-154`: `@PreAuthorize("hasRole('ROLE_CLIENT')")`.
- `api/.../service/UserService.java:560-577`:
  ```java
  if (requester.getId().equals(id)                                   // ← auto-eliminazione consentita!
      || requester.getRole().getEditOtherPermissions().contains(PermissionEntity.PEOPLE_AND_TEAMS)) {
      userToSoftDelete.setEnabled(false); ... setEmail(email+"_"+id); ... invalidateSessions(...);
  }
  ```
- Soft delete (disabilita + rinomina email). Usato dalla pagina **People** (gestione utenti). Il ramo
  `requester.getId().equals(id)` permette comunque a chiunque di soft-eliminarsi.

## 3. Modifiche proposte

### 3.1 Backend
1. **`AuthController.deleteAccount` (`DELETE /auth`) — rimuovere l'auto-eliminazione self-service.**
   - **Opzione A (consigliata):** rimuovere l'endpoint (o `@PreAuthorize("denyAll()")` / rispondere 403).
     Elimina sia l'auto-eliminazione sia il rischio "owner cancella l'intera company con un click".
     La cancellazione avviene **solo** dal flusso admin (People → soft-delete).
   - Opzione B: mantenerlo ma restringerlo (es. solo owner, o solo admin) — meno pulito, mantiene il rischio
     company-delete. **Sconsigliata.**
2. **`UserService.softDeleteUser` — togliere il ramo di auto-eliminazione.**
   ```java
   // PRIMA: if (requester.getId().equals(id) || <ha PEOPLE_AND_TEAMS edit-others>)
   // DOPO:  if (!requester.getId().equals(id) && <ha PEOPLE_AND_TEAMS edit-others>)
   //        else -> 403 "You don't have permission"
   ```
   Così solo un admin può eliminare, e **nessuno può eliminare sé stesso** (evita anche il lockout dell'ultimo
   admin da parte di sé stesso; due admin possono comunque eliminarsi a vicenda — accettabile).
   - (Facoltativo) endpoint `PATCH /users/soft-delete/{id}`: alzare `@PreAuthorize` a un controllo permessi più
     esplicito, ma il check nel service è sufficiente.

### 3.2 Frontend
1. **`UserProfile/index.tsx`:** rimuovere il pulsante "Delete account" + `onDeleteAccount` (con Opzione A l'utente
   non si auto-elimina più). In alternativa mostrarlo **solo** agli admin — ma se l'auto-eliminazione va tolta
   del tutto, la rimozione è più coerente.
2. **Pagina People (admin):** l'azione di eliminazione resta; è già gated dai permessi. Verificare che il pulsante
   di delete sia mostrato solo a chi ha `PEOPLE_AND_TEAMS` (probabile già così) → eventuale piccolo adeguamento.
3. i18n: la chiave `delete_account` può restare inutilizzata o essere rimossa.

## 4. Invasività — BASSA
- Backend: 2 modifiche piccole (rimuovere/blindare `deleteAccount`; togliere il ramo self in `softDeleteUser`).
- Frontend: rimuovere 1 pulsante + handler.
- **Nessuna** migrazione DB, **nessun** cambio schema.
- Rebuild **backend + frontend** → deploy (swap `api` + `frontend`, `pull`, `up -d`, `restart nginx`).
- Rischio: basso. Nota: assicurarsi che resti almeno un admin operativo.

## 5. Decisioni da confermare
1. **Opzione A (rimuovere `DELETE /auth`) o B (restringerlo)?** → consiglio **A**.
2. Un admin deve poter eliminare **il proprio** account? → consiglio **NO** (eliminazione sempre admin→altro utente).
3. Serve un flusso separato e ben protetto per **cancellare l'intera company** (owner)? → fuori scope per ora;
   valutare un'azione dedicata e confermata in futuro, se necessaria.

---
**Prossimo passo (quando si approva):** implementare §3, rebuild backend+frontend, deploy, testare che:
un utente non-admin **non** veda/non possa eliminare account; un admin possa eliminare altri utenti dalla pagina People.
