# Piano — Scelta admin: "Invita utenti" **oppure** "Crea utente"

> **Stato: RINVIATA** (piano approvato come documento, implementazione più avanti — decisione 2026-09-01).
> Il **toggle** (invita ⇄ crea) è **solo frontend**. **AGGIORNAMENTO 2026-09-01:** il modo "Crea utente" deve
> però **inviare una mail di benvenuto con le credenziali** (vedi §4.4) → questo richiede una **piccola aggiunta
> backend** (nuovo template + invio). Quindi la feature completa NON è più solo-frontend.

## 1. Obiettivo

Dare a un account admin la **scelta** tra due modi di aggiungere persone all'organizzazione, nella stessa
finestra "Invita utenti":

1. **Invita via email** — inserisci una o più email → parte l'invito → gli utenti si registrano da soli
   (nome/cognome/telefono/password li mettono loro). *(comportamento attuale con `INVITATION_VIA_EMAIL=true`)*
2. **Crea utente** — l'admin compila il form completo (nome, cognome, email, telefono, password) e l'account
   viene creato **subito**, senza invito via email. *(comportamento attuale con `INVITATION_VIA_EMAIL=false`)*

Oggi è un **aut-aut** deciso dal flag `INVITATION_VIA_EMAIL`: si vede solo uno dei due. L'obiettivo è mostrarli
**entrambi** con un toggle, mantenendo come default il comportamento del flag.

## 2. Stato attuale del codice (cosa esiste già)

- **Dialog:** `frontend/src/content/own/PeopleAndTeams/components/InviteUserDialog.tsx`
  - Riga ~128: `isEmailVerificationEnabled ? (<form invito email>) : (roleId && <CreateUser/>)`.
  - `isEmailVerificationEnabled` = `frontend/src/config.ts:36` = `getRuntimeValue('INVITATION_VIA_EMAIL') === 'true'`.
- **Form "invita":** inline nel dialog → `dispatch(inviteUsers(roleId, emails, false))` (invia la mail).
- **Form "crea":** `components/CreateUser.tsx` → monta `<RegisterJWT role={roleId} invitationMode .../>`
  (`content/pages/Auth/Register/RegisterJWT.tsx`). In `invitationMode` (riga ~102-104):
  1. `dispatch(inviteUsers(role, [email], true))` → crea l'invito **senza mail** (`disableSendingEmail=true`);
  2. `register({...values, role:{id}}, invitationMode)` → registra subito l'account (nome/cognome/telefono/password).

## 3. Verifica backend (perché NON serve toccarlo)

`UserService.signup` (`api/.../service/UserService.java:148-188`):
- riga 166-171: se `enableInvitationViaEmail && userInvitations.isEmpty()` → 406 "You are not invited...".
- Ma il passo (1) del form "crea" **crea prima l'invito**, quindi al passo (2) `findByRoleAndEmail` lo trova →
  il check passa → `enableAndReturnToken(user, true, ...)` crea l'account.
- `invite()` con `disableSendingEmail=true` non manda email (gate riga 379). → **Nessuna mail spuria.**

**Conclusione:** entrambi i flussi funzionano già con `INVITATION_VIA_EMAIL=true` (il valore live). La feature è
**esclusivamente UI**: rendere selezionabile ciò che oggi è deciso dal flag.

## 4. Modifiche proposte (frontend-only)

### 4.1 `InviteUserDialog.tsx`
- Aggiungere stato modalità:
  ```tsx
  type AddMode = 'invite' | 'create';
  const [mode, setMode] = useState<AddMode>(isEmailVerificationEnabled ? 'invite' : 'create');
  ```
- Sotto il `DialogTitle`, un **`ToggleButtonGroup`** (MUI, già usato altrove) con due opzioni:
  - `invite` → icona `EmailOutlined`, label `t('add_mode_invite')`
  - `create` → icona `PersonAddAlt1` (o simile), label `t('add_mode_create')`
  - `exclusive`, `onChange` → `setMode` (ignorare `null` per non deselezionare entrambi).
- Cambiare la condizione di rendering da `isEmailVerificationEnabled ? ... : ...` a `mode === 'invite' ? ... : ...`.
  - Il ramo `create` resta `roleId && <CreateUser roleId={roleId} .../>` (invariato).
- (Facoltativo) titolo dinamico: `t(mode === 'invite' ? 'invite_users' : 'create_user')`.

