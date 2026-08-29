# 17 — MOD-003A Implementation

LDAP/Active Directory — hardening del logging, documentazione di configurazione e
test del flusso reale. Nessuna modifica al licensing, all'algoritmo OU→role o al
comportamento di autenticazione.

Ambiente: Windows 11, JDK 17 Temurin 17.0.20.1 (portable), Maven 3.8.6 (wrapper),
Docker 29.4.3. Data: 2026-08-26. Nessun commit/push.

---

## Stato

**PASS**

Scope completato: `.env.example` completato, config AD documentata,
`printStackTrace()` sostituito con logging strutturato, aggiunti 9 test del flusso
LDAP reale. Build e suite completa verdi (1439 test). Licensing, OU-mapping,
`memberOf`, StartTLS, truststore e multi-tenancy **invariati**.

---

## Changes implemented

| # | Change | File | Tipo |
|---|---|---|---|
| 1 | Set completo variabili `LDAP_*` (placeholder, no secret) | [`.env.example`](../../.env.example) | config/doc |
| 2 | `printStackTrace()` → `log.debug(...)` (+ `@Slf4j`) | [`configuration/LdapSecurityConfig.java`](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java) | hardening codice |
| 3 | Test del flusso LDAP reale (9) | [`service/LdapServiceTest.java`](../../api/src/test/java/com/grash/service/LdapServiceTest.java) | test (nuovo) |

Diff applicativo netto MOD-003A: `.env.example` (+27/-1),
`LdapSecurityConfig.java` (+8/-3), nuovo file di test. **Nessun'altra modifica**
al codice applicativo.

---

## .env.example

