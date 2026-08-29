# 05 — LDAP / Active Directory

Obiettivo aziendale futuro: collegare Atlas all'Active Directory. Questo
documento fotografa **ciò che già esiste** (nessuna modifica proposta qui).

---

## 1. Componenti

| Componente | File | Ruolo |
|---|---|---|
| Security config | [`configuration/LdapSecurityConfig.java`](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java) | Bean LDAP (contextSource, authenticator, provider) |
| Service | [`service/LdapService.java`](../../api/src/main/java/com/grash/service/LdapService.java) | Sync, sign-in, mapping OU→ruolo |
| Job di sync | [`job/LdapSyncJob.java`](../../api/src/main/java/com/grash/job/LdapSyncJob.java), [`job/LdapSyncJobScheduler.java`](../../api/src/main/java/com/grash/job/LdapSyncJobScheduler.java) | Sync schedulata (Quartz) |
| Login DTO | [`dto/LdapLoginRequest.java`](../../api/src/main/java/com/grash/dto/LdapLoginRequest.java) | |
| Endpoint auth | [`controller/AuthController.java`](../../api/src/main/java/com/grash/controller/AuthController.java) | login LDAP |
| Instance config | [`controller/InstanceConfigController.java`](../../api/src/main/java/com/grash/controller/InstanceConfigController.java), [`dto/InstanceConfig.java`](../../api/src/main/java/com/grash/dto/InstanceConfig.java) | espone `ldapEnabled` al frontend |

---

## 2. Il gate: entitlement `SSO`

[`LdapSecurityConfig.java:24-63`](../../api/src/main/java/com/grash/configuration/LdapSecurityConfig.java#L24):

```java
@Configuration
@ConditionalOnProperty(name = "ldap.enabled", havingValue = "true")
public class LdapSecurityConfig {
    @Bean
    public LdapContextSource contextSource(LicenseService licenseService) {
        LdapContextSource contextSource = new LdapContextSource();
        if (!licenseService.hasEntitlement(LicenseEntitlement.SSO))
            throw new IllegalStateException("SSO entitlement is required for LDAP authentication");
        ...
    }
}
```

Due condizioni per attivare LDAP:

1. `ldap.enabled=true` (env `LDAP_ENABLED`, default `false`) → attiva la config.
2. Entitlement `SSO` presente → altrimenti il bean `contextSource` lancia
   `IllegalStateException` all'avvio del contesto LDAP.

⇒ In self-hosted senza licenza SSO, l'autenticazione LDAP è bloccata.

---

## 3. Cosa è già implementato (LdapService)

Analisi delle firme dei metodi:

- **`syncLdapUsers()`** ([:94](../../api/src/main/java/com/grash/service/LdapService.java#L94)) —
  sincronizza gli utenti LDAP: crea/aggiorna/disabilita in base ai flag.
- **`fetchAllLdapUsernames()`** ([:181](../../api/src/main/java/com/grash/service/LdapService.java#L181)) —
  ricerca utenti (user search bases + filter).
- **`getLdapUserDetailsByUsername()`** / **`extractLdapUserDetails()`** —
  mapping attributi (email, first/last name, username, objectClass).
- **`getNewLdapUser()`** ([:161](../../api/src/main/java/com/grash/service/LdapService.java#L161)) —
  creazione utente locale da record LDAP.
- **`getLdapUserOu()` / `extractOuFromDn()`** ([:369/:395](../../api/src/main/java/com/grash/service/LdapService.java#L369)) —
  estrazione OU dal DN per il **mapping OU → ruolo**.
- **`signinLdap(LdapLoginRequest)`** ([:220](../../api/src/main/java/com/grash/service/LdapService.java#L220)) —
  login con bind LDAP, emette token (`refreshTokenService.createTokenPair`).
- **`getDefaultRoleForLdapUser()`** ([:284](../../api/src/main/java/com/grash/service/LdapService.java#L284)) —
  ruolo di default.
- **`findUserByLdapId()`** ([:365](../../api/src/main/java/com/grash/service/LdapService.java#L365)).

**Conclusione:** client LDAP, autenticazione, user search, sync
(create/update/disable), mapping OU→ruolo e attribute mapping sono **già presenti
e funzionanti**. Manca solo l'entitlement e la configurazione.

---

## 4. Configurazione disponibile (application.yml:129-200)

| Proprietà | Env | Default | Descrizione |
|---|---|---|---|
| `ldap.enabled` | `LDAP_ENABLED` | `false` | Attiva LDAP |
| `ldap.url` | `LDAP_URL` | `ldap://localhost:389` | URL server |
| `ldap.base-dn` | `LDAP_BASE_DN` | | Base DN |
| `ldap.org-admin` | `LDAP_ORG_ADMIN` | | |
| `ldap.manager-dn` / `manager-password` | `LDAP_MANAGER_DN` / `_PASSWORD` | | Service account (search mode) |
| `ldap.user-search-bases` | `LDAP_USER_SEARCH_BASES` | | separate da `\|` |
| `ldap.user-search-filter` | `LDAP_USER_SEARCH_FILTER` | `(uid={0})` | |
| `ldap.search-subtree` | `LDAP_SEARCH_SUBTREE` | `true` | |
| `ldap.ou-role-mappings` | `LDAP_OU_ROLE_MAPPINGS` | | Mapping OU→ruolo |
| `ldap.sync.enabled` | `LDAP_SYNC_ENABLED` | `true` | |
| `ldap.sync.create` | `LDAP_SYNC_CREATE` | `true` | |
| `ldap.sync.update` | `LDAP_SYNC_UPDATE` | `true` | |
| `ldap.sync.disable` | `LDAP_SYNC_DISABLE` | `false` | |
| `ldap.sync.cron` | `LDAP_SYNC_CRON` | `0 0 0,12 * * ?` | 2 volte/giorno |
| `ldap.attributes.email` | `LDAP_ATTR_EMAIL` | `mail` | |
| `ldap.attributes.first-name` | `LDAP_ATTR_FIRSTNAME` | `givenName` | |
| `ldap.attributes.last-name` | `LDAP_ATTR_LASTNAME` | `sn` | |
| `ldap.attributes.username` | `LDAP_ATTR_USERNAME` | `uid` | |
| `ldap.attributes.object-class` | `LDAP_OBJECT_CLASS` | `inetOrgPerson` | |

Vedi anche la guida esistente [`dev-docs/LDAP_SETUP.md`](../../dev-docs/LDAP_SETUP.md).

---

## 5. Active Directory

AD è LDAP-compatibile. Gli attributi già configurabili permettono il mapping AD
tipico, ad esempio:

- `LDAP_ATTR_USERNAME=sAMAccountName`
- `LDAP_OBJECT_CLASS=user`
- `LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})`
- `manager-dn`/`manager-password` per il bind con service account AD.

**Non richiede nuovo codice** per il caso base; richiede l'entitlement `SSO`
(Livello A) e la configurazione. Il mapping OU→ruolo è già supportato.

---

## 6. Classificazione

🟡 **UNLOCK_PLUS_MODIFICATION** — la feature è completa nel codice; per usarla in
self-hosted servono: (a) sblocco dell'entitlement `SSO`, (b) `ldap.enabled=true`,
(c) un server LDAP/AD raggiungibile (dipendenza esterna). Nessun componente
commerciale mancante.
