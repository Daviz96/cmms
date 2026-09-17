# Atlas CMMS — versione self-hosted Bratex

> README della **nostra** versione di Atlas CMMS (fork `Daviz96/cmms`, branch `self-hosted`).
> Il [README.md](../README.md) alla radice è quello di upstream `Grashjs/cmms` e descrive il
> prodotto ufficiale: **non** riflette le nostre modifiche, le nostre immagini né il nostro deploy.
>
> Stato e changelog sempre aggiornati → **[PROJECT-STATUS.md](PROJECT-STATUS.md)** (fonte di verità).
> Guida di sviluppo per l'assistente → [CLAUDE.md](CLAUDE.md).

---

## 1. Cos'è questa versione

Atlas CMMS è un gestionale di manutenzione (work order, asset, ricambi, preventive maintenance).
Questa è la nostra installazione self-hosted, in produzione su `https://cmms.firmabratex.pl`
(solo LAN, dietro Caddy con TLS wildcard).

Differenza sostanziale rispetto a upstream: **non serve una licenza commerciale**. La modalità
`LICENSING_SELF_HOSTED_MODE` concede le funzionalità localmente, senza chiamate a Keygen
(MOD-001). Di conseguenza white-labeling, LDAP e le altre feature "a pagamento" sono attive.

## 2. Cosa abbiamo aggiunto rispetto a upstream

| Area | Nostra modifica | Versione |
|---|---|---|
| **Licensing** | Modalità self-hosted: entitlement concessi localmente, nessun Keygen | v1.0.0 |
| **Storage** | MinIO/GCP self-hosted; cancellazione degli oggetti quando si elimina un file (MOD-004B) | v1.0.0 |
| **Mail** | Immagini inline via CID; sezione **download APK + QR** e istruzioni di configurazione server nelle mail di invito/benvenuto | v1.0.1 |
| **Utenti** | Admin può **creare** un utente (non solo invitarlo), con link "imposta password" — nessuna password in chiaro via mail | v1.2.0 |
| **Asset** | **Visibilità asset per assegnazione**: un ruolo con `view ASSETS` ma senza `viewOther ASSETS` vede solo gli asset assegnati (creatore, primaryUser, assignedTo, team), in lista, albero e picker | v1.3.0 |
| **i18n** | Correzioni polacco (backend, frontend, mobile) | varie |
| **LDAP** | Configurazione e hardening LDAP/AD (MOD-003A) | v1.0.0 |
| **WebSocket** | Refresh del token e riconnessione su CONNECT scaduto (import/export/notifiche) | v1.2.2 |
| **Sicurezza** | `sentry.send-default-pii` forzato a `false`: nessun dato utente fuori sede | v1.4.0 |

## 3. Immagini Docker

Le nostre immagini, **non** quelle di upstream (`intelloop/*`):

```
dablio96/self-hosted-cmms-backend:self-hosted-vX.Y.Z
dablio96/self-hosted-cmms-frontend:self-hosted-vX.Y.Z
```

`latest` punta all'ultima versione in produzione. Le `-rcN` sono release candidate da testare
prima della promozione.

⚠️ **I due repo non hanno gli stessi tag.** Le release non sono sempre sincronizzate: `v1.2.1` fu
solo backend, `v1.2.2` solo frontend. Un tag che esiste per il backend puo' non esistere per il
frontend. Per questo in `docker-compose.prod.yml` le due righe `image:` sono **indipendenti** e si
aggiornano separatamente: non esiste una variabile unica che le tenga insieme, proprio per non
poter chiedere un tag inesistente.

Il tag sta nel compose, non nel `.env`: `grep 'image: dablio96' docker-compose.yml` dice sempre
cosa gira, e il commit che tocca quelle righe e' il registro del deploy.

## 4. File di compose: quale usare

| File | Uso |
|---|---|
| `docker-compose.yml` | **Sviluppo**: builda backend e frontend dai sorgenti (`atlas-cmms-backend:local`, `atlas-cmms-frontend:local`). Volumi Docker normali, nginx esposto su `3000`. |
| `docker-compose.prod.yml` | **Produzione** (`/srv/docker/atlas`): immagini pubblicate su Docker Hub con il **tag scritto esplicitamente**, nginx senza porta host (ci arriva Caddy sulla rete Docker), dati in **bind-mount** su `/srv/data/databases/atlas/*`. |

## 5. Avvio in locale (sviluppo)

```bash
cp .env.example .env          # poi valorizza almeno POSTGRES_*, MINIO_*, JWT_SECRET_KEY
docker compose up -d --build  # builda api e frontend dai sorgenti
```

Interfaccia su `http://localhost:3000`.

`JWT_SECRET_KEY` deve essere **base64 valido** (`openssl rand -base64 32`): un valore con
caratteri non-base64 fa fallire l'avvio del backend con `Illegal base64 character`.