Aggiunte tutte le variabili LDAP effettivamente lette dal codice (verificate
nell'audit MOD-003), come placeholder vuoti, con commenti:

```env
LDAP_ENABLED=false
LDAP_URL=
LDAP_BASE_DN=
LDAP_ORG_ADMIN=
LDAP_MANAGER_DN=
LDAP_MANAGER_PASSWORD=          # secret: mai un valore reale
LDAP_USER_SEARCH_BASES=
LDAP_USER_SEARCH_FILTER=
LDAP_SEARCH_SUBTREE=true
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

Nessun secret reale inserito (`LDAP_MANAGER_PASSWORD` resta vuoto).

---

## AD configuration documentation

Configurazione di riferimento per Active Directory (valori fittizi). Da
impostare via env / secret manager, **non** committare la password reale:

```env
LDAP_ENABLED=true
LDAP_URL=ldaps://dc01.example.local:636
LDAP_BASE_DN=DC=example,DC=local
LDAP_ORG_ADMIN=atlas-admin@example.local
LDAP_MANAGER_DN=CN=svc-atlas,OU=Service Accounts,DC=example,DC=local
LDAP_MANAGER_PASSWORD=<via-secret-manager>
LDAP_USER_SEARCH_BASES=OU=Users,DC=example,DC=local
LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
LDAP_SEARCH_SUBTREE=true
LDAP_ATTR_USERNAME=sAMAccountName
LDAP_ATTR_EMAIL=mail
LDAP_ATTR_FIRSTNAME=givenName
LDAP_ATTR_LASTNAME=sn
LDAP_OBJECT_CLASS=user
```

Note operative:
- **Secret**: `LDAP_MANAGER_PASSWORD` è la password del service account; passarla
  via secret manager / Docker secrets. È visibile via `docker inspect` se passata
  come env in chiaro.
- **LDAPS / certificato**: la validazione TLS usa il **truststore JVM** del
  container API. Un certificato AD self-signed va importato nel truststore
  (`LDAPS → certificato server → truststore JVM → container API`). La validazione
  del certificato **non** è disattivabile (default sicuro). StartTLS non è
  implementato.
- **Raggiungibilità**: il container `api` deve poter raggiungere `LDAP_URL`.
- **Company**: tutti gli utenti LDAP sono associati alla company posseduta da
  `LDAP_ORG_ADMIN` (integrazione mono-company).

Il prefisso richiesto è solo configurazione: nessuna modifica al codice per il
caso base AD.

---

## Sync defaults

Situazione (invariata, come da vincolo del brief):

- `application.yml`: `sync.enabled/create/update=true`, `sync.disable=false`;
- `docker-compose.yml`: `LDAP_SYNC_ENABLED/CREATE/UPDATE/DISABLE=false`.

**Non modificati automaticamente.** Per un deployment Docker generico il sync
resta **disabilitato di default**: l'amministratore deve abilitarlo
esplicitamente (`LDAP_SYNC_ENABLED=true`, ecc.). Il default disabilitato è la
scelta sicura per un'immagine generica e viene lasciato così di proposito.

> Nota (non risolta, fuori scope): alcune variabili nel compose
> (`LDAP_ATTR_EMAIL/FIRSTNAME/LASTNAME/USERNAME`, `LDAP_SYNC_CRON`) usano
> `${VAR}` senza default `:-`, quindi `docker compose config` avvisa e passa
> stringa vuota se non impostate. Con LDAP abilitato l'operatore deve valorizzare
> questi attributi (ora documentati in `.env.example`). Non si è alterata la
> semantica del compose.

---

## LDAP logging hardening

`LdapSecurityConfig.ldapAuthenticator` (tentativo bind multi-base):

```diff
-                    } catch (Exception ignored) {
-                        ignored.printStackTrace();
+                    } catch (Exception ex) {
+                        // Expected when the user is not present in this search base; try the next one.
+                        // Log the reason (no credentials) at debug level instead of printing the stack trace.
+                        log.debug("LDAP bind attempt failed for one search base: {}", ex.getMessage());
```

- Aggiunto `@Slf4j` (pattern già usato nel progetto).
- Livello **debug** (fallimento atteso quando si prova la base successiva).
- Viene loggato **solo** `ex.getMessage()`: nessuna password/credenziale, nessuno
  stack trace su stderr.
- Comportamento funzionale invariato (si continua a provare le basi successive e a
  lanciare `BadCredentialsException` se nessuna autentica).

Nessun altro `printStackTrace()` residuo nel percorso LDAP
(`LdapService`, `LdapSyncJob*`).

---

## Tests added

File: [`api/.../service/LdapServiceTest.java`](../../api/src/test/java/com/grash/service/LdapServiceTest.java)
— **9 test**, JUnit 5 + Mockito. `LdapTemplate` e `LdapAuthenticationProvider`
sono mockati: nessun server LDAP esterno necessario.

```
[INFO] Running com.grash.service.LdapServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

> Nota tecnica: `LdapService` riceve `ldapTemplate`, `ldapAuthenticationProvider`
> e `userService` via field `@Autowired`; `@InjectMocks` fa solo constructor
> injection, quindi nel test questi collaboratori sono iniettati esplicitamente
> con `ReflectionTestUtils`. Testcontainers non è stato usato: il flusso è
> deterministico con i mock e non richiede un LDAP reale.

---

## Licensing regression

Non modificato alcun gate. Verificato:

- self-hosted concede `SSO` → contesto LDAP disponibile (coperto da
  `LicenseServiceTest`, entitlement `SSO` incluso nel set self-hosted);
- commercial senza entitlement → comportamento originale
  (`LicenseServiceTest.selfHosted_disabled_withoutLicense_returnsInvalidState_regression`).

`LicenseService`, `LicensingState`, `hasEntitlement()`, `LicenseEntitlement` e
`Consts.usageBasedFreeLimits` **invariati** (nessuna riga nel diff MOD-003A).

---

## Authentication tests

| Test | Verifica |
|---|---|
| `signinLdap_whenProviderNull_throwsForbidden` | LDAP non abilitato → 403 |
| `signinLdap_whenOrgAdminBlank_throwsServerError` | org-admin mancante → errore controllato |
| `signinLdap_invalidCredentials_throwsForbidden` | `AuthenticationException` (bind fallito) → 403 |
| `signinLdap_localNonLdapEmail_delegatesToUserServiceSignin` | collisione email locale/LDAP → delega a `userService.signin`, `authenticate` mai chiamato |

## Provisioning tests

| Test | Verifica |
|---|---|
| `signinLdap_newUser_provisionsInOrgAdminCompany_withMappedAttributes_andRandomPassword` | JIT provisioning; attribute mapping (email/firstName/lastName da LDAP); **isolamento company** (company dell'org-admin); `ssoProvider=LDAP`; password locale = hash random, **mai** la password AD (`verify(passwordEncoder, never()).encode("ad-secret")`) |
| `signinLdap_newUser_mapsOuToRole` | OU→role: DN con `OU=Admins` + mapping `admins=ADMIN` → ruolo `ADMIN` |

## Synchronization tests

| Test | Verifica |
|---|---|
| `syncLdapUsers_createsNewUser` | sync create → nuovo utente LDAP nella company dell'org-admin |
| `syncLdapUsers_disablesUserRemovedFromDirectory` | utente non più in AD + `sync.disable=true` → soft-disable (`enabled=false`), utente salvato |

## Security tests

| Test | Verifica |
|---|---|
| `extractLdapUserDetails_escapesLdapInjectionInUsername` | username `ab*)(uid=*` → filtro LDAP escapato (`\2a`, nessun `(uid=*` grezzo) |
| Password AD | mai salvata/copiata: coperto dal test di provisioning (password locale = hash random) |

---

## Build result

```text
Java version:  Eclipse Temurin 17.0.20.1
Maven version: 3.8.6 (mvnw wrapper)
Build result:  BUILD SUCCESS  (mvnw -DskipTests package → target/app.jar, Spring Boot repackaged)
Test result:   BUILD SUCCESS  (mvnw test)
```

---

## Full test suite

```
[INFO] Tests run: 1439, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Baseline precedente (MOD-002): **1430**. Ora **1439** = **+9** (i test
`LdapServiceTest`). 0 failure, 0 error, 0 skip. Nessun test preesistente
modificato o rotto.

| | Baseline (MOD-002) | Ora (MOD-003A) | Δ |
|---|---:|---:|---:|
| Test totali | 1430 | 1439 | +9 |
| Failures / Errors / Skipped | 0 / 0 / 0 | 0 / 0 / 0 | — |

---

## Docker verification

`docker compose config` → exit 0. Tutte le variabili LDAP risolte (default
blank/false quando non impostate); `LDAP_ENABLED="false"`,
`LICENSING_SELF_HOSTED_MODE="false"`. Boot end-to-end non eseguito (fuori scope
MOD-003A; la compose referenzia l'immagine pubblicata, non il build locale) — la
logica LDAP è coperta dai test unit.

---

## Remaining risks

| Rischio | Stato | Nota |
|---|---|---|
| Privilege escalation via `LDAP_OU_ROLE_MAPPINGS` → `ADMIN` | Aperto | algoritmo OU→role non modificato (fuori scope); documentato in [16](16-mod003-ldap-ad-audit.md) |
| Certificato LDAPS self-signed | Aperto | importare nel truststore JVM; StartTLS non implementato (fuori scope) |
| Default sync `false` in compose | Documentato | abilitare esplicitamente per il deprovisioning |
| Variabili compose senza default `:-` | Documentato | valorizzare gli attributi LDAP se LDAP abilitato |
| Credenziali service account in env | Documentato | usare secret manager |
| Ruolo aggiornato al login, non dal sync | Aperto (open decision) | invariato |

---

## Out of scope (non implementati, come da brief)

- `memberOf` → role mapping;
- ruoli Atlas custom nel mapping LDAP;
- allowlist/irrigidimento del role mapping;
- StartTLS;
- truststore/certificate pinning dedicati;
- multi-company LDAP;
- redesign dell'algoritmo OU→role;
- qualsiasi modifica al licensing;
- refactoring non correlato.

Restano **open decisions** per il responsabile tecnico (vedi
[16-mod003-ldap-ad-audit.md](16-mod003-ldap-ad-audit.md)).

---

## Git state

```
 M .env.example
 M api/src/main/java/com/grash/configuration/LdapSecurityConfig.java
 M api/src/main/java/com/grash/service/LicenseService.java          (MOD-001, invariato)
 M api/src/main/resources/application.yml                           (MOD-001, invariato)
 M docker-compose.yml                                               (MOD-001, invariato)
?? api/src/test/java/com/grash/service/LdapServiceTest.java         (MOD-003A)
?? api/src/test/java/com/grash/service/LicenseServiceTest.java      (MOD-001)
?? api/src/test/java/com/grash/service/SelfHostedUsageLimitsTest.java (MOD-002)
?? docs/
```

Modifiche **MOD-003A**: `.env.example`, `LdapSecurityConfig.java`,
`LdapServiceTest.java`. Nessun commit, nessun push. Nessun secret introdotto.

---

## Recommendation

**MOD-003A completata (PASS).** LDAP/AD è utilizzabile in self-hosted con sola
configurazione; il logging è stato hardenizzato senza esporre credenziali; il
flusso di autenticazione/provisioning/sync/sicurezza è ora coperto da test
deterministici; build e suite completa sono verdi (1439/0/0/0); il licensing e il
modello di sicurezza/tenancy restano invariati.

Prossimi passi possibili (decisione del responsabile tecnico, **non** avviati):
prova runtime live con immagine buildata localmente; e le open decisions
(`memberOf`, ruoli custom, allowlist, StartTLS, truststore dedicato,
aggiornamento ruolo nel sync, multi-company). Come da sezione "Decision gate",
**mi fermo**: non procedo con MOD-004/005/006.
