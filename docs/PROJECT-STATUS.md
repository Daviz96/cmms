# Atlas CMMS self-hosted — Stato del progetto & Changelog

> **Documento master di stato.** Snapshot dello stato reale (produzione) + storico delle versioni.
> Aggiornato: **2026-09-02**. Deployment live: `https://cmms.firmabratex.pl` (LAN-only dietro Caddy, TLS wildcard).
> Fork: `Daviz96/cmms`, branch **`self-hosted`** (HEAD `377fa3bd`). Immagini: Docker Hub `dablio96/self-hosted-cmms-*`.

---

## 1. Stato LIVE attuale (snapshot)

| Componente | Immagine / versione | Stato |
|---|---|---|
| **Backend** (`atlas-cmms-backend`) | `dablio96/self-hosted-cmms-backend:self-hosted-v1.2.1` | ✅ live |
| **Frontend** (`atlas-cmms-frontend`) | `dablio96/self-hosted-cmms-frontend:self-hosted-v1.2.0` | ✅ live (fix `timers` in coda, non ancora buildato) |
| **DB** (`atlas_db`) | `postgres:16-alpine` | ✅ dati preservati |
| **Storage** (`atlas_minio`) | `minio/...2025-04-22` | ✅ |
| **Ingress interno** (`atlas_nginx`) | `nginx:1.27-alpine` | ✅ solo `80/tcp` interno (nessuna porta host) |
| **Reverse proxy** | `caddy:2` (`/srv/docker/proxy`) | ✅ TLS wildcard |
| **Licensing** | `LICENSING_SELF_HOSTED_MODE=true` | ✅ `SELF_HOSTED` (34 entitlement) |

**Ingress:** unico accesso = **Caddy** → `cmms.firmabratex.pl`. `/download/*` servito da Caddy (APK). Tutto il resto →
`reverse_proxy atlas_nginx:80` (rete Docker). La porta host `3000` è stata **chiusa** (2026-09-02): `192.168.101.80:3000`
non risponde più (nessun accesso HTTP grezzo che scavalca Caddy/TLS).

**Server:** stack atlas = `/srv/docker/atlas` (compose + `.env`); Caddy = `/srv/docker/proxy`. Dati in **bind-mount**:
`/srv/data/databases/atlas/{postgres,minio}` (**mai `docker compose down -v`**). Config/logo backend:
`/srv/data/applications/atlas/{config,logo}`. APK: `/srv/data/applications/caddy/download/atlas-cmms.apk`.

---

## 2. Changelog / storico versioni

Ordine cronologico. "Deploy" = attivo in produzione.

| Versione | Contenuto | Commit | Deploy |
|---|---|---|---|
| **v1.0.0** | Baseline self-hosted (MOD-001..020): licensing self-hosted mode, storage MinIO/GCP, i18n, ecc. | `a03c35db` (tag `self-hosted-v1.0.0`) | ✅ |
| **v1.0.1** | Mail invito/benvenuto: **download APK (QR + link)** + istruzioni config server; immagini inline CID | `decbc2cd` | ✅ |
| **v1.0.2** | **Bug 2** (invito che parte: frontend nostro `disableSendingEmail=false`) + **Bug 3** (ricerca WO session-safe) + log Bug 1. Backend + **primo frontend nostro**. | `635bf2c9` | ✅ |
| **v1.0.3** | **Bug 1** (`conflict_error` auto-eliminazione: `logout`→`invalidateSessionsById`). Backend. | `635bf2c9` | ✅ |
| **v1.1.0** | **Sync upstream** (32 commit `Grashjs/cmms`): rate-limiting login, PDF RTL/CJK, **flusso eliminazione account a 2 passi (conferma email)**, signed-URL caching, webhook, migrazione `Part.version`, ecc. Conflitti risolti (4). | `69a259f4`+`7920c0d3` | ✅ |
| **v1.2.0** | **Feature admin "Crea utente"** (toggle Invita⇄Crea) con **link imposta-password** (nessuna password in mail) + **fix QR/dialog "scarica app" → APK self-hosted** (non app ufficiale) | `5ea45b81`,`889ee9e0`,`f3169fef` | ✅ |
| **v1.2.1** | **Fix mail**: `accountCreatedSubject` risolto dal message source giusto (`messages*`, non `mailMessages*`) → basta errore `No message found for pl_PL`; `createUserByAdmin` `@Transactional` (niente utenti orfani) | `3953835e` | ✅ (solo backend) |
| **(in coda)** | **i18n PL**: `timers` era "Liczniki" (uguale a meters) → **"Timery"**. Committato, **NON** ancora buildato/deployato (batch frontend) | `377fa3bd` | ⏳ |

