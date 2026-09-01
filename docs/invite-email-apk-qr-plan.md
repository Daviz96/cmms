# Piano — Mail di invito: QR + download APK Android + istruzioni

> Documento di **progetto** (design), richiesto prima di qualsiasi modifica al codice. Nessuna
> modifica applicata: qui si definiscono obiettivo, opzioni, decisioni e piano di implementazione.

## 1. Obiettivo

Arricchire la **mail di invito** in modo che il destinatario possa:
1. **scaricare l'APK Android custom** (la nostra build self-hosted) tramite un **QR code** e/o un link diretto;
2. leggere **istruzioni** chiare per **installare l'app** e **configurare il server custom**.

Ambito: **solo Android** (l'APK iOS non è ancora prodotto/distribuito → vedi §7). Deploy **LAN-only**.

## 2. Stato attuale (come funziona la mail oggi)

- Template invito: [`api/src/main/resources/templates/invite.html`](../api/src/main/resources/templates/invite.html) — è "sottile": richiama il layout condiviso
  [`templates/fragments/main-layout.html`](../api/src/main/resources/templates/fragments/main-layout.html) → fragment `emailTemplate(headerText, preheaderText, messageText, buttonText, buttonLink)`.
- Testi (i18n) in [`mailMessages.properties`](../api/src/main/resources/mailMessages.properties) + varianti per lingua (`_it_IT`, `_pl_PL`, …). Chiavi invito:
  `inviteTitle`, `inviteHeader`, `inviteMessage`, `joinCompany`. Il pulsante punta a `joinLink` (URL di accettazione invito).
- Invio: logica in `UserService`/`UserInvitationService` + MailService (Thymeleaf + JavaMail), locale del destinatario.

## 3. Scoperte che semplificano il lavoro

1. **Il layout carica già immagini da URL del backend**: il logo è `environment.getProperty('api.host') + '/images/logo.png'`, servito dalla cartella statica montata (`./logo:/app/static/images` → path `/images/…`). ⇒ **il QR può essere ospitato allo stesso modo** (`/images/atlas-qr.png`) e referenziato con `<img>`, **senza codice Java**.
2. **`messageText` è renderizzato con `th:utext`** (HTML non-escapato) ⇒ possiamo iniettare blocchi HTML (sezioni, liste di passi).
3. **L'URL del server è già una property** (`api.host`/`PUBLIC_SERVER_URL`) ⇒ le istruzioni possono mostrarlo dinamicamente senza hardcode.

Conseguenza: gran parte del lavoro è **template + testi i18n + hosting di 2 file statici** (APK e PNG del QR). Modifiche Java **solo se** si vuole il QR generato dinamicamente (§4.2, Opzione B).

## 4. Design proposto (decisioni + opzioni)

### 4.1 Dove hostare l'APK

| Opzione | Come | Pro | Contro |
|---|---|---|---|
| **A. Caddy static (consigliata)** | Route `handle /download/*` → `file_server` da una cartella su disco (es. `/srv/atlas/public/`) | Caddy è già il proxy esterno; MIME corretto facile; zero backend | Aggiunge un blocco al Caddyfile |
| B. nginx location | `location /download/` nel `nginx.conf` + volume | Sta nello stack compose | Modifica nginx.conf + mount |
| C. MinIO bucket pubblico | Upload APK, link `/storage/…` | Riusa storage esistente | Config bucket pubblico/presigned |
| D. Backend `/images` | Mettere l'APK nel volume `logo/` | Nessuna infra nuova | Impropio (APK sotto /images); MIME |

**Raccomandazione: A (Caddy).** URL stabile e semplice, es. `https://<dominio-LAN>/download/atlas-cmms.apk`.
Impostare il MIME `application/vnd.android.package-archive`. Nome file **stabile** (non versionato nell'URL) così il link/QR nel template non cambia ad ogni release; la versione si mette nel filename di cortesia o in un `atlas-cmms-<ver>.apk` con un symlink/alias a `atlas-cmms.apk`.

### 4.2 QR code — cosa codifica e come si genera/mostra

- **Cosa codifica:** l'URL di download dell'APK (§4.1), es. `https://<dominio-LAN>/download/atlas-cmms.apk`. È un URL **fisso** → il QR è **statico** (uguale per tutti gli inviti).
- **Come mostrarlo nella mail:**
  - **Opzione 1 (consigliata): immagine hostata** come il logo → `<img src="…/images/atlas-qr.png">`. Coerente con il meccanismo esistente, **zero codice**. (Limite: i client email spesso bloccano le immagini remote finché l'utente non clicca "mostra immagini", e off-LAN non si caricano — vedi §7. Stesso limite del logo attuale.)
  - **Opzione 2 (robusta): immagine inline CID** allegata alla mail → renderizza sempre. Richiede modifica al MailService (aggiungere inline part con `Content-ID`).
- **Come generare il PNG del QR:**
  - **Opzione A (semplice, consigliata per ora): pre-generato** una volta in fase di deploy, codificando l'URL reale, e messo nella cartella statica (`logo/atlas-qr.png` → `/images/atlas-qr.png`). Nessuna dipendenza nuova.
  - **Opzione B (dinamica): generazione backend** (libreria ZXing) da property → sempre allineato all'URL configurato. Aggiunge dipendenza + endpoint/util Java.

**Raccomandazione:** QR **statico pre-generato** (A) mostrato come **immagine hostata** (1) per la prima versione; valutare CID inline (2) / generazione dinamica (B) se emergono problemi di rendering.

### 4.3 Contenuti della mail (nuove sezioni)

Aggiungere sotto il messaggio d'invito due blocchi (testi da i18n, §4.4):
- **📱 Scarica l'app (Android):** frase + **QR** + **pulsante/link diretto** al download. (Il link tappabile serve a chi legge la mail **sul telefono**; il QR a chi la legge **su desktop** e scansiona col telefono.)
  - Nota installazione: "consenti l'installazione da origini sconosciute" (sideload fuori dal Play Store).
- **⚙️ Configura il server:** passi — apri l'app → schermata Login → **"Custom server"** → inserisci `https://<dominio-LAN>/api` → **Salva** → accedi con le tue credenziali. (L'URL del server mostrato dinamicamente dalla property.)

### 4.4 Lingue

Nuove chiavi i18n in **base** (`mailMessages.properties`) + almeno **italiano** (`_it_IT`) e **polacco** (`_pl_PL`), coerenti col target del progetto. Le altre lingue ereditano il fallback base finché non tradotte.
Chiavi proposte (nomi indicativi): `downloadAppTitle`, `downloadAppAndroid`, `downloadAppButton`, `installUnknownSources`, `configureServerTitle`, `configureServerSteps` (o passi separati `configStep1..3`), `scanQrHint`.

## 5. Modifiche file-per-file (previste)

| File | Modifica |
|---|---|
| `templates/invite.html` (o il fragment) | aggiungere sezione "download app" (QR `<img>` + link) e "configura server" (passi); usare `api.host` per URL server/QR/APK |
| `templates/fragments/main-layout.html` | **opzionale**: estendere `emailTemplate` con un blocco "extra content", oppure lasciare il layout e mettere le sezioni in `invite.html` dopo il fragment |
| `mailMessages.properties` (+ `_it_IT`, `_pl_PL`) | nuove chiavi testo (§4.4) |
| `logo/atlas-qr.png` (volume statico) **oppure** MailService (CID) | il PNG del QR (Opzione 1/A) |
| **Infra: Caddyfile** (sul server) | route `/download/*` → file_server per servire l'APK; MIME apk |
| **Infra: file APK** | upload di `atlas-cmms.apk` nella cartella servita |
| `UserService`/`UserInvitationService`/MailService | **solo se** QR dinamico (B) o immagine CID (2); altrimenti **nessuna modifica Java** |
| Config (`application.yml`/env) | **opzionale**: property per l'URL APK / abilitare la sezione download |

## 6. Firma dell'APK & aggiornamenti (decisione importante)

L'APK release attuale è firmato con il **keystore di debug** bundle. Per una **distribuzione interna** con aggiornamenti nel tempo serve una **chiave di firma stabile**: se in futuro distribuisci un APK firmato con una chiave **diversa**, l'aggiornamento **non** si installa sopra il precedente (l'utente deve disinstallare/reinstallare, perdendo lo stato locale).
**Raccomandazione:** creare un **release keystore dedicato** (conservato e ri-usato per tutte le build), prima di distribuire l'app via mail. → Decisione da prendere prima del rollout (vedi §8).

## 7. Rischi e vincoli

- **LAN-only:** il telefono deve essere sulla **stessa rete/VPN** per scaricare l'APK e per raggiungere il server. Se il destinatario apre la mail **fuori LAN**, il QR (immagine remota) e il download non funzionano. → Le istruzioni devono dire "connettiti alla rete aziendale/VPN".
- **Blocco immagini nei client email:** immagini remote spesso bloccate di default (Gmail/Outlook) → il QR potrebbe non apparire subito. Mitigazione: CID inline (§4.2 Opz.2) e/o link testuale sempre presente.
- **Origini sconosciute (Android):** sideload richiede di abilitare l'installazione da origini sconosciute → istruzione esplicita.
- **Certificato HTTPS:** il telefono deve fidarsi del cert del dominio LAN (ok se cert valido via Caddy).
- **iOS:** non coperto (nessun APK/IPA distribuibile ora). Prevedere una riga "iOS: contatta l'amministratore" oppure ometterlo.
- **Versioning APK:** URL stabile + gestione della sostituzione del file ad ogni nuova release.

## 8. Decisioni approvate (definitive)

1. **Hosting APK: Caddy `/download/`** ✅ → `https://cmms.firmabratex.pl/download/atlas-cmms.apk` (MIME `application/vnd.android.package-archive`, nome file stabile).
2. **Dominio server:** `https://cmms.firmabratex.pl` → config app = `https://cmms.firmabratex.pl/api`. (Oggi LAN; in futuro il sito sarà **esposto su internet con SSL adeguato** → usare il dominio pubblico rende il design già a prova di quella evoluzione.)
3. **QR: statico pre-generato** (minime risorse sul server — generato **una volta**, nessuna libreria/CPU a runtime).
4. **Rendering: immagini INLINE (CID)** ✅ → **risolve il blocco immagini** dei client email. Da applicare **sia al QR sia al LOGO** (oggi il logo è remoto e viene bloccato). Richiede estendere il MailService per allegati inline con `Content-ID`.
5. **Firma APK:** si prevede un **release keystore** stabile, ma come **lavoro separato** successivo (non in questa modifica). Fino ad allora la distribuzione usa la firma attuale.
6. **iOS: escluso** per ora (in azienda usano tutti Android). Nessuna riga iOS nella mail; si includerà quando ci sarà un ambiente di sviluppo/test iOS.
7. **Sia invito sia benvenuto** ✅ → sezioni download+config in **`invite.html`** e **`signup.html`**.

### Design finalizzato (conseguenze)
- **Immagini inline (CID)** per logo + QR in tutte le mail interessate → niente più blocco rendering. Il MailService deve supportare inline attachments; i template referenziano `src="cid:logo"` / `src="cid:appQr"`.
- **QR statico**: PNG generato una volta codificando `https://cmms.firmabratex.pl/download/atlas-cmms.apk`, bundle come risorsa del backend e allegato inline (nessuna generazione per-email).
- **URL fissi via dominio pubblico** (`cmms.firmabratex.pl`) → validi sia in LAN sia dopo l'esposizione internet.
- **Solo Android**, keystore stabile rimandato a task separato.

## 9. Piano di implementazione (quando approvato)

1. **Infra APK**: creare cartella pubblica + route Caddy `/download/*` (MIME apk); caricare `atlas-cmms.apk`; testare il download da telefono in LAN.
2. **QR**: generare `atlas-qr.png` codificando l'URL APK; metterlo nella cartella statica `/images/` (o predisporre CID).
3. **i18n**: aggiungere le chiavi (§4.4) in `mailMessages.properties` + `_it_IT` + `_pl_PL`.
4. **Template**: aggiornare `invite.html` (+ eventuale fragment) con sezioni download/config; usare `api.host` per gli URL.
5. **(Opz.) Java**: solo se QR dinamico o CID inline.
6. **Rebuild + redeploy** immagine backend (i template/properties sono bundle nel JAR) → `docker build` → push → `pull` + `up -d api` sul server.
7. **Test**: inviare un invito reale, aprire la mail su desktop **e** su telefono, verificare QR/render, scaricare+installare l'APK, configurare il server, login.

## 10. Deployment

Le modifiche a **template e properties sono compilate nel JAR** ⇒ per andare in produzione servono **rebuild + re-push dell'immagine** `dablio96/self-hosted-cmms-backend` e `pull` + `up -d api` sul server (vedi `dev-docs/upgrade-to-self-hosted.md`). L'APK e il QR (file statici serviti da Caddy) **non** richiedono rebuild dell'immagine: si aggiornano sul server.

## 11. Validazione tecnica (MailService) — CID inline già predisposto

Verificato in [`EmailService2.java`](../api/src/main/java/com/grash/service/EmailService2.java) (impl SMTP di `MailService`):
- `sendHtmlMessage` costruisce già un messaggio **multipart** (`new MimeMessageHelper(message, true, "UTF-8")`) con `setText(htmlBody, true)`;
- esiste già una riga **commentata** `//helper.addInline("attachment.png", resourceFile);` con
  `@Value("classpath:/static/images/logo.png") Resource resourceFile;` → **lo scaffolding per il logo inline c'è già**.

**Approccio implementativo (basso rischio):**
1. In `EmailService2`: aggiungere `@Value("classpath:/static/images/atlas-qr.png") Resource qrResource;`;
   dopo `setText(...)` fare `helper.addInline("logo", resourceFile)` **sempre** e
   `helper.addInline("appQr", qrResource)` **solo** per i template che lo usano (invito/benvenuto),
   condizionando sul nome template in `sendMessageUsingThymeleafTemplate`.
   *(`addInline` va chiamato dopo `setText`, come già nell'ordine attuale.)*
2. `fragments/main-layout.html`: sostituire l'`<img>` del logo (oggi URL remoto) con **`src="cid:logo"`**.
3. `invite.html` e `signup.html`: sezioni download+config con **`<img src="cid:appQr">`**, link a
   `https://cmms.firmabratex.pl/download/atlas-cmms.apk` e passi di config (`https://cmms.firmabratex.pl/api`).
4. i18n: nuove chiavi in `mailMessages.properties` + `_it_IT` + `_pl_PL`.
5. **QR PNG**: risorsa statica `api/src/main/resources/static/images/atlas-qr.png` (codifica l'URL APK),
   generata **una volta** (nessuna generazione a runtime → coerente con "minime risorse").
6. **White-label logo**: v1 inlinea `logo.png` di default; `custom-logo.png` è un raffinamento successivo.

Nessuna modifica a `MailServiceFactory`/SendGrid per la v1 (invito/benvenuto passano dall'impl SMTP
`EmailService2`; se in futuro userete SendGrid, l'inline andrà replicato lì).

---

**Prossimo passo:** decisioni §8 **acquisite**. Unico dettaglio operativo da chiudere: **come generare
il PNG del QR** (§11.5). Su tuo via procedo con l'implementazione un pezzo alla volta (prima i18n +
template + `EmailService2`, poi il QR, infine rebuild immagine); le parti server (route Caddy
`/download/` + upload APK) restano a te.
