# Rotazione della chiave MinIO

> Procedura per sostituire `MINIO_PASSWORD` in produzione (`/srv/docker/atlas`).
> Motivo della rotazione (2026-09-17): fino alla correzione di `atlas-backup.sh` la secret
> finiva in chiaro in ogni archivio di backup. Vedi [README.md](README.md) §10-bis.

## Perche' non e' un semplice cambio di password

Nel `.env` c'e' **una sola** variabile, e alimenta due servizi:

```
MINIO_PASSWORD  ->  minio:  MINIO_ROOT_PASSWORD   (password root di MinIO)
                ->  api:    MINIO_SECRET_KEY      (il backend la usa per firmare gli URL)
```

Se i due valori si disallineano, il backend non riesce piu' a firmare ne' a leggere: **tutti gli
allegati smettono di funzionare**. Per questo `docker compose up -d` va lanciato una volta sola,
cosi' compose ricrea **entrambi** i container con il nuovo valore nello stesso momento.

Cosa **non** viene toccato: gli oggetti nel bucket. Non c'e' cifratura lato server (SSE-KMS), le
credenziali servono solo ad autenticare. I file restano dove sono.

## Passo 0 — Ricognizione (decide quale strada seguire)

MinIO cifra il proprio archivio IAM (utenti aggiuntivi, service account, policy) con le credenziali
root. Se ce ne sono, cambiare la root senza precauzioni li rende illeggibili. Verifica:

```bash
cd /srv/docker/atlas
set -a; . ./.env; set +a
docker run --rm --network atlas-cmms_default \
  -e MU="$MINIO_USER" -e MP="$MINIO_PASSWORD" \
  --entrypoint sh quay.io/minio/mc:latest -c '
    mc alias set a http://minio:9000 "$MU" "$MP" --api S3v4 >/dev/null
    echo "--- utenti IAM ---";      mc admin user list a
    echo "--- service account ---"; mc admin user svcacct ls a "$MU"
    echo "--- access key ---";      mc admin accesskey ls a 2>/dev/null || true
    echo "--- policy ---";          mc admin policy ls a
  '
```

> L'immagine `mc` ha `mc` come entrypoint: senza `--entrypoint sh` il comando verrebbe letto
> come `mc sh` e fallirebbe. Le credenziali passano da `-e`, non sulla riga di comando, cosi'
> non finiscono in `docker inspect`. Si usa `mc alias set` invece di `MC_HOST_...` perche'
> quest'ultimo e' una URL, e una secret base64 con `+` o `=` richiederebbe l'escaping.

- **Nessun utente e nessun service account** -> **strada A** (caso atteso: usiamo solo la root).
- **Ci sono voci IAM** -> **strada B**.

Sotto *policy* compaiono sempre le cinque predefinite di MinIO (`consoleAdmin`, `diagnostics`,
`readonly`, `readwrite`, `writeonly`): sono built-in e non contano. Conta solo la presenza di
utenti o service account creati da noi.

> Il sottocomando corretto e' `mc admin user svcacct` (non `mc admin svcacct`); nelle versioni
> recenti esiste anche `mc admin accesskey`. Verificabile con `mc admin user --help`.

> Nota: `mc` viene scaricato da `quay.io` perche' il repo Docker Hub `minio/minio` non esiste piu'.

## Passo 1 — Backup verificato

```bash
sudo ./atlas-backup-bratex.sh backup
tar -tzf /srv/data/backups/atlas_backups/atlas_backup_<TS>.tar.gz | head
```

Serve un backup **prima** della rotazione, e deve essere uno di quelli nuovi (senza credenziali
dentro). Conserva il valore vecchio di `MINIO_PASSWORD` finche' la verifica finale non e' passata:
e' il rollback.

## Passo 2 — Genera la nuova secret

```bash
openssl rand -base64 32
```

Requisiti MinIO: minimo 8 caratteri. Non usare caratteri che complichino il `.env`
(niente `#`, niente apici); il base64 va bene.

## Passo 3 — Aggiorna il `.env`

```bash
cd /srv/docker/atlas
sudo cp .env .env.pre-rotazione          # rollback
sudo sed -i 's|^MINIO_PASSWORD=.*|MINIO_PASSWORD=<NUOVA_SECRET>|' .env
grep '^MINIO_' .env                      # controlla che sia una riga sola e corretta
```

`MINIO_USER` resta invariato: il nome utente non e' il segreto, e cambiarlo aggiunge rischio senza
benefici.

## Passo 4 — Applica

### Strada A — nessuna voce IAM (caso normale)

```bash
sudo docker compose config -q && echo "COMPOSE OK"
sudo docker compose up -d                 # ricrea insieme minio E api
sudo docker compose restart nginx
sudo docker compose ps
```

### Strada B — ci sono voci IAM

MinIO deve ri-cifrare l'archivio IAM con la nuova chiave. Si fa passandogli **una volta sola** anche
le credenziali vecchie, poi si rimuovono.

1. Aggiungi temporaneamente al servizio `minio` in `docker-compose.yml`:

   ```yaml
         MINIO_ROOT_USER_OLD: ${MINIO_USER}
         MINIO_ROOT_PASSWORD_OLD: ${MINIO_PASSWORD_OLD}
   ```

2. Metti nel `.env` `MINIO_PASSWORD_OLD=<vecchia secret>` accanto alla nuova `MINIO_PASSWORD`.
3. `sudo docker compose up -d` e controlla nei log di `atlas_minio` che l'avvio sia pulito.
4. **Rimuovi** le due righe `*_OLD` dal compose e `MINIO_PASSWORD_OLD` dal `.env`, poi
   `sudo docker compose up -d` di nuovo.

## Passo 5 — Verifica (nell'ordine)

```bash
# 1. i container sono su
sudo docker compose ps

# 2. MinIO accetta le NUOVE credenziali
cd /srv/docker/atlas && set -a; . ./.env; set +a
docker run --rm --network atlas-cmms_default \
  -e MU="$MINIO_USER" -e MP="$MINIO_PASSWORD" \
  --entrypoint sh quay.io/minio/mc:latest -c '
    mc alias set a http://minio:9000 "$MU" "$MP" --api S3v4 && mc ls a/atlas-bucket
  '

# 3. il backend e' ripartito
sudo docker compose logs --tail=30 api | grep -E 'Started ApiApplication|ERROR'
```

**4. La prova che conta: apri un allegato dall'interfaccia web.** E' l'unica verifica che copre
tutta la catena: backend che firma l'URL -> nginx `/storage` -> MinIO che valida la firma. Se
l'allegato si scarica, la rotazione e' riuscita.

```bash
# 5. anche il backup funziona con le nuove credenziali
sudo ./atlas-backup-bratex.sh backup
```

## Rollback

Finche' non hai confermato il punto 4, il ritorno indietro e' immediato:

```bash
cd /srv/docker/atlas
sudo cp .env.pre-rotazione .env
sudo docker compose up -d && sudo docker compose restart nginx
```

Gli oggetti nel bucket non sono stati toccati in nessun caso, quindi non c'e' rischio di perdita
dati: il peggio che puo' succedere e' che gli allegati non siano raggiungibili finche' non si
riallineano le credenziali.

## Dopo

- I backup precedenti al 2026-09-17 contengono la secret **vecchia**, che da ora e' inutile.
- Aggiorna la riga della rotazione in [PROJECT-STATUS.md](PROJECT-STATUS.md).
- Momento consigliato: fuori orario. Tra il riavvio di `minio` e quello di `api` gli allegati sono
  brevemente non raggiungibili.
