# Atlas CMMS — MOD-003
## Audit e progettazione integrazione LDAP / Active Directory

## Decisione

**MOD-001 APPROVATA.**  
**MOD-002 APPROVATA.**

Il report MOD-002 dimostra che tutti gli 8 limiti commerciali `UNLIMITED_*` sono
già bypassati tramite il percorso licensing centralizzato, senza modifiche ai
service applicativi. La suite è a 1430/1430 test verdi.

Il prossimo modulo è **MOD-003 — LDAP / Active Directory**.

Questa fase è **SOLO AUDIT + PROGETTAZIONE**.

**NON implementare ancora LDAP/AD.**
**NON modificare il comportamento di autenticazione.**
**NON procedere a MOD-004.**

---

# 1. Obiettivo

Determinare esattamente cosa serve per rendere disponibile in Atlas CMMS
l'integrazione LDAP/Active Directory in una installazione self-hosted.

L'obiettivo finale del progetto è permettere all'istanza self-hosted di:

1. autenticare utenti tramite Active Directory/LDAP;
2. eventualmente sincronizzare utenti;
3. eventualmente creare/aggiornare/disabilitare utenti locali in base ad AD;
4. mappare gruppi/OU AD verso ruoli Atlas, se il codice lo supporta;
5. mantenere separati autenticazione, autorizzazione e provisioning;
6. non dipendere da Keygen o da una licenza commerciale per queste funzioni.

**Non assumere che tutte le funzioni LDAP già presenti nel repository siano
bloccate dalla licenza. Verificare il codice.**

---

# 2. Fonti da analizzare

Usare il codice attuale e tutta la documentazione audit disponibile, in
particolare:

```text
docs/self-hosted-audit/04-feature-matrix.md
docs/self-hosted-audit/11-modification-plan.md
docs/self-hosted-audit/12-test-plan.md
docs/self-hosted-audit/13-mod001-implementation.md
docs/self-hosted-audit/14-mod001-verification.md
docs/self-hosted-audit/15-mod002-verification.md
```

Se esistono documenti più recenti nell'audit, considerarli prioritari.

---

# 3. Mappatura completa del codice LDAP

Individuare tutte le occorrenze di:

```text
LDAP
ldap
LDAP_ENABLED
LDAP_URL
LDAP_BASE_DN
LDAP_MANAGER_DN
LDAP_MANAGER_PASSWORD
LDAP_OBJECT_CLASS
LDAP_USER_SEARCH_BASES
LDAP_USER_SEARCH_FILTER
LDAP_ATTR_USERNAME
LDAP_ATTR_EMAIL
LDAP_ATTR_FIRSTNAME
LDAP_ATTR_LASTNAME
LDAP_ORG_ADMIN
LDAP_OU_ROLE_MAPPINGS
LDAP_SYNC_ENABLED
LDAP_SYNC_CREATE
LDAP_SYNC_UPDATE
LDAP_SYNC_DISABLE
LDAP_SYNC_CRON
ENABLE_SSO
OAUTH2
```

Ricostruire il percorso:

```text
configuration
    ↓
application.yml / environment
    ↓
Spring configuration
    ↓
LDAP client/configuration
    ↓
authentication
    ↓
user provisioning
    ↓
role mapping
    ↓
synchronization
```

Per ogni componente indicare:

- file;
- classe;
- metodo;
- funzione;
- dipendenze;
- eventuale controllo licensing;
- eventuale controllo ruolo;
- eventuale controllo `hasEntitlement()`;
- eventuale controllo `hasLicense`.

---

# 4. Individuare eventuali gate commerciali

Cercare esplicitamente pattern come:

```java
hasEntitlement(...)
hasLicense(...)
licenseService...
```

all'interno del codice LDAP/SSO.

Per ogni gate creare una tabella:

