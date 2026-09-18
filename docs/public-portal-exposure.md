# Esporre il portale zgłoszeń su internet

> **Stato: PIANO, da eseguire.** Obiettivo: un dipendente deve poter inquadrare il QR code
> e inviare una segnalazione **anche da rete mobile**, senza essere sul WiFi aziendale.
> Ambito deciso: **solo il portale**. Login, API di gestione e BookStack restano in LAN.

## 1. Situazione di partenza (verificata 2026-09-18)

```
cmms.firmabratex.pl   -> 192.168.101.80    record PUBBLICO con IP privato (per questo oggi e' LAN-only)
firmabratex.pl        -> 46.242.239.239    il sito, ospitato da home.pl
IP pubblico sede      -> 193.34.139.17     uscita dell'ufficio, STATICO, diverso dal sito
zona DNS pubblica     -> pannello home.pl, gestita da noi
DNS interno           -> Windows Server (AD), risolve gia' cmms e wiki su 192.168.101.80
router/firewall       -> sotto il nostro controllo
certificato           -> wildcard *.firmabratex.pl caricato a mano in Caddy, NON ACME
```

Il certificato wildcard copre gia' qualsiasi nuovo sottodominio: **non serve configurare ACME
ne' DNS-01**.

## 2. ⚠️ Il rischio principale: la 443 espone TUTTO Caddy

Caddy smista per hostname, ma ascolta su una porta sola. Oggi serve tre siti:

```
wiki.firmabratex.pl   -> bookstack
cmms.firmabratex.pl   -> atlas_nginx  (+ /download/* con l'APK)
```

Inoltrando la 443 dal router, **chiunque su internet puo' raggiungere anche wiki e cmms**: basta
puntare quegli hostname al nostro IP con un file `hosts` o un resolver custom. Gli hostname non
sono segreti e il wildcard li certifica tutti.

Quindi il primo lavoro non e' aprire, e' **chiudere alla LAN i due siti interni**. Senza questo
passo, "espongo solo il portale" diventa "espongo BookStack e il CMMS completo".

## 3. Prerequisiti — tutti soddisfatti

- IP pubblico `193.34.139.17`: **statico** (confermato 2026-09-18). Nessun DDNS necessario, il
  record A si scrive una volta sola.
- Router/firewall: sotto il nostro controllo.
- Zona DNS: gestita da noi dal pannello home.pl.
- Certificato: wildcard gia' valido per qualsiasi sottodominio nuovo.

Non restano incognite: il piano si puo' eseguire per intero.

## 4. Passo 1 — Rinforzare PRIMA di aprire

L'ordine conta: questi tre punti vanno chiusi mentre il server e' ancora irraggiungibile.

### 4.1 Chiudere la registrazione libera

`ALLOWED_ORGANIZATION_ADMINS` e' **vuota**. Con quel valore il controllo in `UserService.signup`
viene saltato: chi raggiunge `/auth/signup` puo' creare un account e una propria organizzazione
sulla nostra istanza.

```bash
cd /srv/docker/atlas
sudo cp .env .env.pre-esposizione
# elenco separato da virgole delle email autorizzate a creare organizzazioni
sudo sed -i 's|^ALLOWED_ORGANIZATION_ADMINS=.*|ALLOWED_ORGANIZATION_ADMINS=nome@firmabratex.pl|' .env
```

Nota: `INVITATION_VIA_EMAIL=true`, quindi nessuno puo' entrare **nella nostra azienda** senza
invito. Il rischio era solo la creazione di organizzazioni parassite.

### 4.2 Attivare reCAPTCHA

Il portale non ha protezione anti-spam. Un QR attaccato a una macchina e' fotografabile e l'UUID
nell'URL **non e' un segreto**: chi ce l'ha puo' inviare segnalazioni in massa.

⚠️ **Le due chiavi vanno valorizzate INSIEME.** Il frontend esegue il captcha solo se
`RECAPTCHA_SITE_KEY` e' impostata; il backend lo pretende se `RECAPTCHA_SECRET_KEY` lo e'. Con
una sola delle due, ogni invio fallisce con "Recaptcha token missing".

```bash
sudo sh -c 'cat >> /srv/docker/atlas/.env' <<'EOF'
RECAPTCHA_SITE_KEY=...
RECAPTCHA_SECRET_KEY=...
EOF
```

