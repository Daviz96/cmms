# 16 — MOD-003 LDAP / Active Directory Audit

Fase **solo audit + progettazione**. Nessuna modifica al codice, nessun commit.
`Code changes: none`.

Riferimenti: [05-ldap-ad.md](05-ldap-ad.md), [13-mod001-implementation.md](13-mod001-implementation.md),
[14-mod001-verification.md](14-mod001-verification.md), [15-mod002-verification.md](15-mod002-verification.md).

---

## Stato

**PARTIAL**

LDAP/Active Directory è **completamente implementato** nel codice AGPL
(autenticazione bind, provisioning JIT, sync schedulata, mapping OU→ruolo,
attribute mapping). L'**unico gate commerciale** è l'entitlement `SSO`, già
concesso in self-hosted da MOD-001. È **utilizzabile self-hosted con
configurazione**, ma prima di un rollout AD di produzione servono: alcune scelte
di configurazione, la riconciliazione di default divergenti, hardening TLS/role
mapping e nuovi test del flusso di autenticazione (tutto documentato qui, **non
applicato**).

---

## Executive summary

- **Nessun blocco commerciale reale**: l'unico gate è
  `LdapSecurityConfig.contextSource` che lancia se manca l'entitlement `SSO`
  ([riga 62](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java#L62)).
  MOD-001 (self-hosted) concede `SSO` → gate superato senza Keygen.
  `LdapService.signinLdap` **non** ha alcun controllo licensing.
- **Attivazione**: `@ConditionalOnProperty("ldap.enabled"=true)` sull'intera
  config. Con `ldap.enabled=false` (default) nessun bean LDAP viene creato.
- **AD supportato** tramite configurazione: attributi (`sAMAccountName`, `mail`,
  `givenName`, `sn`), `objectClass=user`, filtro di ricerca, service account bind.
- **Docker già cablato**: tutte le variabili `LDAP_*` sono già passate al
  container `api` in `docker-compose.yml`.
- **Da affrontare prima della produzione**: StartTLS assente (solo `ldaps://`),
  rischio privilege-escalation nel mapping OU→ruolo (config), default sync
  divergenti tra `application.yml` e `docker-compose.yml`, `.env.example`
  incompleto, test del flusso di autenticazione mancanti.

---

## LDAP architecture

```
POST /auth/signin-ldap  (permitAll)  ──► AuthController.signinLdap
        │
        ▼
LdapService.signinLdap(LdapLoginRequest)
        │  usa ldapAuthenticationProvider (bean solo se ldap.enabled=true e SSO)
        ▼
LdapAuthenticationProvider  ── BindAuthenticator + FilterBasedLdapUserSearch (per ogni base)
        │
        ▼
LDAP/AD server (bind)  ──►  success/fail
        │ success
        ▼
provisioning JIT / update utente locale (company = owner ldap.org-admin)
        │
        ▼
ruolo da OU→role mapping   ──►  refreshTokenService.createTokenPair → JWT
```

Componenti:

| Componente | File | Ruolo |
|---|---|---|
| Config beans | [`configuration/LdapSecurityConfig.java`](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java) | contextSource, authenticator (multi-base), authoritiesPopulator, userDetailsMapper, provider. Gate `SSO`. |
| Servizio | [`service/LdapService.java`](../../api/src/main/java/com/grash/service/LdapService.java) | signin, provisioning JIT, sync, attribute/OU mapping |
| Endpoint | [`controller/AuthController.java:66`](../../api/src/main/java/com/grash/controller/AuthController.java#L66) | `POST /auth/signin-ldap` |
| DTO | [`dto/LdapLoginRequest.java`](../../api/src/main/java/com/grash/dto/LdapLoginRequest.java) | `{username, password}` |
| Sync job | [`job/LdapSyncJob.java`](../../api/src/main/java/com/grash/job/LdapSyncJob.java) | Quartz Job → `syncLdapUsers()` |
| Sync scheduler | [`job/LdapSyncJobScheduler.java`](../../api/src/main/java/com/grash/job/LdapSyncJobScheduler.java) | ApplicationRunner: cron + sync iniziale |
| Security wiring | [`configuration/WebSecurityConfig.java`](../../api/src/main/java/com/grash/configuration/WebSecurityConfig.java) | provider LDAP nel ProviderManager, permitAll endpoint |

Dipendenze (pom): `org.springframework.ldap:spring-ldap-core`,
`org.springframework.security:spring-security-ldap` (versioni gestite dal BOM
Spring Boot 3.5.16; backend JNDI). Nessuna libreria UnboundID/Apache Directory.

---

## LDAP configuration

Da `application.yml` (blocco `ldap:`), con default:

| Proprietà | Env | Default | Note |
|---|---|---|---|
| `ldap.enabled` | `LDAP_ENABLED` | `false` | attiva l'intera config (`@ConditionalOnProperty`) |
| `ldap.url` | `LDAP_URL` | `ldap://localhost:389` | `ldap://` o `ldaps://` |
| `ldap.base-dn` | `LDAP_BASE_DN` | — | base DN |
| `ldap.org-admin` | `LDAP_ORG_ADMIN` | — | email owner della company target |
| `ldap.manager-dn` | `LDAP_MANAGER_DN` | — | service account (search mode) |
| `ldap.manager-password` | `LDAP_MANAGER_PASSWORD` | — | **secret** |
| `ldap.user-search-bases` | `LDAP_USER_SEARCH_BASES` | — | multiple, separate da `\|` |
| `ldap.user-search-filter` | `LDAP_USER_SEARCH_FILTER` | `(uid={0})` | template bind |
| `ldap.search-subtree` | `LDAP_SEARCH_SUBTREE` | `true` | |
| `ldap.attributes.username` | `LDAP_ATTR_USERNAME` | `uid` | AD: `sAMAccountName` |
| `ldap.attributes.email` | `LDAP_ATTR_EMAIL` | `mail` | |
| `ldap.attributes.first-name` | `LDAP_ATTR_FIRSTNAME` | `givenName` | |
| `ldap.attributes.last-name` | `LDAP_ATTR_LASTNAME` | `sn` | |
| `ldap.attributes.object-class` | `LDAP_OBJECT_CLASS` | `inetOrgPerson` | AD: `user` |
| `ldap.ou-role-mappings` | `LDAP_OU_ROLE_MAPPINGS` | — | `ou=ROLE_CODE\|ou2=ROLE_CODE2` |
| `ldap.sync.enabled` | `LDAP_SYNC_ENABLED` | `true` (yml) | vedi discrepanza sotto |
| `ldap.sync.create` | `LDAP_SYNC_CREATE` | `true` (yml) | |
| `ldap.sync.update` | `LDAP_SYNC_UPDATE` | `true` (yml) | |
| `ldap.sync.disable` | `LDAP_SYNC_DISABLE` | `false` (yml) | soft-disable |
| `ldap.sync.cron` | `LDAP_SYNC_CRON` | `0 0 0,12 * * ?` | 2×/giorno |

> ⚠️ **Discrepanza default sync**: `docker-compose.yml` imposta
> `LDAP_SYNC_ENABLED/CREATE/UPDATE/DISABLE` a **`false`** di default (righe 88-91),
> mentre `application.yml` usa `true` per enabled/create/update. In un deployment
> Docker il sync è quindi **disattivato di default** finché non lo si abilita
> esplicitamente. Da riconciliare/documentare (non modificato in questa fase).

---

## Authentication flow

`LdapService.signinLdap` ([riga 220](../../api/src/main/java/com/grash/service/LdapService.java#L220)):

1. Se `ldapAuthenticationProvider == null` → `403 "LDAP authentication is not
   enabled"`. Il provider esiste solo con `ldap.enabled=true` **e** entitlement
   `SSO`.
2. Se `ldap.org-admin` non configurato → `500`.
3. **Fallback utente locale**: se lo username è un'email e l'utente trovato **non**
   è LDAP (`ssoProvider != "LDAP"`) → login normale `userService.signin(...)`.
4. Risoluzione username LDAP: per email→`ssoProviderId`, oppure
   `findUserByLdapId`.
5. Bind: `ldapAuthenticationProvider.authenticate(UsernamePasswordAuthenticationToken(ldapUsername, password))`.
6. Su successo:
   - utente inesistente → **provisioning JIT** (`getNewLdapUser`) nella company
     dell'`org-admin`;
   - utente esistente → aggiorna `lastLogin` e **rivaluta il ruolo** da OU ad ogni
     login ([righe 271-273](../../api/src/main/java/com/grash/service/LdapService.java#L271)).
7. `refreshTokenService.createTokenPair(user)` → JWT/refresh.
8. `AuthenticationException` → `403 "LDAP authentication failed"`.

Note: multi-base — `ldapAuthenticator` prova ogni base in `user-search-bases`
finché una autentica ([LdapSecurityConfig righe 104-113](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java#L104)); se nessuna → `BadCredentialsException`.

---

## User provisioning

`getNewLdapUser` ([riga 161](../../api/src/main/java/com/grash/service/LdapService.java#L161)):

- `ssoProvider="LDAP"`, `ssoProviderId=<ldap username>`, `createdViaSso=true`.
- **Password locale** = `passwordEncoder.encode(UUID.randomUUID())` → casuale, la
  password AD **non** viene mai salvata.
- `enabled=true`, `company` = quella dell'`org-admin`, ruolo = OU→role (o default
  `LIMITED_TECHNICIAN`).
- Identità utente LDAP: coppia `(ssoProviderId, ssoProvider="LDAP")`
  (`findBySsoProviderIdAndSsoProvider`).

Il provisioning avviene sia al **primo login** (JIT) sia via **sync** (se
`ldap.sync.create=true`).

---

## Synchronization

`syncLdapUsers` ([riga 94](../../api/src/main/java/com/grash/service/LdapService.java#L94), `@Profile("!test")`):

- Guardie: `ldapSyncEnabled`, `ldapTemplate != null`, `ldap.org-admin` valorizzato.
- Company target = `companyService.findByOwnerEmailAndOwnsCompany(ldap.org-admin)`.
- Recupera tutti gli username LDAP (`fetchAllLdapUsernames`).
- **Utenti locali LDAP non più presenti in AD** → se `ldap.sync.disable=true`,
  `setEnabled(false)` (soft-disable, **mai** cancellazione hard).
- **Utenti presenti**: se `ldap.sync.update=true` aggiorna email/nome (diff-based);
  riabilita se era disabilitato.
- **Nuovi utenti**: se `ldap.sync.create=true` → `getNewLdapUser` + save.
- Scheduler: `LdapSyncJobScheduler` (ApplicationRunner, `@Profile("!test")`)
  schedula il cron Quartz **e** lancia un **sync iniziale all'avvio**.

Risposte al questionario (sezione 8 del brief):

| Domanda | Comportamento |
|---|---|
| Chi esegue il sync | `LdapSyncJob` (Quartz) + sync iniziale allo startup |
| Scheduler Spring | Quartz (job store JDBC) via `LdapSyncJobScheduler` |
| Identificazione utente | `ssoProviderId` (= username LDAP) + `ssoProvider="LDAP"` |
| Utente AD eliminato | soft-disable **solo se** `sync.disable=true`, altrimenti resta attivo |
| Cambio email | aggiornata se `sync.update=true` |
| Cambio nome | aggiornato se `sync.update=true` |
| Cambio gruppo/OU | **il sync NON aggiorna il ruolo**; il ruolo si aggiorna solo al **login** |
| Aggiornamento ruoli | solo al login (rivalutazione OU→role) |
| Sovrascrittura utente locale | no: agisce solo su utenti con `ssoProvider="LDAP"` |
| Soft-disable vs delete | solo soft-disable |
| Company errata | no: sempre la company dell'`org-admin` |

> ⚠️ Il ruolo NON viene aggiornato dal sync (solo al login). Un utente che cambia
> OU in AD mantiene il vecchio ruolo finché non rifà login.

---

## AD group / role mapping

- **Non** si usano i gruppi LDAP per le authorities: `authoritiesPopulator`
  restituisce un set vuoto ([LdapSecurityConfig righe 120-132](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java#L120)).
- Il ruolo Atlas deriva dagli **OU nel DN** dell'utente:
  `getLdapUserOu` estrae i segmenti `ou=` dal DN; `getRoleForOu` li mappa via
  `ldap.ou-role-mappings` (formato `ou=ROLE_CODE|ou2=ROLE_CODE2`, case-insensitive,
  **primo match**), altrimenti default `LIMITED_TECHNICIAN`.
- Il `ROLE_CODE` è confrontato con `role.getCode().name()` tra i **ruoli di
  default** (`roleService.findDefaultRoles()`) → **solo ruoli di default**
  (enum `RoleCode`), **non** ruoli custom.
- **Nessun mapping da group membership AD** (`memberOf`): solo OU dal DN.

Classificazione: **C** (parziale) — funziona ma limitato ai ruoli di default e
al DN-OU, non ai gruppi AD.

---

## OAuth2 / SSO separation

Percorso **separato e implementato**, da non confondere con LDAP:

- File: `configuration/OAuth2ClientRegistrationConfig.java`,
  `security/OAuth2AuthenticationSuccessHandler.java`,
  `OAuth2AuthenticationFailureHandler.java`, `security/OAuth2Properties.java`.
- Attivazione: `WebSecurityConfig` abilita `oauth2Login` solo se
  `enable-sso=true` **e** `licenseService.isSSOEnabled()`
  ([riga 96](../../api/src/main/java/com/grash/configuration/WebSecurityConfig.java#L96)).
- Provisioning OAuth2 (`createUserFromOAuth`): basato sul **dominio email**;
  crea utente `createdViaSso=true` con subscription BUSINESS. Logica **diversa** da
  LDAP (che usa la company dell'org-admin).
- Endpoint: `/oauth2/**`, `/login/oauth2/**`, `/auth/sso/**` (permitAll).

**OAuth2/OIDC va trattato come modulo separato** (non necessario per LDAP/AD).
Classificazione: **A** (implementato; gate `enable-sso` + entitlement `SSO`
concesso da MOD-001) — richiede provider OAuth2 esterno.

---

## Licensing gates

| Funzione | Gate licensing | Effetto | Self-hosted (MOD-001) | Azione necessaria |
|---|---|---|---|---|
| LDAP context/bind | `hasEntitlement(SSO)` in `LdapSecurityConfig.contextSource` | throw `IllegalStateException` se assente | ✅ concesso → nessun blocco | nessuna (config `ldap.enabled=true`) |
| LDAP login | nessuno (solo `provider != null`) | — | ✅ | nessuna |
| LDAP sync | nessuno | — | ✅ | nessuna |
| LDAP user creation/update/disable | nessuno | — | ✅ | nessuna |
| LDAP role mapping (OU) | nessuno | — | ✅ | nessuna |
| OAuth2/SSO | `enable-sso` + `isSSOEnabled()` | oauth2Login attivo solo se entrambi | ✅ (entitlement) | config `enable-sso=true` + provider |

**Unico gate commerciale = entitlement `SSO`**, concesso da MOD-001. Nessun altro
controllo `hasEntitlement`/`hasLicense` nel percorso LDAP. **Non modificato.**

---

## Security audit

**Credenziali service account** (`LDAP_MANAGER_DN/PASSWORD`):
- Iniettate via env → `contextSource.setPassword` (non loggate).
- Non compaiono in eccezioni applicative. Visibili via `docker inspect` (standard
  per env Docker) → gestire come secret (secret manager / Docker secrets).
- ⚠️ In `ldapAuthenticator` un bind fallito su una base fa `ignored.printStackTrace()`
  ([riga 109](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java#L109))
  → stack trace su stderr (può includere DN/base, **non** password). Hardening
  consigliato: log strutturato a livello debug.

**TLS**:
- `ldaps://` supportato (via URL). **StartTLS non implementato**.
- Nessun truststore custom: usa il truststore JVM → un certificato AD self-signed
  va importato nel truststore. **Nessun toggle per disabilitare la validazione
  del certificato** (default sicuro: non è possibile disattivarla).

**Injection**:
- Filtri costruiti con `EqualsFilter`/`AndFilter` e `.encode()` (Spring LDAP →
  escaping RFC 4515). Lo username utente passa da `EqualsFilter(usernameAttr,
  safeUsername).encode()` e dal bind search (`FilterBasedLdapUserSearch` con
  sostituzione `{0}` gestita/escapata da Spring). → **LDAP injection mitigata**.
- `fetchAllLdapUsernames` fa `filter.replace("{0}", "*")` ma opera sul **filtro di
  configurazione** (non input utente).

**Password**:
- La password AD è usata **solo** per il bind; **mai** salvata (password locale =
  UUID random hashata), mai loggata, mai copiata nel DB.

---

## Multi-tenancy audit

- Ogni utente LDAP è associato **sempre** alla company dell'`ldap.org-admin`
  (`findByOwnerEmailAndOwnsCompany`). Un utente LDAP **non** può scegliere la
  company né accedere ad altre company.
- Se l'`org-admin` non ha una company → login `500` (nessuna creazione impropria).
- Ruoli/permessi restano quelli del modello Atlas (assegnati entro quella
  company). LDAP **non** bypassa i controlli di organization/company/role/permission
  a valle.
- **Modello mono-company**: un realm LDAP → una company Atlas. Adeguato per uso
  interno mono-organizzazione; non copre scenari multi-company via LDAP.
- Collisione email: se lo username-email coincide con un utente locale **non**-LDAP,
  vince il flusso locale (`userService.signin`) — nessun bypass (serve comunque la
  password corretta), ma possibile confusione di account da documentare.

---

## Docker configuration

`docker-compose.yml` **già cabla tutte** le variabili LDAP al servizio `api`
(righe 74-92). Nessuna modifica necessaria alla compose per abilitare AD.

| Variable | Required (AD) | Secret | Default compose | Descrizione |
|---|---|---|---|---|
| `LDAP_ENABLED` | sì | no | `false` | attiva LDAP |
| `LDAP_URL` | sì | no | `` | `ldaps://ad.example.com:636` |
| `LDAP_BASE_DN` | sì | no | `` | `dc=example,dc=com` |
| `LDAP_ORG_ADMIN` | sì | no | `` | email owner company target |
| `LDAP_MANAGER_DN` | sì (search) | no | `` | service account DN |
| `LDAP_MANAGER_PASSWORD` | sì (search) | **sì** | `` | password service account |
| `LDAP_USER_SEARCH_BASES` | sì | no | `` | `ou=Users,dc=example,dc=com` |
| `LDAP_USER_SEARCH_FILTER` | sì | no | `` | AD: `(sAMAccountName={0})` |
| `LDAP_ATTR_USERNAME` | sì | no | (yml `uid`) | AD: `sAMAccountName` |
| `LDAP_OBJECT_CLASS` | sì | no | (yml `inetOrgPerson`) | AD: `user` |
| `LDAP_ATTR_EMAIL/FIRSTNAME/LASTNAME` | consigliato | no | (yml `mail/givenName/sn`) | attribute mapping |
| `LDAP_OU_ROLE_MAPPINGS` | opzionale | no | `` | `ou=ADMIN\|ou2=LIMITED_TECHNICIAN` |
| `LDAP_SYNC_ENABLED/CREATE/UPDATE/DISABLE` | opzionale | no | `false` | ⚠️ default compose `false` |
| `LDAP_SYNC_CRON` | opzionale | no | (yml `0 0 0,12 * * ?`) | schedule |

> ⚠️ `.env.example` documenta **solo** `LDAP_ENABLED=false`. Le altre variabili
> LDAP non sono presenti nell'esempio (gap di documentazione da colmare in fase
> di implementazione).

---

## Existing tests

| Classe | # test | Copertura |
|---|---:|---|
| `LdapSecurityConfigTest` | 9 | gate `SSO` (throw/allow), contextSource, ldapTemplate, authenticator (con/senza search config), authoritiesPopulator, userDetailsMapper (mapping attributi e attributi mancanti), provider |
| `LdapSyncJobTest` | 2 | `execute` chiama sync; swallow eccezioni |
| `LdapSyncJobSchedulerTest` | 6 | non-schedula se disabilitato (sync/ldap), schedula + sync iniziale, reschedule, swallow eccezioni |
| `AuthControllerTest` | 1 (ldap) | `POST /auth/signin-ldap` restituisce AuthResponse (service mockato) |

Coprono config, gate licensing, orchestrazione sync e wiring endpoint.

## Missing tests

Il **flusso di autenticazione reale** (`LdapService.signinLdap`) e il
provisioning/mapping **non** sono coperti. Da progettare (non implementare ora):

1. autenticazione LDAP riuscita → provisioning JIT + token;
2. credenziali non valide → `403`;
3. utente sconosciuto → `403` / BadCredentials multi-base;
4. attribute mapping (email/firstName/lastName) e fallback `username@local`;
5. creazione utente (sync + JIT);
6. update utente (email/nome) via sync;
7. soft-disable (utente rimosso da AD, `sync.disable=true`);
8. OU→role mapping (match, no-match→default, primo match);
9. LDAP non raggiungibile → nessun crash, login fallisce in modo pulito;
10. TLS/LDAPS failure (cert non fidato);
11. LDAP injection (username con `*()\\` → filtri escapati);
12. multi-company isolation (utente LDAP sempre nella company org-admin);
13. collisione email locale vs LDAP (fallback a `userService.signin`).

> Nota: i nuovi test richiederebbero un LDAP in container (Testcontainers) per il
> flusso end-to-end; l'infrastruttura Testcontainers esiste già nel progetto.

---

## Dependency analysis

| Libreria | Coordinata | Versione | Uso |
|---|---|---|---|
| Spring LDAP Core | `org.springframework.ldap:spring-ldap-core` | BOM Spring Boot 3.5.16 | `LdapTemplate`, filtri, contextSource |
| Spring Security LDAP | `org.springframework.security:spring-security-ldap` | BOM Spring Boot 3.5.16 | `BindAuthenticator`, `FilterBasedLdapUserSearch`, `LdapAuthenticationProvider` |

Backend JNDI (JDK). Nessuna dipendenza LDAP aggiuntiva (no UnboundID/Apache DS).
Nessuna nuova dipendenza introdotta o proposta in questa fase.

---

## Feature classification

Legenda: **A** già funzionante self-hosted · **B** implementata ma bloccata da
licensing · **C** parziale · **D** non implementata · **E** implementata ma
insicura/da correggere.

| Funzionalità | Classe | Note |
|---|---|---|
| LDAP bind authentication | **A** | via MOD-001 (SSO concesso); **B** senza MOD-001 |
| Provisioning JIT al login | **A** | company = org-admin |
| Sync schedulata (create/update/disable) | **A** | soft-disable; ⚠️ default compose `false` |
| Attribute mapping (AD) | **A** | configurabile (`sAMAccountName`, `mail`, `user`…) |
| OU → role mapping | **C** | solo ruoli di default, DN-OU, non gruppi AD; rischio escalation |
| Group membership (`memberOf`) → ruoli | **D** | non implementato (authoritiesPopulator vuoto) |
| `ldaps://` (LDAPS) | **A** | via URL; truststore JVM |
| StartTLS | **D** | non implementato |
| Custom truststore / cert pinning | **C** | solo truststore JVM, nessuna config dedicata |
| OAuth2 / OIDC SSO | **A** | modulo separato; `enable-sso` + SSO |
| Sync: aggiornamento ruolo | **C** | ruolo aggiornato solo al login, non dal sync |
| Logging bind fallito (`printStackTrace`) | **E** | hardening minore consigliato |

---

## Required modifications

Documentate, **non applicate**. Da valutare per un eventuale `MOD-003-impl`:

1. **Configurazione AD** (nessun codice): `LDAP_ENABLED=true`, `LDAP_URL=ldaps://…`,
   `LDAP_BASE_DN`, `LDAP_ORG_ADMIN`, `LDAP_MANAGER_DN/PASSWORD`,
   `LDAP_USER_SEARCH_BASES`, `LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})`,
   `LDAP_ATTR_USERNAME=sAMAccountName`, `LDAP_OBJECT_CLASS=user`.
2. **`.env.example`**: aggiungere l'elenco completo delle variabili `LDAP_*`
   (solo placeholder, nessun secret).
3. **Riconciliare i default sync** tra `application.yml` (`true`) e
   `docker-compose.yml` (`false`); scegliere un default coerente e documentarlo.
4. **Hardening TLS**: documentare l'import del certificato AD nel truststore;
   valutare supporto StartTLS come modulo separato.
5. **Role mapping**: documentare/limitare il rischio di escalation (es. allowlist
   dei RoleCode mappabili); valutare supporto ruoli custom e gruppi AD `memberOf`.
6. **Aggiornamento ruolo nel sync** (attualmente solo al login): valutare se
   allineare sync e login.
7. **Logging**: sostituire `printStackTrace()` con log strutturato.
8. **Test**: implementare i test del flusso di autenticazione (lista sopra).

---

## Risks

| Rischio | Gravità | Mitigazione |
|---|---|---|
| Privilege escalation via `LDAP_OU_ROLE_MAPPINGS` → `ADMIN` (misconfig o OU controllata) | Alta | allowlist RoleCode, review config, evitare mapping ad ADMIN |
| Cert LDAPS self-signed non fidato → login fallisce silenziosamente (eccezioni ingoiate) | Media | importare cert nel truststore; migliorare logging |
| Default sync `false` in compose → utenti AD rimossi restano attivi | Media | impostare `LDAP_SYNC_DISABLE=true` se serve deprovisioning |
| Modello mono-company | Media | adeguato per uso interno; non multi-org |
| Fallback email `username@local` per attributi mancanti | Bassa | configurare correttamente `LDAP_ATTR_EMAIL` |
| Credenziali service account in env (docker inspect) | Bassa/Media | secret manager / Docker secrets |
| Ruolo aggiornato solo al login, non dal sync | Bassa | documentare; login ricalcola |

---

## Recommendation

**LDAP/Active Directory è implementato e non è bloccato dal licensing commerciale
oltre l'entitlement `SSO`, già concesso da MOD-001.** È utilizzabile in
self-hosted **con sola configurazione** (nessuna modifica al codice per il caso
base).

Prima di un rollout AD di produzione si raccomanda un modulo separato
`MOD-003-impl` (di sola configurazione + documentazione + test, **senza** toccare
il licensing) che: (a) aggiunga le variabili `LDAP_*` a `.env.example`, (b)
riconcili i default di sync, (c) documenti/hardeni TLS e role mapping, (d)
implementi i test del flusso di autenticazione.

Come da sezione 19 del brief, **mi fermo**: la decisione su come procedere
(LDAP già utilizzabile / modifica minimale / implementazione / suddivisione in
moduli / quali test) spetta al responsabile tecnico.

---

## Git

```
git status --short   →  invariato rispetto a MOD-001/MOD-002
```

Modifiche tracciate: solo quelle di MOD-001 (LicenseService, application.yml,
docker-compose.yml, .env.example). File non tracciati: i test di MOD-001/MOD-002 e
la cartella `docs/`. **MOD-003 non ha modificato codice.**

**Code changes: none.**
