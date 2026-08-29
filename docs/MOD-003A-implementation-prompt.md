# Atlas CMMS — MOD-003A
## LDAP/Active Directory — hardening, configurazione e test

## Decisione tecnica

Il report `16-mod003-ldap-ad-audit.md` è stato analizzato.

Conclusione: LDAP/AD è già implementato, il licensing non è più un blocco grazie a MOD-001 e il caso base AD richiede soprattutto configurazione, hardening limitato e test. Questa attività **non deve riscrivere LDAP** e **non deve modificare il licensing**.

## Obiettivi

1. Completare la documentazione LDAP in `.env.example`.
2. Documentare una configurazione AD/LDAPS di riferimento con valori fittizi.
3. Sostituire `printStackTrace()` nel percorso LDAP con logging strutturato, senza esporre secret.
4. Aggiungere test del flusso LDAP reale, preferibilmente usando l'infrastruttura Testcontainers già presente.
5. Verificare provisioning JIT, attribute mapping, OU→role, sync, isolamento company, error handling e LDAP injection.
6. Verificare che MOD-001 e il licensing restino invariati.

## NON modificare

Non modificare:

- `LicenseService`, `LicensingState`, `hasEntitlement()` o `LicenseEntitlement`;
- `Consts.usageBasedFreeLimits`;
- JWT/authentication/multi-tenancy;
- algoritmo OU→role mapping;
- supporto `memberOf`;
- StartTLS;
- custom truststore/certificate pinning;
- `MULTI_INSTANCE`.

## 1. `.env.example`

Aggiungere tutte le variabili LDAP realmente utilizzate dal progetto, senza secret reali:

```env
LDAP_ENABLED=false
LDAP_URL=
LDAP_BASE_DN=
LDAP_ORG_ADMIN=
LDAP_MANAGER_DN=
LDAP_MANAGER_PASSWORD=
LDAP_USER_SEARCH_BASES=
LDAP_USER_SEARCH_FILTER=
LDAP_SEARCH_SUBTREE=
LDAP_ATTR_USERNAME=
LDAP_ATTR_EMAIL=
LDAP_ATTR_FIRSTNAME=
LDAP_ATTR_LASTNAME=
LDAP_OBJECT_CLASS=
LDAP_OU_ROLE_MAPPINGS=
LDAP_SYNC_ENABLED=false
LDAP_SYNC_CREATE=false
LDAP_SYNC_UPDATE=false
LDAP_SYNC_DISABLE=false
LDAP_SYNC_CRON=
```

Verificare nel codice ogni variabile prima di aggiungerla.

## 2. Configurazione AD

Documentare una configurazione di riferimento, ad esempio:

```env
LDAP_ENABLED=true
LDAP_URL=ldaps://dc01.example.local:636
LDAP_BASE_DN=DC=example,DC=local
LDAP_ORG_ADMIN=atlas-admin@example.local
LDAP_MANAGER_DN=CN=svc-atlas,OU=Service Accounts,DC=example,DC=local
LDAP_MANAGER_PASSWORD=<SECRET>
LDAP_USER_SEARCH_BASES=OU=Users,DC=example,DC=local
LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
LDAP_SEARCH_SUBTREE=true
LDAP_ATTR_USERNAME=sAMAccountName
LDAP_ATTR_EMAIL=mail
LDAP_ATTR_FIRSTNAME=givenName
LDAP_ATTR_LASTNAME=sn
LDAP_OBJECT_CLASS=user
LDAP_OU_ROLE_MAPPINGS=
```

Spiegare secret, certificato LDAPS/truststore JVM, raggiungibilità dal container API e associazione alla company dell'`LDAP_ORG_ADMIN`.

## 3. Sync defaults

Il report rileva:

- `application.yml`: enabled/create/update=true, disable=false;
- `docker-compose.yml`: tutti false.