Chiavi da Google reCAPTCHA (il frontend usa la modalita' **invisibile**, `executeAsync`).

### 4.3 Applicare e verificare, ancora in LAN

```bash
sudo docker compose up -d && sudo docker compose restart nginx
# prova un invio dal portale sul WiFi: deve funzionare CON il captcha attivo
```

Se l'invio fallisce qui, non proseguire: si sistemano le chiavi prima di esporre.

## 5. Passo 2 — Caddyfile

`/srv/docker/proxy/Caddyfile`. Fare una copia prima: `sudo cp Caddyfile Caddyfile.pre-esposizione`.

```caddy
(lan_only) {
	@not_lan not remote_ip 192.168.0.0/16 10.0.0.0/8 172.16.0.0/12 127.0.0.1/8
	respond @not_lan 403
}

wiki.firmabratex.pl {
	tls /certs/wildcard.crt /certs/wildcard.key
	import lan_only
	reverse_proxy bookstack:80
}

cmms.firmabratex.pl {
	tls /certs/wildcard.crt /certs/wildcard.key
	import lan_only
	handle_path /download/* {
		root * /srv/data/applications/caddy/download
		file_server
	}
	handle {
		reverse_proxy atlas_nginx:80
	}
}

zgloszenia.firmabratex.pl {
	tls /certs/wildcard.crt /certs/wildcard.key

	# Gli unici endpoint API di cui il portale ha bisogno.
	@api_portale {
		path /api/request-portals/public/*
		path /api/requests/portal/*
		path /api/files/upload/request-portal/*
		path /api/instance-config
		path /api/locations/public/mini/*
		path /api/assets/public/mini/*
	}
	@api_resto {
		path /api/*
	}

	handle @api_portale {
		reverse_proxy atlas_nginx:80
	}
	handle @api_resto {
		respond 403
	}
	# Tutto il resto = file statici dell'app React (serve anche la pagina del portale).
	handle {
		reverse_proxy atlas_nginx:80
	}
}
```

⚠️ **I blocchi non possono stare su una riga sola.** `handle @x { direttiva }` fa fallire
l'adattamento con `Unexpected next token after '{' on same line`: la graffa aperta dev'essere
l'ultimo token della riga. (I `path` ripetuti dentro un matcher invece vanno bene: Caddy li
unisce in un unico elenco in OR.)

Questo blocco e' stato **validato** con `caddy validate` e `caddy adapt`: il matcher del portale
compila correttamente tutti e sei i percorsi.

**Cosa resta visibile da fuori, e perche':** il frontend e' un'unica applicazione React servita
da `/`, quindi esponendo il portale si espongono anche i file della pagina di login. Non e'
separabile a livello di percorso. Pero' `/api/auth/*` risponde 403, quindi e' una facciata
inutilizzabile: nessuna credenziale puo' essere verificata. `/storage/*` non e' nell'elenco
perche' il portale carica le foto tramite l'API e non legge da MinIO.

Applicare e verificare **dalla LAN**, prima di aprire il router:

```bash
sudo docker exec caddy caddy validate --config /etc/caddy/Caddyfile   # adattare al nome container
sudo docker restart caddy
curl -sI https://cmms.firmabratex.pl | head -1                        # 200, dalla LAN
curl -sI https://zgloszenia.firmabratex.pl/request-portal/<uuid> | head -1   # 200
curl -sI https://zgloszenia.firmabratex.pl/api/auth/signin | head -1          # 403
```

Per provare il nuovo hostname prima che il DNS esista, aggiungere temporaneamente
`192.168.101.80 zgloszenia.firmabratex.pl` al proprio `hosts`.

## 6. Passo 3 — Record DNS pubblico

Nel pannello home.pl:

```
zgloszenia.firmabratex.pl   A   193.34.139.17
```

**Non** toccare il record `cmms`: deve restare su `192.168.101.80`, e' quello che lo tiene
irraggiungibile da fuori.

## 7. Passo 4 — Port forward

Sul router: **solo la 443** verso `192.168.101.80:443`.

Non inoltrare la 80: il QR contiene gia' `https://`, il certificato e' manuale e non serve
HTTP-01. Una porta in meno e' una porta in meno.

## 8. Passo 5 — Verifica dall'esterno

**Da rete mobile, WiFi aziendale spento:**

| Prova | Atteso |
|---|---|
| `https://zgloszenia.firmabratex.pl/request-portal/<uuid>` | il form si apre |
| invio di una segnalazione con foto | arriva in piattaforma |
| `https://zgloszenia.firmabratex.pl/api/auth/signin` | **403** |
| `https://zgloszenia.firmabratex.pl/api/swagger-ui/index.html` | **403** |
| `https://cmms.firmabratex.pl` (con hosts -> 193.34.139.17) | **403** |
| `https://wiki.firmabratex.pl` (idem) | **403** |

Le ultime due sono le piu' importanti: dimostrano che la chiusura alla LAN funziona davvero.
Se rispondono 200, fermarsi e richiudere il port forward.

## 9. Passo 6 — Record sul DNS interno (split DNS)

Abbiamo un DNS interno che controlliamo: il Windows Server di dominio, dove `cmms` e `wiki`
risolvono gia' su `192.168.101.80`. Sfruttarlo elimina alla radice il problema del NAT loopback.

Aggiungere lo stesso tipo di record, con la struttura gia' usata per gli altri due:

```
zgloszenia.firmabratex.pl   A   192.168.101.80
```

Risultato:

| Chi risolve | Dove | Percorso |
|---|---|---|
| dispositivi sul WiFi aziendale | AD DNS | 192.168.101.80, il traffico resta in LAN |
| chiunque da internet | zona home.pl | 193.34.139.17, entra dal port forward |

Fatto questo, il file `hosts` sul PC non serve piu' per le prove interne.

⚠️ **Attenzione ai telefoni con "DNS privato" (DoH/DoT).** Android e iOS recenti possono
scavalcare il DNS della rete: quei dispositivi otterrebbero l'IP pubblico anche stando in
ufficio, e dipenderebbero dal NAT loopback. E' lo scenario esatto del nostro caso d'uso, quindi
**provare il QR con un telefono connesso al WiFi aziendale** prima di stamparne una serie.

## 10. Passo 7 — Rigenerare i QR code

Il link del portale e' costruito da `window.location.origin`, non da una variabile di
configurazione. Quindi **basta aprire la pagina di condivisione dal nuovo hostname** e il QR
generato puntera' li' da solo. Nessuna modifica a `PUBLIC_SERVER_URL` — che non va toccata,
perche' guida anche gli URL firmati di MinIO.

I QR gia' stampati con `cmms.firmabratex.pl` continueranno a funzionare **solo in LAN**: vanno
sostituiti.

## 11. Passo 8 (successivo) — Rate limiting

reCAPTCHA copre lo spam automatico, ma non un abuso mirato. Nota: **Caddy non ha rate limiting
integrato**, servirebbe ricompilarlo con il modulo `caddy-ratelimit`. Molto piu' semplice usare
nginx, che ce l'ha nativo e il cui file e' gia' versionato nel nostro repo (`nginx.conf`).

⚠️ Perche' funzioni, nginx deve vedere l'**IP reale** del client: dietro Caddy vedrebbe sempre
l'IP di Caddy e limiterebbe tutti insieme. Serve quindi `set_real_ip_from` sulla rete Docker piu'
`real_ip_header X-Forwarded-For`, altrimenti il limite e' inutile o dannoso.

## 12. Rollback

Ogni passo e' reversibile e in ordine inverso:

1. **Chiudere il port forward** sul router — taglia l'accesso esterno immediatamente
2. `sudo cp Caddyfile.pre-esposizione Caddyfile && sudo docker restart caddy`
3. Rimuovere il record `zgloszenia` dal pannello home.pl
4. `sudo cp .env.pre-esposizione .env && sudo docker compose up -d && sudo docker compose restart nginx`

Il punto 1 da solo riporta la situazione allo stato di oggi.

## 13. Cosa resta esposto, consapevolmente

- **I file statici dell'app React**, quindi anche la pagina di login. Inutilizzabile:
  `/api/auth/*` risponde 403.
- **L'UUID del portale**, che non e' un segreto per costruzione: chi ha il QR puo' inviare
  segnalazioni. E' lo scopo. reCAPTCHA e rate limiting sono le contromisure, non il segreto
  dell'URL.
- **L'IP pubblico della sede**, ora associato a un hostname noto.

## 14. Pulizia possibile, in seguito

Nella zona pubblica di home.pl, `cmms.firmabratex.pl` punta a `192.168.101.80`: un indirizzo
privato, inutilizzabile da fuori, che serve solo ai dispositivi in LAN che **non** usano l'AD DNS.
Se tutti passano dal DNS di dominio, quel record espone il nostro indirizzamento interno senza
dare nulla in cambio e si puo' rimuovere. Da valutare **dopo** aver completato l'esposizione, non
durante.
