# 19 — MOD-004B — Sicurezza e lifecycle degli allegati

## Obiettivo

Implementare in modo mirato le correzioni emerse dall'audit MOD-004 sugli allegati Atlas.

Questo incarico è successivo all'audit `18-mod004-attachment-audit.md`, che ha confermato che il flusso base degli allegati è già funzionante end-to-end e non richiede modifiche architetturali.

Riferimento principale:
- `docs/self-hosted-audit/18-mod004-attachment-audit.md`

## Scope

In scope esclusivamente:

1. mitigazione del rischio stored-XSS derivante da `Content-Type` client-controlled e dalla pubblicazione degli allegati tramite `/storage`;
2. cancellazione del relativo oggetto MinIO quando viene eliminato il record `File`;
3. test automatici mirati per verificare le correzioni.

Out of scope:

- licensing;
- MOD-005 o altri moduli;
- redesign dell'architettura storage;
- introduzione di GCP/filesystem;
- modifiche al modello multi-tenant;
- modifiche al sistema di autenticazione;
- modifiche Docker di produzione non strettamente necessarie;
- refactoring non necessario.

## Regola fondamentale

Prima di modificare il codice:

1. leggere `CLAUDE.md`;
2. leggere integralmente `18-mod004-attachment-audit.md`;
3. individuare nel codice le implementazioni effettive di:
   - `FileController`;
   - `FileService`;
   - `MinioService`;
   - `StorageService`;
   - `FileMapper`;
   - configurazione nginx relativa a `/storage`;
   - eventuali helper per filename/content type;
4. verificare che il comportamento descritto nell'audit corrisponda al codice attuale.

Non assumere che i riferimenti di riga dell'audit siano ancora invariati.

## 1. Mitigazione stored-XSS

Analizzare il percorso:

    MultipartFile
      -> upload
      -> contentType
      -> MinIO
      -> presigned URL
      -> nginx /storage
      -> browser

Determinare la mitigazione più sicura e meno invasiva compatibile con l'architettura esistente.

Preferire, se tecnicamente compatibile con il comportamento applicativo già adottato:

- evitare che contenuti HTML/SVG/arbitrary active content vengano interpretati dal browser come documenti eseguibili;
- utilizzare `Content-Disposition: attachment` quando appropriato;
- utilizzare `X-Content-Type-Options: nosniff`;
- introdurre una allowlist MIME/estensioni solo se necessaria e senza rompere i casi d'uso documentati.

Non introdurre una allowlist arbitraria senza prima verificare quali tipi di file Atlas supporta realmente.

La soluzione deve preservare:
- immagini/preview dove richieste;
- download degli allegati;
- presigned URL;
- isolamento tenant;
- compatibilità con MinIO.

Documentare esplicitamente la scelta effettuata e perché.

## 2. Lifecycle MinIO

L'audit ha rilevato che `FileService.delete()` elimina il record PostgreSQL ma non l'oggetto MinIO.

Implementare il lifecycle corretto:

    delete File
        -> autorizzazione già esistente
        -> eliminazione oggetto storage
        -> eliminazione metadati DB

Analizzare attentamente l'ordine delle operazioni e la gestione degli errori.

Requisiti:

- non permettere che un utente non autorizzato elimini un file;
- non cancellare oggetti appartenenti a un altro record;
- utilizzare l'`object key` memorizzato in `File.path`;
- gestire in modo esplicito il caso di oggetto già assente;
- non introdurre cancellazioni massive o cleanup non richiesti;
- considerare `thumbnailPath` quando presente;
- verificare le implicazioni per eventuali associazioni esistenti.

Prima dell'implementazione verificare se esistono già operazioni di cancellazione storage in altri servizi che definiscano una convenzione da riutilizzare.

## 3. Test

Aggiungere test mirati, senza costruire una suite sproporzionata.

Devono essere coperti almeno:

### Security

- un file HTML/SVG non deve poter essere servito con comportamento che consenta stored-XSS;
- gli header di sicurezza scelti sono presenti nel percorso effettivamente utilizzato;
- il comportamento normale di download degli allegati rimane funzionante.