**Immagini Docker Hub:** backend `v1.0.0..v1.2.1`; frontend `v1.0.2`, `v1.1.0`, `v1.2.0` (il frontend è cambiato solo
in quei punti). Tag git: solo `self-hosted-v1.0.0` (le altre versioni = commit + tag immagine; si possono aggiungere tag git).

---

## 3. In coda / batch (da NON buildare finché non si accumulano più fix)

**Frontend — prossima rebuild `v1.2.x`:**
- `timers` → "Timery" (traduzione PL) — commit `377fa3bd`.
- *(aggiungere qui i prossimi ritocchi UI/i18n che emergono dai test)*

Quando si chiude il batch: `docker build ./frontend` → tag nuovo → push → server swap `frontend` → `pull`+`up -d`+`restart nginx`.

---

## 4. Decisioni aperte / backlog (piani pronti, non implementati)

- **Eliminazione utenti solo agli admin** — parzialmente coperto dal flusso upstream a 2 passi (conferma email) che ha
  **rimosso** l'hard-delete istantaneo. Da decidere se limitare del tutto l'auto-eliminazione ai soli admin.
  Piano: [restrict-user-deletion-to-admins-plan.md](restrict-user-deletion-to-admins-plan.md).
- **Traduzione completa** del dialog "scarica app" (`MobileAppDownloadDialog`) — al momento in inglese sul sito PL.
- **Tag git** per le versioni `v1.0.1..v1.2.1` (oggi solo `v1.0.0`), per storico più pulito.

---

## 5. Test funzionali eseguiti (v1.1.0 / v1.2.x)
- ✅ Login, upload/download allegati (storage MinIO).
- ✅ **Ricerca Work Order** (Bug 3) — validata con seed (`dev-docs/seed_test_data.py`), `totalElements=6`, nessun NPE.
- ✅ **Eliminazione account** — nuovo flusso a 2 passi con conferma email.
- ✅ **Invito** utente via email.
- ✅ **Crea utente** (v1.2.1) — nessun errore/conflitto. **Da confermare end-to-end:** arrivo mail imposta-password + link `/account/set-password`.

---

## 6. Come riprendere / riferimenti
- **Snapshot + storico:** questo file.
- **Bug storici (1/2/3) risolti:** [live-deployment-bugs-handoff.md](live-deployment-bugs-handoff.md).
- **Sync upstream (procedura):** [upstream-sync-plan.md](upstream-sync-plan.md).
- **Feature crea-utente (design):** [admin-invite-vs-create-user-plan.md](admin-invite-vs-create-user-plan.md).
- **Runbook deploy backend:** `dev-docs/deploy-v1.1.0-runbook.md` (riusabile bumpando la versione), `dev-docs/upgrade-to-self-hosted.md` (locali, non pushati).
- **Seed dati test:** `dev-docs/seed_test_data.py` (locale).
- **Gotcha operativi:** Caddy↔rete `atlas-cmms_default` (permanente); dopo swap immagini → **`restart nginx`**; server compose usa **bind-mount** (mai `down -v`); SSH pilotato dall'assistente non possibile (chiave con passphrase + sudo).