| Funzione | Gate licensing | Effetto | Self-hosted attuale | Azione necessaria |
|---|---|---|---|---|
| LDAP login | ... | ... | ... | ... |
| LDAP sync | ... | ... | ... | ... |
| LDAP user creation | ... | ... | ... | ... |
| LDAP update | ... | ... | ... | ... |
| LDAP disable | ... | ... | ... | ... |
| LDAP role mapping | ... | ... | ... | ... |
| SSO/OAuth | ... | ... | ... | ... |

**Non modificare ancora nessun gate.**

---

# 5. Distinguere LDAP da OAuth2/SSO

Non confondere:

```text
LDAP / Active Directory
```

con:

```text
OAuth2 / OIDC / SSO
```

Analizzare separatamente.

Se il repository contiene entrambi, documentare:

- cosa è già implementato;
- cosa è bloccato;
- cosa è configurabile;
- cosa dipende dal licensing;
- cosa richiederebbe sviluppo.

MOD-003 riguarda principalmente **LDAP / Active Directory**.

OAuth2/OIDC deve essere documentato come possibile modulo separato se non è
necessario per LDAP.

---

# 6. Active Directory

Verificare se l'implementazione supporta realmente AD oppure solamente LDAP
generico.

Analizzare almeno:

```text
LDAP protocol
LDAP bind
search base
user search filter
objectClass
attribute mapping
group membership
OU
TLS/LDAPS
certificate validation
```

Determinare se sono supportati:

```text
ldap://
ldaps://
StartTLS
```

e quali meccanismi di bind sono previsti.

Non inventare supporto non presente.

---

# 7. Attributi AD

Verificare il mapping attuale di:

```text
username
email
first name
last name
display name
groups
OU
```

Confrontarlo con gli attributi AD comunemente utilizzati, ma distinguere
chiaramente:

```text
supportato dal codice
```

da:

```text
possibile ma da configurare
```

da:

```text
non supportato
```

Non modificare il mapping in questa fase.

---

# 8. Sincronizzazione

Analizzare nel dettaglio:

```text
LDAP_SYNC_ENABLED
LDAP_SYNC_CREATE
LDAP_SYNC_UPDATE
LDAP_SYNC_DISABLE
LDAP_SYNC_CRON
```

Determinare:

1. quale componente esegue il sync;
2. se usa scheduler Spring;
3. come viene identificato un utente;
4. cosa succede se l'utente AD viene eliminato;
5. cosa succede se cambia email;
6. cosa succede se cambia nome;
7. cosa succede se cambia gruppo;
8. se vengono aggiornati i ruoli;
9. se un utente locale può essere sovrascritto;
10. se esiste soft-disable oppure cancellazione;
11. se il sync può creare utenti in una company errata.

---

# 9. Ruoli e autorizzazione

Questa è una parte critica.

Determinare se:

```text
AD group
```

può essere mappato a:

```text
Atlas role
```

e se esiste già:

```text
LDAP_OU_ROLE_MAPPINGS
```

capire esattamente il formato atteso.

Verificare anche:

- ruolo Organization Admin;
- ruoli Company;
- eventuali ruoli globali;
- permessi multi-company;
- rischio di privilege escalation tramite gruppo AD.

**Non modificare i ruoli durante questo audit.**

---

# 10. Sicurezza

Analizzare almeno:

### Credenziali LDAP

Verificare dove vengono conservati:

```text
LDAP_MANAGER_DN
LDAP_MANAGER_PASSWORD
```

e se finiscono in:

```text
log
exception
actuator
configuration endpoint
Docker inspect
```

### TLS

Verificare:

- validazione certificato;
- truststore;
- possibilità di disabilitare TLS verification;
- default sicuri/insicuri.

### Injection

Verificare che:

```text
username
email
search filters
DN
group
```

non permettano LDAP injection.

### Password

Verificare che le password AD:

- non vengano salvate in chiaro;
- non vengano loggate;
- non vengano copiate nel database Atlas.

---

# 11. Multi-tenancy / company isolation

È obbligatorio verificare questo punto.

Determinare come un utente LDAP viene associato a:

```text
organization
company
roles
```

e cosa accade quando lo stesso utente AD accede a più company.

