# MOD-012 — Mobile Runtime Acceptance Test

## 1. Contesto

MOD-011 è concluso con **PASS**.

Stato consolidato:

- F-01: RESOLVED;
- Asset PATCH senza `status`: funzionante;
- suite backend: 1446/1446;
- security regression: PASS;
- licensing: invariato;
- authorization: invariata;
- multi-tenancy: invariata;
- frontend web: invariato;
- mobile source: invariato.

È stato documentato F-04 relativo alla semantica delle PATCH con campi `@NotNull` omessi e mapper MapStruct `SET_NULL`.

**F-04 NON deve essere modificato durante questo MOD**, salvo che il test mobile dimostri concretamente che l'app ufficiale genera questo tipo di PATCH e che ciò costituisca un problema reale di compatibilità.

L'obiettivo di MOD-012 è verificare **a runtime l'app mobile ufficiale Android/iOS contro il nostro Atlas self-hosted locale**.

## 2. Obiettivo

Dimostrare con un test reale che l'app mobile ufficiale può:

1. configurare il nostro server self-hosted;
2. raggiungere il backend;
3. autenticarsi;
4. recuperare i dati;
5. leggere Asset;
6. leggere/creare/modificare Work Order;
7. utilizzare allegati;
8. utilizzare QR/barcode, se disponibile;
9. gestire la sessione;
10. eventualmente operare offline;
11. sincronizzare dati, se supportato;
12. funzionare senza dipendere dal licensing Cloud.

Il test deve essere eseguito **solo in ambiente locale**.

## 3. Principio fondamentale

Questo MOD è un **acceptance test runtime**, non un'attività di sviluppo.

NON modificare:

- mobile source;
- frontend;
- backend;
- licensing;
- API;
- database schema;
- Docker architecture;
- Caddy;
- DNS;
- production.

Se un test fallisce:

```text
FAIL
↓
raccogli evidence
↓
determina causa
↓
classifica
↓
STOP se richiede modifica
```

Non correggere automaticamente il problema.

## 4. Fonti

Leggere prima:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/27-mod009-mobile-compatibility-audit.md`;
3. `docs/self-hosted-audit/28-mod010-local-acceptance-test.md`;
4. `docs/self-hosted-audit/29-mod011-f01-fix-verification.md`.

Non rileggere l'intero repository.

## 5. Ambiente locale

Identificare il sistema locale utilizzato per il test.

Registrare:

```text
OS:
Device/emulator:
Android version:
iOS version:
Atlas commit/version:
Backend endpoint:
Frontend endpoint:
```

NON utilizzare `websrv01`.

NON utilizzare database production.

NON utilizzare MinIO production.

## 6. Requisito fondamentale: raggiungibilità

Prima di configurare l'app verificare che il dispositivo/emulatore possa raggiungere il computer che ospita Atlas.

Ricordare:

```text
localhost
```

sul telefono/emulatore NON equivale automaticamente al computer host.

Determinare l'IP LAN corretto dell'host.

Verificare:

```text
DEVICE
   ↓
HOST LAN IP
   ↓
