# Atlas CMMS — MOD-002
## Verifica e validazione dei limiti numerici in modalità Self-Hosted

## Stato decisionale

MOD-001 è **APPROVATA**.

La verifica precedente ha dimostrato:

- build Java 17 riuscita;
- 5/5 test MOD-001 superati;
- 1412/1412 test complessivi superati;
- `LICENSING_SELF_HOSTED_MODE` centralizzato;
- `hasEntitlement()` invariato;
- nessun bypass di autenticazione/autorizzazione;
- nessuna chiamata Keygen in modalità self-hosted;
- policy self-hosted attualmente basata sull'intero `LicenseEntitlement`;
- `UNLIMITED_*` quindi risultano attivi in self-hosted.

La documentazione precedente identifica **MOD-002 — Limiti numerici illimitati** come
un modulo che, in teoria, non richiede modifiche al codice: i limiti vengono
bypassati automaticamente quando i relativi entitlement `UNLIMITED_*` risultano
attivi.

Questa attività serve quindi a **dimostrare concretamente** che tale assunzione
è corretta.

NON introdurre una nuova implementazione del licensing.

NON modificare i service dei limiti se il comportamento attuale è già corretto.

NON procedere con MOD-003.

---

# 1. Obiettivo

Verificare che, con:

```env
LICENSING_SELF_HOSTED_MODE=true
```

i limiti numerici commerciali di Atlas vengano effettivamente disattivati
tramite gli entitlement `UNLIMITED_*`.

Il flusso desiderato è:

```text
LICENSING_SELF_HOSTED_MODE=true
        ↓
LicenseService
        ↓
LicensingState
        ↓
UNLIMITED_*
        ↓
hasEntitlement(UNLIMITED_X)
        ↓
usageBasedFreeLimits
        ↓
nessun limite commerciale
```

Non vogliamo sostituire i limiti con valori artificialmente enormi.

Non vogliamo modificare `Consts.usageBasedFreeLimits`.

Non vogliamo aggiungere `if (selfHosted)` nei service.

---

# 2. Fonte di verità

Usare come riferimento:

```text
docs/self-hosted-audit/04-feature-matrix.md
docs/self-hosted-audit/11-modification-plan.md
docs/self-hosted-audit/12-test-plan.md
docs/self-hosted-audit/13-mod001-implementation.md
docs/self-hosted-audit/14-mod001-verification.md
```

e il codice attuale del repository.

In particolare individuare:

```text
Consts.usageBasedFreeLimits
```

e tutti i punti in cui vengono verificati gli entitlement:

```text
UNLIMITED_ASSETS
UNLIMITED_USERS
UNLIMITED_LOCATIONS
UNLIMITED_PARTS
UNLIMITED_PREVENTIVE_MAINTENANCE
UNLIMITED_WORK_ORDERS
UNLIMITED_CHECKLISTS
UNLIMITED_METERS
```

Usare i nomi effettivamente presenti nel repository. Se un nome differisce,
documentarlo invece di correggerlo arbitrariamente.

---

# 3. Prima fase — audit del codice

Prima di modificare qualsiasi cosa, ricostruire per ogni limite:

```text
Feature
↓
Entitlement
↓
Service
↓
Metodo che controlla il limite
↓
Condizione che consente il superamento
```

Produrre una tabella:

| Feature | Entitlement | Limite attuale | Service | Metodo | Self-hosted bypass |
|---|---|---:|---|---|---|
| Assets | UNLIMITED_ASSETS | ... | ... | ... | sì/no |
| Users | UNLIMITED_USERS | ... | ... | ... | sì/no |
| Locations | UNLIMITED_LOCATIONS | ... | ... | ... | sì/no |
| Parts | UNLIMITED_PARTS | ... | ... | ... | sì/no |
| PM | UNLIMITED_PREVENTIVE_MAINTENANCE | ... | ... | ... | sì/no |
| Work Orders | UNLIMITED_WORK_ORDERS | ... | ... | ... | sì/no |
| Checklists | UNLIMITED_CHECKLISTS | ... | ... | ... | sì/no |
| Meters | UNLIMITED_METERS | ... | ... | ... | sì/no |

Se una feature non utilizza realmente `hasEntitlement()` per il proprio limite,
segnalarlo.

---

# 4. Non modificare il codice se MOD-001 è sufficiente

Se l'analisi dimostra:

```text
UNLIMITED_X
    ↓
hasEntitlement()
    ↓
true in self-hosted
    ↓
limite bypassato
```

allora:

**NON modificare il codice applicativo.**

In questo caso MOD-002 è una verifica funzionale/documentale, non una modifica
del software.

---

# 5. Test obbligatori

I test devono dimostrare sia il comportamento commerciale sia quello
self-hosted.

## 5.1 Commercial mode — limite attivo

Con:

```env
LICENSING_SELF_HOSTED_MODE=false
```

verificare almeno un limite rappresentativo:

```text
conteggio <= limite
    → operazione consentita

conteggio > limite
    → operazione rifiutata
```

Se esistono già test equivalenti, riutilizzarli.

Non duplicare inutilmente test già presenti.

---

# 6. Self-hosted mode — limite disattivato

Con:

```env
LICENSING_SELF_HOSTED_MODE=true
```

verificare almeno:

```text
hasEntitlement(UNLIMITED_ASSETS) == true
```

e che il relativo limite non venga applicato.

Fare la stessa verifica per tutte le categorie che possono essere testate in
modo ragionevole.

Priorità:

