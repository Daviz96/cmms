# Piano — Sincronizzare il fork con l'upstream (Grashjs/cmms)

> **Stato: PROPOSTA / da eseguire in sessione dedicata** (documento 2026-09-01). Basato su un **dry-run reale**
> del merge (fatto e poi `--abort`, `self-hosted` intatto). Obiettivo: integrare le novità upstream **senza
> rompere** le nostre funzionalità self-hosted.

## 1. Situazione misurata

- **Remote:** `origin` = `Daviz96/cmms` (il nostro fork), `upstream` = `Grashjs/cmms` (già configurato + fetchato).
- **Punto di fork (merge-base):** `e1d24406` (24 ago 2026) = il nostro `main`.
- **Divergenza dal fork:**
  - upstream/main: **+32 commit** (feature/fix).
  - `self-hosted`: **+4 commit** (MOD self-hosted + fix Bug 1/Bug 3 + feature mail).
- **`LicenseService` NON toccato da upstream** → la nostra modalità self-hosted licensing è al sicuro.

## 2. Esito del dry-run (`git merge --no-commit upstream/main` su branch usa-e-getta)

- **Conflitti testuali da risolvere a mano: 4 file, 1 hunk ciascuno**
  1. `api/src/main/java/com/grash/service/MinioService.java` — nostre modifiche storage self-hosted **vs**
     upstream `ce75e31f` ("file upload usa company ID invece del parametro folder").
  2. `api/src/main/resources/mailMessages.properties`
  3. `api/src/main/resources/mailMessages_it_IT.properties`
  4. `api/src/main/resources/mailMessages_pl_PL.properties`
     → nostre chiavi mail (download APK/QR + config server) **vs** upstream `dc8a8603` ("i18n files").
     Probabile risoluzione: **tenere entrambi i set di chiavi** (aggiunte adiacenti).
- **129 file auto-mergiati** da Git senza conflitto.

## 3. ⚠️ File auto-mergiati da RILEGGERE (rischio semantico)

Auto-merge OK = nessun conflitto *testuale*, **non** garanzia di correttezza. Rileggere e testare:

| File | Nostra modifica | Modifica upstream | Perché rileggere |
|---|---|---|---|
| `controller/AuthController.java` | fix Bug 1 (`logout` → `invalidateSessionsById`) | `d7e7ec00`+`714a99dc` **rework eliminazione account** (flusso request+conferma) | **ALTA** — entrambe presenti dopo il merge; verificare che coesistano e che `deleteAccount` sia quello nuovo upstream |
| `service/UserService.java` | invito (Bug 2 lato repo) + `invalidateSessionsById` | varie | media |
| `service/WorkOrderService.java` | fix Bug 3 (query session-safe) | `4e307282` webhook, `7d31186b` counts | media |
| `service/GCPService.java` | storage self-hosted | `ce75e31f` upload by company ID | media (storage) |
| `resources/application.yml` | config self-hosted | modifiche upstream | media (verificare nessuna proprietà persa) |
| `mobile/i18n/en.ts`, `pl.ts` | nostre traduzioni | `dc8a8603` | bassa |

## 4. ⚠️ Altri punti di attenzione

1. **Migrazione DB `bdd94408`** (`Part.version` non-nullable + migration record esistenti): giriamo
   `ddl-auto: validate` + Liquibase sul **DB live**. **Testare su una copia del DB live** prima del deploy.
   (Nota: `Part` è l'unica entità con `@Version`; questo cambia il suo comportamento — verificare che non
   introduca `OptimisticLocking` inaspettati.)
2. **Eliminazione account (upstream `d7e7ec00`/`714a99dc`)**: upstream ha rifatto il flusso in "request +
   confirmation". **Interseca** sia il nostro fix Bug 1 sia il piano
   [restrict-user-deletion-to-admins-plan.md](restrict-user-deletion-to-admins-plan.md). **Decisione:** dopo il
   merge, valutare se il flusso upstream copre già la nostra esigenza (bloccare l'auto-eliminazione pericolosa) →
   eventualmente il piano di restrizione si semplifica o si annulla.
3. **CSP / nginx** (`211413b4`, `fa8485ed`): modifiche header/CSP → verificare compatibilità col nostro setup
   **Caddy** (rev-proxy) e con l'APK servita da `/download/*`.
4. **File upload** (`ce75e31f`): tocca lo storage → coordinare con MinIO/GCP self-hosted (conflitto già noto in §2).

## 5. Procedura (sicura, non distruttiva)

1. **Branch di lavoro** dal nostro: `git checkout -b sync-upstream self-hosted`. **Non toccare** `self-hosted`/`main`.
2. `git merge upstream/main`.
3. **Risolvere i 4 conflitti** (§2) preservando il comportamento self-hosted (storage MinIO, chiavi mail nostre).
4. **Rileggere i file §3** (soprattutto `AuthController`) e correggere eventuali problemi semantici.
5. `git commit` del merge.
6. **Build** `docker build ./api` + `docker build ./frontend` (valida la compilazione) → **eseguire i test backend**.
7. **Deploy su ambiente di TEST** (stack locale o DB seedato con `dev-docs/seed_test_data.py`), **mai diretto sul live**.
   Verificare: login/rate-limit, storage (upload file), eliminazione account, ricerca WO, mail invito.
8. **Migrazione DB**: testare su **copia del DB live** (dump → restore in un Postgres di test → avviare il backend
   nuovo → controllare che Liquibase applichi `bdd94408` senza errori).
9. Solo dopo OK completo: `git checkout self-hosted && git merge sync-upstream` → rebuild immagini
   `self-hosted-vX.Y.Z` → push → deploy sul live (`pull` + `up -d` + `restart nginx`).

## 6. Rollback
- Il merge è su un branch separato: se qualcosa va storto, si **abbandona il branch** senza toccare `self-hosted`.
- Sul live: le immagini precedenti (`self-hosted-v1.0.3` backend, `v1.0.2` frontend) restano su Docker Hub →
  rollback = ripristinare i tag vecchi nel compose + `pull` + `up -d` + `restart nginx`.
- DB: backup **prima** del deploy (la migrazione `Part.version` modifica dati).

## 7. Cadenza futura
- Sincronizzare **spesso e in piccoli batch** (`git fetch upstream` + merge periodici) per tenere la superficie
  di conflitto minima (oggi: 4 file). Rimandare a lungo = conflitti che crescono.

---
**Stima:** bassa-media. Il merge in sé è piccolo (4 conflitti da 1 hunk); il grosso del lavoro è **rilettura
semantica** dei file §3 + **test** (build, test backend, migrazione DB su copia, smoke test funzionale).
**Prossimo passo:** eseguire §5 in una sessione dedicata (branch `sync-upstream`), quando decidi di procedere.