Atlas frontend/backend
```

Se sono necessarie regole firewall locali, documentarle.

Non modificare firewall aziendali o production.

## 7. Server URL

Utilizzare la funzionalità già identificata nel MOD-009:

```text
Login
→ Custom Server
```

Configurare l'endpoint corretto per l'ambiente locale.

Non inventare il formato dell'URL.

Usare esattamente il formato supportato dal codice mobile e dalla configurazione API.

Documentare:

```text
URL configured:
Expected:
Actual:
```

Non riportare eventuali token o credenziali nel report.

## 8. Connessione iniziale

Verificare:

1. app avviabile;
2. Custom Server configurabile;
3. connessione al backend;
4. risposta API;
5. nessun errore bloccante.

Se l'app non raggiunge il server, determinare se la causa è:

```text
NETWORK
TLS
URL
PORT
CORS
API
AUTH
APP CONFIGURATION
```

Non modificare il codice.

## 9. Authentication

Eseguire:

### Test A
Login con utente valido.

Expected:
```text
login success
dashboard/home available
```

### Test B
Logout.

Expected:
```text
session terminated
```

### Test C
Login nuovamente.

Expected:
```text
login success
```

### Test D
Credenziali errate.

Expected:
```text
authentication rejected
```

Non utilizzare account production.

## 10. Organization / Company

Se l'app supporta company/organization context:

verificare che l'utente veda solo i dati della company corretta.

Se possibile:

```text
Company A
Company B
User A
User B
```

Verificare:

```text
User A → Company A data
User B → Company B data
```

e che non sia possibile accedere ai dati dell'altra company.

Non eseguire penetration testing aggressivo.

## 11. Asset

Testare:

1. lista Asset;
2. apertura Asset;
3. ricerca;
4. visualizzazione dettagli;
5. modifica di un Asset, se supportata;
6. eventuale creazione Asset, se supportata.

Prestare attenzione alle PATCH.

Se l'app esegue una PATCH parziale, catturare la request solo se possibile con strumenti già disponibili.

Verificare se invia:

```json
{
  "name": "...",
  "status": "..."
}
```

oppure omette campi.

Questo è particolarmente importante per F-04.

**Non correggere F-04 durante questo test.**

Se l'app invia PATCH parziali che omettono `name` e il backend produce 500:

```text
F-04 MOBILE IMPACT = CONFIRMED
```

e fermarsi dalla modifica.

## 12. Work Orders

Verificare:

1. lista;
2. dettaglio;
3. ricerca;
4. creazione, se disponibile;
5. modifica;
6. cambio stato;
7. assegnazione, se disponibile.

Registrare eventuali:

- 401;
- 403;
- 404;
- 409;
- 422;
- 500.

Non correggere.

## 13. Attachments

Verificare:

```text
Asset/Work Order
    ↓
Upload image
    ↓
Storage
    ↓
View
    ↓
Download
```

Testare almeno:

- immagine;
- PDF o altro file supportato.

Verificare:

- upload;
- visualizzazione;
- download;
- eventuale delete.

Confrontare il comportamento con quello già verificato nel MOD-010.

## 14. QR / Barcode

Se il dispositivo dispone della fotocamera e l'app supporta QR/barcode:

1. aprire scanner;
2. scansionare un codice reale o di test;
3. verificare riconoscimento;
4. verificare associazione all'Asset, se supportata.

Se non è possibile testare per limiti hardware:

```text
NOT TESTED
```

Non modificare l'app.

## 15. Offline

Verificare innanzitutto se l'app presenta funzionalità offline.

Test:

1. caricare dati online;
2. disabilitare temporaneamente la rete del dispositivo;
3. riaprire dati già sincronizzati;
4. verificare cosa rimane disponibile.

NON assumere che l'app supporti write-sync.

Se esiste un'operazione offline documentata:

```text
create/update offline
↓
restore network
↓
sync
↓
verify backend
```

Se il comportamento non è chiaramente supportato:

```text
NOT VERIFIED
```

Non implementare nulla.

## 16. Push Notifications / FCM

Verificare solo se il progetto locale dispone già della configurazione necessaria.

Se non esistono:

- Firebase credentials;
- push configuration;
- device registration;

non creare una nuova infrastruttura.

Classificare:

```text
NOT TESTED — environment prerequisite missing
```

Questo non è automaticamente un blocker.

## 17. Licensing Mobile

Durante il test verificare che:

- login funzioni;
- feature core siano accessibili;
- non compaiano messaggi di upgrade;
- non venga richiesta una subscription;
- non venga richiesto Keygen;
- non venga richiesto Atlas Cloud.

Se compare un blocco commerciale:

```text
documentare schermata/errore
```

Non modificare il client.

## 18. API Evidence

Se possibile utilizzare strumenti già presenti nell'ambiente per identificare:

```text
request
response
HTTP status
endpoint
```

Non catturare o pubblicare:

- password;
- JWT;
- refresh token;
- cookie;
- API key.

Nel report utilizzare:

```text
Authorization: REDACTED
```

## 19. Runtime Matrix

Creare una tabella:

| Test | Android | iOS | Result |
|---|---|---|---|
| Custom Server | | | |
| Connection | | | |
| Login | | | |
| Logout | | | |
| Assets | | | |
| Work Orders | | | |
| Attachments | | | |
| QR/Barcode | | | |
| Offline read | | | |
| Offline write-sync | | | |
| Push | | | |
| Licensing | | | |

Usare:

```text
PASS
FAIL
NOT TESTED
NOT SUPPORTED
BLOCKED
```

## 20. F-04 Verification

F-04 è ora un punto specifico di osservazione.

Non modificarlo.

Determinare:

### Caso A
L'app invia sempre DTO completi.

```text
F-04 mobile impact: NONE OBSERVED
```

### Caso B
L'app invia PATCH parziali ma non omette campi `@NotNull`.

```text
F-04 mobile impact: NONE OBSERVED
```

### Caso C
L'app omette `name` o altri campi obbligatori.

```text
F-04 mobile impact: CONFIRMED
```

In Caso C:

- documentare request;
- documentare response;
- non correggere;
- proporre successivamente MOD separato.

## 21. Classification

Classificare eventuali problemi:

### P0
Sistema inutilizzabile / rischio grave.

### P1
Funzione core mobile inutilizzabile.

### P2
Funzione importante degradata.

### P3
Problema minore.

### INFO
Comportamento o limite non bloccante.

## 22. Production Readiness

MOD-012 non decide ancora il deployment production.

Deve però stabilire:

```text
MOBILE STATUS:
PASS
PASS WITH FINDINGS
FAIL
```

e:

```text
F-04:
NO MOBILE IMPACT
CONFIRMED
NOT VERIFIED
```

## 23. Documentation

Produrre:

```text
docs/self-hosted-audit/30-mod012-mobile-runtime-acceptance.md
```

Struttura:

```text
# MOD-012 — Mobile Runtime Acceptance Test