### 4.2 i18n — `frontend/src/i18n/translations/*.ts` (chiavi flat)
Aggiungere almeno in `en.ts`, `pl.ts`, `it.ts` (le altre 15 lingue: fallback su en o traduzione successiva):
- `add_mode_invite` → EN "Invite by email" / IT "Invita via email" / PL "Zaproś przez e-mail"
- `add_mode_create` → EN "Create user" / IT "Crea utente" / PL "Utwórz użytkownika"
- (se titolo dinamico) `create_user` → EN "Create user" / IT "Crea utente" / PL "Utwórz użytkownika"

### 4.4 Mail di benvenuto con credenziali (modo "Crea utente") — richiesta 2026-09-01
Quando l'admin crea l'utente dal form, dopo la creazione deve partire una **mail di benvenuto** contenente:
- **le credenziali di accesso** (email/username + password);
- il **suggerimento di cambiare la password** dopo il primo login;
- il **link alla pagina di login**.

**Stato attuale:** il modo "crea" (`RegisterJWT invitationMode`, riga ~102-104) crea l'invito con
`disableSendingEmail=true` → **nessuna mail**. La password è nota lato frontend al momento della creazione, ma NON
viene inviata.

**Implementazione proposta (serve backend):**
- **Nuovo template mail** `account-created.html` (fragment + layout come per invite/signup), con: intestazione
  "Il tuo account è stato creato", email/username, password, avviso "cambia la password dopo il primo accesso",
  bottone "Vai al login" → `${frontend.url}/account/login`.
- **Chiavi i18n** in `mailMessages(.properties/_it_IT/_pl_PL)`: subject + corpo (es. `accountCreatedSubject`,
  `accountCreatedIntro`, `accountCreatedCredentials`, `accountCreatedChangePasswordHint`, `accountCreatedLoginButton`).
- **Invio:** aggiungere nel flusso di creazione-by-admin l'invio della mail con la password in chiaro. Due opzioni:
  - **(a)** estendere `signup`/register lato backend: quando l'account viene creato con ruolo (contesto admin),
    inviare la mail di benvenuto con le credenziali. La password in chiaro è disponibile in `UserSignupRequest`
    prima dell'hashing → passarla al template.
  - **(b)** un endpoint/flag dedicato "invia credenziali" chiamato dal frontend dopo la creazione.
  - Preferibile **(a)** (atomico, niente password che gira in altre chiamate).

> ⚠️ **Nota sicurezza:** inviare la **password in chiaro via email** è una pratica sconsigliata (l'email non è un
> canale sicuro, resta negli archivi). Poiché è un deployment self-hosted interno e l'utente lo richiede
> esplicitamente, si può procedere **ma** con: (1) forte suggerimento di cambio password al primo login;
> valutare in futuro l'alternativa più sicura = inviare un **link "imposta password"** monouso invece della
> password in chiaro (elimina del tutto la password dall'email).

### 4.5 Riepilogo file toccati
- Frontend: `InviteUserDialog.tsx` (toggle) + i18n `en/pl/it` (label toggle).
- Backend: nuovo template `account-created.html` + chiavi `mailMessages*` + invio nel flusso di creazione (opzione a).
- Invariati: `CreateUser.tsx`, `RegisterJWT.tsx` (salvo il passaggio password già presente), `slices/user.ts`.

## 5. Comportamento / edge case
- **Default** = comportamento attuale: `INVITATION_VIA_EMAIL=true` → parte su "Invita"; `false` → parte su "Crea".
  Nessuna regressione per chi non tocca il toggle.
- Il ruolo (`UserRoleCardList`) resta obbligatorio in entrambe le modalità (il ramo "crea" già richiede `roleId`).
- La password nel form "crea" ha min 12 caratteri (validazione Yup esistente in `RegisterJWT`).
- Cambiando modalità non serve resettare le email digitate (restano nello stato; opzionale un reset per pulizia).

## 6. Build & deploy
- **Frontend** (toggle): `docker build ./frontend -t dablio96/self-hosted-cmms-frontend:self-hosted-vX.Y.Z`.
- **Backend** (mail benvenuto §4.4): `docker build ./api -t dablio96/self-hosted-cmms-backend:self-hosted-vX.Y.Z`.
- Push (utente `docker login`) → server: swap `frontend.image` + `api.image` → `pull` → `up -d api frontend` →
  **`restart nginx`** (IP cache).

## 7. Stima
- Frontend: ~30-40 righe in `InviteUserDialog.tsx` + 3 chiavi × 3 lingue (rischio basso, riusa componenti esistenti).
- Backend (mail): nuovo template + ~5 chiavi × 3 lingue + invio nel flusso creazione (rischio basso-medio).
- 1 rebuild frontend **+** 1 rebuild backend + deploy.

---
**Prossimo passo (quando si riprende):** implementare §4, rebuild frontend, deploy, testare i due percorsi
(invito che parte + creazione diretta che crea subito l'account senza mail).