## 6. Deploy in produzione

```bash
# 1. backup (obbligatorio)
docker exec atlas_db pg_dump -U atlas atlas > atlas_$(date +%F).sql

# 2. aggiorna il tag nel compose (righe image: di api e/o frontend)
#    le due righe sono INDIPENDENTI: se la release tocca solo il backend, lascia fermo il frontend
sudo grep -n 'image: dablio96' docker-compose.yml

# 3. swap — pullare SOLO api e frontend (vedi §7)
docker compose pull api frontend
docker compose up -d
docker compose restart nginx

# rollback: rimetti i tag precedenti nelle righe image: e ripeti il passo 3
```

Il deploy va eseguito a mano sul server: la chiave SSH ha passphrase e richiede sudo, quindi
non è pilotabile dall'assistente.

## 7. Trappole operative — leggere prima di toccare la produzione

1. **Mai `docker compose down -v`.** I dati (Postgres e MinIO) sono in bind-mount su
   `/srv/data/databases/atlas/`. Il flag `-v` li distrugge.
2. **Mai `docker compose pull` senza argomenti.** Il repo Docker Hub `minio/minio` **non esiste
   più** (404, upstream è passato a `alpine/minio`). La nostra immagine vive solo nella cache
   locale del server e su `quay.io/minio/minio`. Un pull globale fallisce. Pullare `api frontend`.
3. **Non aggiornare MinIO** senza un'operazione dedicata con backup del bucket: il downgrade del
   formato dati non è garantito e romperebbe il rollback.
4. **Dopo lo swap delle immagini: `restart nginx`.** Altrimenti nginx resta agganciato ai vecchi
   IP dei container e risponde 502.
5. **Se un container frontend non parte** e il log dice `Error getting 'X' from process.env`:
   l'immagine richiede una variabile che il compose non passa. `runtime-env-cra` pretende che
   **tutte** le chiavi di `frontend/.env.example` siano presenti nell'ambiente, anche vuote.
6. **Caddy** è sulla rete `atlas-cmms_default` (collegamento permanente) e raggiunge `atlas_nginx:80`.
   La porta host `3000` è chiusa dal 2026-09-02.

## 8. Variabili d'ambiente specifiche nostre

Oltre a quelle del [README upstream](../README.md#set-environment-variables):

| Variabile | Default | Descrizione |
|---|---|---|
| `LICENSING_SELF_HOSTED_MODE` | `false` | **`true` in produzione.** Concede gli entitlement localmente, senza Keygen. |
| `LICENSE_FINGERPRINT_REQUIRED` | `false` | Lasciare `false` in self-hosted. |
| `SENTRY_DSN` | vuoto | Vuoto = Sentry **disattivato**. Da lasciare vuoto salvo decisione esplicita. |
| `SENTRY_SEND_PII` | `false` | Nostra aggiunta: upstream lo hardcoda a `true`. Non alzarlo. |
| `CLARITY_ID` | vuoto | Microsoft Clarity, disattivato. La variabile deve comunque **esistere** (vedi §7.5). |

## 9. App mobile

**Non** usare l'app ufficiale dagli store: serve il nostro **APK self-hosted**. Il link e il QR
code arrivano nelle mail di invito e benvenuto; nel frontend c'è il dialog "scarica app".
Alla prima apertura: schermata di login → "Custom server" → indirizzo del server → Salva.

## 10. Sincronizzarsi con upstream

Procedura in [upstream-sync-plan.md](upstream-sync-plan.md); esempio completo di un sync reale,
con conflitti, deviazioni e test, in [upstream-sync-2026-09.md](upstream-sync-2026-09.md).

In breve: branch usa-e-getta da `self-hosted`, `git merge upstream/main`, risoluzione conflitti,
**adattamento dei test upstream** al nostro comportamento (è la parte più lunga: upstream scrive
test che asseriscono il proprio comportamento, non il nostro), build, test sui dati reali, rc,
deploy, promozione. Sincronizzare **spesso e in piccoli batch**.

## 11. Dove guardare

| Domanda | Documento |
|---|---|
| Cosa gira in produzione, con che versione | [PROJECT-STATUS.md](PROJECT-STATUS.md) |
| Come è fatto il codice, regole di sviluppo | [CLAUDE.md](CLAUDE.md) |
| Schema del database | [database-schema.md](database-schema.md) |
| Bug storici risolti | [live-deployment-bugs-handoff.md](live-deployment-bugs-handoff.md) |
| Ultimo sync upstream | [upstream-sync-2026-09.md](upstream-sync-2026-09.md) |
| Feature asset scoping | [feature-proposals/asset-visibility-scoping.md](feature-proposals/asset-visibility-scoping.md) |