**Non cambiare automaticamente i default Docker.** Mantenerli disabilitati per un deployment generico e documentare che l'amministratore deve abilitarli esplicitamente. Se esiste un'ambiguità reale nella configurazione risolta, documentarla e fermarsi prima di alterare la semantica.

## 4. Logging

Individuare `printStackTrace()` nel percorso LDAP e sostituirlo con il logger già usato dal progetto.

Requisiti:

- nessuna password o credenziale nei log;
- livello appropriato;
- comportamento funzionale invariato;
- nessun refactoring generale del logging.

## 5. Test obbligatori

Aggiungere test per:

1. login LDAP riuscito;
2. credenziali errate → errore controllato;
3. utente sconosciuto;
4. mapping `sAMAccountName`, `mail`, `givenName`, `sn`;
5. provisioning JIT;
6. OU→role: match, no-match/default, primo match, case-insensitive;
7. isolamento: utente sempre nella company dell'org-admin;
8. sync create/update/disable;
9. cambio OU: ruolo aggiornato al login ma non dal sync, mantenendo il comportamento attuale;
10. LDAP non raggiungibile;
11. certificato LDAPS non fidato;
12. LDAP injection con `*()\`;
13. collisione email locale/LDAP;
14. licensing regression: self-hosted concede `SSO`, commercial senza entitlement mantiene il comportamento precedente.

Preferire Testcontainers se già disponibile. Non aggiungere dipendenze inutili.

## 6. Test password

Verificare che la password AD:

- sia usata solo per il bind;
- non venga salvata;
- non venga loggata;
- non venga copiata nel DB.

La password locale del provisioning deve restare random/hashata come nel codice attuale.

## 7. Licensing

Non modificare alcun gate. Verificare soltanto:

```text
self-hosted + SSO → LDAP context disponibile
commercial + SSO assente → comportamento originale
```

## 8. OU role mapping

Non modificarne l'algoritmo. Non implementare `memberOf`, ruoli custom o allowlist in questa fase.

Documentare però il rischio di privilege escalation derivante da una configurazione errata di `LDAP_OU_ROLE_MAPPINGS`, soprattutto per ruoli amministrativi.

## 9. TLS

Non implementare StartTLS, custom truststore o certificate pinning.

Documentare soltanto:

```text
LDAPS → certificato server → truststore JVM → container API
```

## 10. Build e regressione

Eseguire:

```bash
cd api
./mvnw test
./mvnw -DskipTests package
```

Baseline precedente:

```text
1430 test
0 failure
0 error
0 skipped
```

Registrare il nuovo risultato e spiegare l'eventuale aumento dovuto ai test LDAP.

Verificare anche:

```bash
docker compose config
git status --short
git diff --stat
git diff
```

## 11. Git

Non fare commit e non fare push.

Non modificare file non necessari.

## 12. Output obbligatorio

Creare:

```text
docs/self-hosted-audit/17-mod003a-implementation.md
```

con:

```markdown
# 17 — MOD-003A Implementation
## Stato
## Changes implemented
## .env.example
## AD configuration documentation
## Sync defaults
## LDAP logging hardening
## Tests added
## Licensing regression
## Authentication tests
## Provisioning tests
## Synchronization tests
## Security tests
## Build result
## Full test suite
## Docker verification
## Remaining risks
## Out of scope
## Git state
## Recommendation
```

## Criterio di successo

PASS solo se LDAP continua a funzionare come prima, non sono stati introdotti gate/bypass licensing, non ci sono secret nel repository, il logging è hardenizzato, i test principali passano, build e suite completa passano e multi-tenancy/ruoli restano invariati.

## Decision gate

Al termine fermarsi. Non procedere con MOD-004, MOD-005 o altri moduli e non implementare `memberOf`, StartTLS, custom roles, custom truststore o multi-company LDAP.

Il responsabile tecnico analizzerà `17-mod003a-implementation.md` e deciderà il passo successivo.