1. Assets
2. Users
3. Locations
4. Parts
5. Preventive Maintenance
6. Work Orders
7. Checklists
8. Meters

Non è necessario creare migliaia di record.

Utilizzare test mirati o mocking del conteggio quando l'architettura lo permette.

---

# 7. Test parametrico preferito

Se la struttura del codice lo consente, preferire un test parametrico invece di
duplicare otto test quasi identici.

Schema:

```text
for each UNLIMITED entitlement:
    selfHosted = true
    assert hasEntitlement(entitlement)
    assert usage limit is not enforced
```

Per le feature dove il test parametrico non è tecnicamente appropriato,
utilizzare test dedicati.

---

# 8. Verifica dei limiti reali

Non limitarsi a verificare:

```java
hasEntitlement() == true
```

Bisogna verificare anche il comportamento del codice che applica il limite.

Per esempio:

```text
UNLIMITED_ASSETS = true
+
asset count > free limit
=
asset creation permitted
```

Questo distingue:

```text
licensing sbloccato
```

da:

```text
feature realmente utilizzabile
```

---

# 9. Attenzione ai limiti non commerciali

Non assumere che ogni limite numerico del progetto sia un limite licensing.

Distinguere:

```text
commercial/free-tier limit
```

da:

```text
technical limit
database constraint
security limit
validation limit
resource limit
```

MOD-002 deve eliminare esclusivamente i limiti commerciali collegati agli
entitlement `UNLIMITED_*`.

Non modificare limiti tecnici o di sicurezza.

---

# 10. Multi-instance

Durante l'analisi verificare anche:

```text
MULTI_INSTANCE
```

Questo entitlement non è un limite numerico.

Il precedente report lo ha identificato come una **decisione di prodotto**:
self-hosted abilita attualmente l'intero enum e quindi consente più company nella
stessa istanza.

NON modificare questo comportamento in MOD-002.

Nel report finale riportare semplicemente:

```text
MULTI_INSTANCE:
enabled / disabled
```

e ricordare che la decisione definitiva è separata da MOD-002.

---

# 11. Test di regressione

Eseguire:

```bash
cd api
./mvnw test
```

oppure su Windows:

```powershell
cd api
.\mvnw.cmd test
```

Il risultato precedente era:

```text
1412 tests
0 failures
0 errors
0 skipped
```

Registrare il nuovo risultato.

Se il numero dei test è aumentato, spiegare quali test sono stati aggiunti.

---

# 12. Docker

Se possibile, effettuare anche una verifica runtime utilizzando **l'immagine
costruita dal codice locale**, non semplicemente:

```text
intelloop/atlas-cmms-backend
```

upstream.

Se il Dockerfile del repository consente il build locale:

```bash
docker build ...
```

e utilizzare l'immagine locale nel test.

Avviare Atlas con:

```env
LICENSING_SELF_HOSTED_MODE=true
```

e verificare almeno:

```text
GET /license/state
```

e una operazione rappresentativa che in modalità commerciale sarebbe limitata.

Non utilizzare una vera licenza Keygen.

---

# 13. Nessun bypass aggiuntivo

Durante il lavoro NON introdurre:

```text
return true
```

nei service dei limiti.

NON modificare:

```text
usageBasedFreeLimits
```

per inserire valori come:

```text
999999999
```

NON aggiungere:

```text
if (selfHosted) ...
```

nei controller/service.

La modalità deve continuare a funzionare attraverso:

```text
LicenseService
→ LicensingState
→ hasEntitlement()
```

---

# 14. Se trovi un problema

Se un limite non viene bypassato correttamente:

1. NON applicare immediatamente un workaround;
2. individuare il punto esatto del gate;
3. identificare l'entitlement utilizzato;
4. verificare se MOD-001 sta producendo il valore corretto;
5. verificare se il gate usa `hasEntitlement()` oppure un meccanismo diverso;
6. documentare la causa.

Solo se è evidente che il problema è stato introdotto da MOD-001 è consentita
una correzione minimale.

Se invece il problema richiede una nuova modifica architetturale:

```text
STOP
```

e documentarlo come modifica separata.

---

# 15. Output richiesto

Creare:

```text
docs/self-hosted-audit/15-mod002-verification.md
```

con:

```markdown
# 15 — MOD-002 Verification

## Stato
PASS / PASS WITH ISSUES / FAIL

## Limiti analizzati

## Asset limits

## User limits

## Location limits

## Part limits

## Preventive Maintenance limits

## Work Order limits

## Checklist limits

## Meter limits

## Commercial mode verification

## Self-hosted mode verification

## Test results

## Docker runtime verification

## MULTI_INSTANCE status

## Findings

## Code changes
None / elenco

## Recommendation
```

---

# 16. Regole Git

Eseguire:

```bash
git status
git diff
```

NON fare commit.

NON fare push.

NON modificare file non necessari.

Se non è stata necessaria alcuna modifica, il risultato atteso è:

```text
Code changes: none
```

---

# 17. Decision gate

Alla fine fermarsi.

NON procedere con:

```text
MOD-003 LDAP/AD
MOD-004 Storage
MOD-005 Email
MOD-006
```

Il responsabile tecnico analizzerà:

```text
15-mod002-verification.md
```

e deciderà il prossimo passo.

La conclusione attesa, se tutti i test confermano l'analisi, è:

```text
MOD-002 non richiede modifiche al codice.
I limiti commerciali sono già disattivati dagli entitlement
self-hosted introdotti da MOD-001.
```