## 1. Objective
## 2. Environment
## 3. Version Tested
## 4. Server Configuration
## 5. Connectivity
## 6. Authentication
## 7. Organization / Company
## 8. Assets
## 9. Work Orders
## 10. Attachments
## 11. QR / Barcode
## 12. Offline
## 13. Push Notifications
## 14. Licensing
## 15. API Evidence
## 16. F-04 Verification
## 17. Android Results
## 18. iOS Results
## 19. Findings
## 20. Remaining Issues
## 21. CLAUDE.md Update
## 22. Final Verdict
```

Ogni test importante:

```text
TEST:
EXPECTED:
ACTUAL:
RESULT:
EVIDENCE:
```

## 24. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

- Current Project State;
- MOD-012;
- Mobile runtime status;
- eventuale F-04 mobile impact;
- Known Issues;
- Documentation Map.

Non lasciare informazioni obsolete.

## 25. Git

Consentito:

```bash
git status
git diff
git diff --check
git log
```

Non eseguire:

```bash
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

## 26. Secret Handling

Non inserire nel report:

- password;
- JWT;
- refresh token;
- API keys;
- OAuth secrets;
- Firebase secrets;
- database credentials.

Mascherare sempre:

```text
********
REDACTED
```

## 27. Definition of Done

MOD-012 è completato quando:

- Android testato oppure motivatamente classificato NOT TESTED;
- iOS testato oppure motivatamente classificato NOT TESTED;
- Custom Server verificato;
- connessione verificata;
- login verificato;
- company isolation verificata se applicabile;
- Assets verificati;
- Work Orders verificati;
- Attachments verificati;
- QR/barcode verificato o classificato;
- offline classificato;
- push classificato;
- licensing verificato;
- F-04 mobile impact determinato;
- test matrix completata;
- report prodotto;
- `CLAUDE.md` aggiornato;
- verdict finale espresso.

## 28. STOP CONDITION

Al termine:

**STOP.**

Non:

- modificare il mobile;
- modificare frontend;
- modificare backend;
- correggere F-04;
- modificare licensing;
- modificare Docker;
- modificare production;
- fare deployment live;
- configurare Caddy;
- modificare DNS;
- iniziare MOD-013.

La decisione sul deployment verrà presa dopo la revisione del report MOD-012.

## 29. Final Output

Output obbligatorio:

```text
CLAUDE.md updated: YES/NO
Code changes: NONE
Android: PASS / FAIL / NOT TESTED
iOS: PASS / FAIL / NOT TESTED
Mobile licensing: PASS / FINDINGS
F-04 mobile impact: NONE / CONFIRMED / NOT VERIFIED
Attachments: PASS / FAIL
Offline: PASS / PARTIAL / NOT TESTED
Push: PASS / NOT TESTED
P0: X
P1: X
P2: X
P3: X
Mobile status: PASS / PASS WITH FINDINGS / FAIL
Final verdict: PASS / PASS WITH FINDINGS / FAIL
```