Verificare che LDAP non possa bypassare i normali controlli di:

```text
organization
company
role
permission
```

---

# 12. Configurazione Docker

Verificare:

```text
docker-compose.yml
.env.example
application.yml
```

e stabilire quali variabili sono realmente necessarie per una configurazione
AD.

Produrre una tabella:

| Variable | Required | Secret | Default | Description |
|---|---|---|---|---|
| LDAP_ENABLED | ... | no | ... | ... |
| LDAP_URL | ... | no | ... | ... |
| LDAP_BASE_DN | ... | no | ... | ... |
| LDAP_MANAGER_DN | ... | no | ... | ... |
| LDAP_MANAGER_PASSWORD | ... | yes | ... | ... |
| ... | ... | ... | ... | ... |

Non inserire credenziali reali.

---

# 13. Test

Individuare i test LDAP già presenti.

Riportare:

```text
test class
test name
coverage
```

Determinare cosa manca.

Progettare almeno:

1. successful LDAP authentication;
2. invalid credentials;
3. unknown user;
4. attribute mapping;
5. user creation;
6. user update;
7. disable;
8. role/group mapping;
9. LDAP unavailable;
10. TLS failure;
11. LDAP injection;
12. multi-company isolation.

**Non implementare i nuovi test in questa fase**, salvo che siano necessari
per comprendere un comportamento già esistente e non modifichino il codice
applicativo.

---

# 14. Dipendenze esterne

Identificare le librerie LDAP utilizzate:

```text
pom.xml
Gradle
Spring LDAP
UnboundID
Apache Directory
JNDI
```

indicare versione e utilizzo.

Verificare eventuali vulnerabilità note solo se il progetto dispone già di
strumenti locali per farlo; non introdurre nuove dipendenze.

---

# 15. Decisione architetturale

Alla fine dell'audit classificare ogni funzionalità:

```text
A = già funzionante self-hosted
B = implementata ma bloccata da licensing
C = parzialmente implementata
D = non implementata
E = implementata ma insicura/da correggere
```

Creare una tabella finale.

---

# 16. NON fare

Durante MOD-003 NON:

- modificare il licensing;
- modificare `LicenseService`;
- modificare `LicensingState`;
- modificare `hasEntitlement()`;
- abilitare LDAP automaticamente;
- inserire credenziali AD;
- cambiare ruoli;
- modificare il database;
- introdurre workaround;
- fare commit;
- fare push;
- procedere con MOD-004.

Se viene individuato un gate licensing che impedisce una funzione LDAP
necessaria, **documentarlo soltanto**.

---

# 17. Output obbligatorio

Creare:

```text
docs/self-hosted-audit/16-mod003-ldap-ad-audit.md
```

Struttura minima:

```markdown
# 16 — MOD-003 LDAP / Active Directory Audit

## Stato
PASS / PARTIAL / BLOCKED / FAIL

## Executive summary

## LDAP architecture

## LDAP configuration

## Authentication flow

## User provisioning

## Synchronization

## AD group / role mapping

## OAuth2 / SSO separation

## Licensing gates

## Security audit

## Multi-tenancy audit

## Docker configuration

## Existing tests

## Missing tests

## Dependency analysis

## Feature classification

## Required modifications

## Risks

## Recommendation
```

---

# 18. Git

Alla fine eseguire:

```bash
git status
git diff
```

e riportare il risultato.

**Non fare commit e non fare push.**

Se non sono state apportate modifiche:

```text
Code changes: none
```

---

# 19. Decision gate

Dopo la produzione di:

```text
16-mod003-ldap-ad-audit.md
```

**FERMARSI.**

Il responsabile tecnico analizzerà il report e deciderà:

- se LDAP è già completamente utilizzabile;
- se serve una modifica minimale;
- se serve una vera implementazione;
- se LDAP deve essere suddiviso in più moduli;
- quali test devono essere implementati;
- se procedere successivamente con MOD-004.

Non iniziare nessun altro modulo autonomamente.