### Lifecycle

- creazione di un `File` + oggetto MinIO;
- cancellazione autorizzata del `File`;
- verifica che l'oggetto MinIO venga eliminato;
- gestione dell'oggetto già assente;
- verifica che una cancellazione non autorizzata non elimini né metadati né oggetto.

### Regression

Verificare che continuino a funzionare:

- upload;
- generazione presigned URL;
- listing/search;
- preview/thumbnail dove applicabile;
- company isolation;
- entitlement e permission gate esistenti.

Usare le infrastrutture di test già presenti nel repository. Non introdurre una nuova tecnologia di test se non necessaria.

## 4. Verifica multi-tenant

Non modificare `CompanyAudit.@PostLoad` né il modello di isolamento.

Aggiungere o aggiornare test solo se necessari per dimostrare che la nuova logica di delete non aggira l'autorizzazione esistente.

Il controllo deve continuare a essere:

    user
      -> authorization
      -> company isolation
      -> File
      -> storage object

## 5. Verifica finale

Eseguire:

1. test specifici del modulo;
2. test dell'area backend interessata;
3. test dell'intero backend se il tempo di esecuzione è ragionevole;
4. eventuali lint/type checks già previsti dal progetto.

Riportare:
- comando;
- risultato;
- numero test;
- eventuali test non eseguiti e motivo.

## 6. Documentazione

Creare:

`docs/self-hosted-audit/19-mod004b-security-lifecycle-implementation.md`

Il documento deve contenere:

- problema originale;
- codice coinvolto;
- soluzione adottata;
- file modificati;
- comportamento precedente;
- comportamento nuovo;
- decisioni tecniche;
- gestione degli errori;
- test aggiunti;
- risultati dei test;
- eventuali limitazioni residue;
- eventuali finding ancora aperti.

Se esiste una documentazione di verification separata prevista dal workflow del progetto, aggiornarla solo secondo le convenzioni già presenti.

## 7. Git

Durante questo incarico:

- non eseguire operazioni Git distruttive;
- non fare `reset --hard`;
- non riscrivere la storia;
- non fare push verso repository remoti;
- non modificare commit precedenti;
- lasciare chiaramente identificabili le modifiche prodotte da MOD-004B.

## 8. Criteri di completamento

MOD-004B può essere considerato completato solo se:

- [ ] il finding stored-XSS è mitigato con una soluzione coerente con l'architettura;
- [ ] il browser non può interpretare arbitrariamente un allegato active-content come pagina same-origin;
- [ ] la cancellazione del record `File` gestisce correttamente il relativo oggetto MinIO;
- [ ] thumbnail/object lifecycle è stato verificato;
- [ ] gli errori storage sono gestiti senza lasciare uno stato incoerente non documentato;
- [ ] i test specifici sono presenti e verdi;
- [ ] i test di regressione rilevanti sono verdi;
- [ ] nessuna modifica al licensing è stata introdotta;
- [ ] nessuna modifica al multi-tenancy è stata introdotta;
- [ ] la documentazione implementation è stata prodotta;
- [ ] eventuali problemi residui sono documentati.

## Anti-hallucination

NON inventare:

- MIME type supportati;
- estensioni consentite;
- comportamento di nginx;
- API storage;
- transazioni;
- modalità di gestione errori;
- requisiti di business.

Se un dettaglio non è determinabile:

1. cercarlo nella documentazione;
2. verificare il codice;
3. verificare i test;
4. se ancora ambiguo, fermarsi e documentare l'incertezza.

## STOP CONDITION

Al termine di MOD-004B:

**NON procedere automaticamente a MOD-005.**

Produrre la documentazione richiesta e fornire un riepilogo finale con:

- modifiche effettuate;
- test eseguiti;
- risultati;
- finding risolti;
- finding ancora aperti;
- eventuali decisioni che richiedono approvazione.

`Code changes: expected, limited to MOD-004B scope.`
