# 10 — Security & Licensing Considerations

---

## 1. Licenza del software (fondamentale)

Atlas CMMS è a **doppia licenza** ([`COMMERCIAL_LICENSE.MD`](../../COMMERCIAL_LICENSE.MD),
[`LICENSE`](../../LICENSE)):

- **AGPLv3** (open source), **oppure**
- **Licenza commerciale** INTELLOOP LLC (a pagamento).

Vanno usate in alternativa: non si possono combinare i diritti delle due.

### Implicazioni per la modifica dei gate

- Sotto **AGPLv3** il diritto di **modificare il codice sorgente** è
  esplicitamente concesso (art. modifica dell'AGPL). Le "Enterprise Features"
  sono presenti nel sorgente AGPL: modificarle per **uso interno** è coerente con
  l'AGPL.
- **Obbligo AGPL (network use):** se la build modificata viene messa a
  disposizione di utenti **attraverso la rete**, l'AGPL richiede di offrire agli
  utenti il **codice sorgente corrispondente** (incluse le modifiche). Per un uso
  interno aziendale, ciò significa rendere disponibile il sorgente modificato agli
  utenti interni della piattaforma.
- **Non usare** contemporaneamente la licenza commerciale e l'AGPL. Se non si
  acquista la licenza commerciale, la base giuridica delle modifiche è l'AGPL, con
  i relativi obblighi.
- **Prima di distribuire** la build fuori dall'ambiente interno, verificare gli
  obblighi (sezione 21 del prompt di audit). Questa è una valutazione legale, non
  tecnica: coinvolgere chi di competenza.

> Questo documento descrive la fattibilità tecnica; non costituisce consulenza
> legale.

---

## 2. Principi tecnici da rispettare nella Fase 2

1. **Non fare bypass globale alla cieca.** Evitare `return true` dentro
   `hasEntitlement()`. Motivi: rende imprevedibile ogni chiamata, nasconde le
   dipendenze esterne reali (LDAP, SMTP, storage), rende difficile la
   manutenzione e la sincronizzazione upstream.
2. **Preferire una modalità self-hosted centralizzata e configurabile** (un
   flag), così il comportamento è esplicito e disattivabile. Vedi
   [11-modification-plan.md](11-modification-plan.md).
3. **Preservare il sistema di licensing** per un'eventuale build cloud: la
   modifica deve essere condizionata da configurazione, non distruttiva.
4. **Non toccare** le integrazioni Keygen/Paddle/OAuth2/SMTP finché non è chiaro
   il loro ruolo (qui documentato): restano necessarie per il percorso cloud e
   per LDAP/SSO/email.
5. **Commit separati e documentati** per ogni modifica self-hosted, così da poter
   fare `merge`/`rebase` dell'upstream senza perdere le personalizzazioni.
   (Remote `upstream` già configurato verso `Grashjs/cmms`.)

---

## 3. Segreti e configurazione

- **Non committare segreti.** `.env.example` mostra le variabili; i valori reali
  vanno in `.env`/secret manager, mai nel repo.
- `keygen.account-id` è hardcoded in [`application.yml`](../../api/src/main/resources/application.yml)
  (`1ca3e517-...`): è l'account **del vendor**, non un segreto aziendale; in una
  modalità self-hosted centralizzata non viene comunque contattato.
- `license-fingerprint-required` (default `true`) e la `KEYGEN_PUBLIC_KEY`
  nel `LicenseService` riguardano la validazione della licenza del vendor: in
  modalità self-hosted centralizzata non entrano in gioco.
- Non modificare credenziali o secret durante l'audit (rispettato).

---

## 4. Superficie di sicurezza da non degradare

Sbloccando le feature, **non** vanno indeboliti i controlli **non** legati alla
licenza, che restano validi e vanno preservati:

- **Permessi di ruolo** (`PermissionEntity`, `getViewPermissions`/`getCreatePermissions`…):
  molte azioni sono gated anche da permessi (es. `FILES`, `SETTINGS`,
  `ANALYTICS`). Vanno mantenuti.
- **Isolamento multi-tenant per company**: i service filtrano per `company.getId()`.
- **Rate limiting** (upload file, scan asset, request portal).
- **Autenticazione/autorizzazione** (JWT, API key, OAuth2).

La modifica self-hosted deve toccare **solo** il livello entitlement, non questi
controlli.

---

## 5. Rischi residui

| Rischio | Mitigazione |
|---|---|
| Sblocco entitlement abilita percorsi che richiedono servizi esterni non pronti (LDAP/SMTP/storage) | Configurare prima i servizi esterni; gli entitlement non creano le dipendenze, le "espongono" |
| Divergenza dall'upstream difficile da mantenere | Modifica centralizzata + commit isolati + `upstream` remote |
| Confusione tra Livello A e Livello B | Documentato qui; la modifica agisce sul Livello A, il Livello B è già aperto in self-hosted |
| Discrepanze naming entitlement frontend/backend (`UNLIMITED_CHECKLIST(S)`, entitlement mancanti nella lista TS) | Se si adotta lista esplicita, allineare `models/owns/license.ts` all'enum backend |
