# MOD-020 — Release Commit, Tag & Push

## 1. Contesto

MOD-019 è concluso con:

```text
CODE FREEZE APPROVED
READY FOR CODE FREEZE
```

Il final code audit ha stabilito che non sono necessari ulteriori fix applicativi prima del freeze.

Il repository contiene attualmente le modifiche accumulate durante i MOD precedenti e la documentazione prodotta.

Il prossimo obiettivo è trasformare il working tree verificato in una **baseline versionata e riproducibile**.

Sequenza:

```text
FINAL TEST
↓
SECRET CHECK
↓
REPOSITORY REVIEW
↓
STAGE
↓
COMMIT
↓
TAG
↓
PUSH
↓
STOP
```

Questo MOD NON deve introdurre nuove funzionalità.

---

# 2. Obiettivo

Preparare e versionare la release corrente di Atlas.

Il risultato deve essere:

```text
working tree verificato
→ commit
→ release tag
→ push remoto
```

La versione committata sarà la baseline da cui verranno:

- generata l'APK definitiva;
- preparato il deployment server;
- eseguiti i test live.

---

# 3. Fonti

Prima leggere:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/38-mod018-project-state-recap.md`;
3. `docs/self-hosted-audit/39-mod019-final-code-audit.md`;
4. documentazione direttamente collegata alle modifiche presenti nel working tree.

Non ripetere gli audit già conclusi.

---

# 4. Regola fondamentale

MOD-020 è un'operazione di **release/versionamento**.

NON utilizzare questo MOD per:

- correggere bug non bloccanti;
- fare refactoring;
- modificare architettura;
- modificare licensing;
- modificare frontend;
- modificare mobile;
- modificare traduzioni;
- aggiornare dipendenze;
- modificare database;
- aggiungere nuove feature.

Se viene scoperto un problema reale che impedisce il commit/release:

```text
STOP
→ DOCUMENT
→ NON CORREGGERE AUTONOMAMENTE
```

salvo problema puramente meccanico necessario per completare il versionamento.

---

# 5. Working Tree Audit

Eseguire:

```powershell
git status --short
git diff --stat
git diff --check
git diff
git ls-files --others --exclude-standard
```

Verificare che ogni modifica sia riconducibile a:

```text
MOD-001
MOD-002
MOD-003A
MOD-004B/C
MOD-006
MOD-011
MOD-015
MOD-016
MOD-017
MOD-018
MOD-019
```

o alla documentazione/test associati.

Se compare un file non riconducibile al progetto:

```text
DO NOT STAGE
```

e documentarlo.

---

# 6. Documentazione

La directory:

```text
docs/
```

contiene la documentazione persistente dei MOD.

Prima del commit verificare quali documenti sono stati prodotti e devono essere versionati.

Obiettivo:

```text
documentazione tecnica del progetto
+
codice
+
test
```

devono costituire una baseline riproducibile.

Non committare:

- log temporanei;
- dump;
- screenshot non destinati alla documentazione;
- file generati temporanei;
- secret;
- artefatti locali.

---

# 7. Secret Audit

Prima dello staging verificare nuovamente:

```text
.env
.env.*
google-services.json
*.pem
*.key
credentials
tokens
JWT
password
API keys
Firebase secrets
```

Prestare particolare attenzione ai file nuovi sotto `docs/` e ai test.

Verificare:

```powershell
git status --short
git diff --cached
```

dopo lo staging.

NON stampare valori segreti.

Se viene trovato un secret reale:

```text
STOP
SECURITY FINDING
```

---

# 8. .gitignore

Verificare che gli artefatti locali rimangano esclusi:

```text
.env
mobile/android/app/google-services.json
build/
APK
logs
IDE files
local configuration
```

Non modificare `.gitignore` se non è necessario.

Se un file deve essere ignorato ma non lo è:

```text
STOP
DOCUMENT
```

e chiedere approvazione prima di modificare la policy Git.

---

# 9. Backend Test Gate

MOD-019 ha stabilito che la suite comportamentale `1446/1446` deve essere rieseguita al gate di commit.

Prima dello staging/commit:

1. avviare Docker/Testcontainers secondo il workflow già documentato;
2. eseguire la suite backend reale;
3. registrare il risultato.

Target documentato:

```text
1446/1446 PASS
```

Se il numero di test è cambiato, riportare il nuovo totale.

Se un test fallisce:

```text
DO NOT COMMIT
STOP
```

Non modificare il codice per far passare il test senza una nuova autorizzazione.

---

# 10. Compile/Test

Verificare almeno:

```text
backend test
backend compile
Android release build, se necessario per il gate
```

Non duplicare inutilmente tutti i test già eseguiti.

Usare le verifiche di MOD-019 come baseline.

---

# 11. Mobile

Il codice mobile deve rimanere invariato rispetto alla baseline verificata in MOD-017/019.

Verificare che non siano comparse modifiche non documentate.

Non effettuare nuovo bug discovery.

Non correggere nuovi bug durante MOD-020.

---

# 12. Frontend

Nessuna modifica frontend è prevista.

Verificare:

```text
frontend/** = unchanged
```

Se compare una modifica frontend:

```text
STOP
→ identify source
→ document
→ do not commit until resolved
```

---

# 13. Licensing

Non modificare il licensing.

Verificare soltanto che il diff relativo al licensing corrisponda alla soluzione approvata:

```text
self-hosted centralizzato
no Keygen per self-hosted
no bypass sparsi
authorization invariata
tenant isolation invariata
cloud/commercial path invariato
```

---

# 14. Database

Non creare migration.

Verificare che non ci siano nuovi:

```text
.sql
migration
liquibase
schema changes
```

non documentati.

---

# 15. Staging

Dopo aver completato tutti i gate:

```powershell
git add <solo file approvati>
```

NON usare:

```powershell
git add -A
```

se questo rischia di includere file non verificati.

Poi:

```powershell
git status
git diff --cached --stat
git diff --cached --check
git diff --cached
```

Verificare nuovamente:

- secret;
- file locali;
- artefatti;
- documentazione;
- test;
- modifiche applicative.

---

# 16. Commit

Solo dopo approvazione implicita di tutti i gate:

creare un singolo commit di release coerente con la baseline.

Messaggio consigliato:

```text
release: finalize self-hosted Atlas modifications
```

Se il repository utilizza una convenzione documentata diversa, rispettare quella convenzione.

Non creare commit intermedi inutili.

---

# 17. Tag

Dopo il commit creare un tag release.

Prima verificare se il repository/documentazione definisce già una versione.

NON inventare una numerazione se esiste già una convenzione.

Se non esiste una convenzione documentata, utilizzare una versione esplicita e semplice, ad esempio:

```text
self-hosted-v1.0.0
```

ma riportare nel report che la numerazione è stata scelta in assenza di una convenzione preesistente.

Preferire un annotated tag:

```powershell
git tag -a <TAG> -m "<TAG> release"
```

NON creare tag multipli.

---

# 18. Verifica post-commit

Dopo il commit:

```powershell
git status
git log -1 --oneline
git show --stat --oneline HEAD
```

Il working tree deve risultare pulito salvo file locali esplicitamente ignorati.

Verificare:

```text
commit hash
tag
working tree clean
```

---

# 19. Push

Il push remoto è parte di MOD-020.

Prima verificare:

```powershell
git remote -v
git branch --show-current
git status
```

NON modificare remote URL senza autorizzazione.

Pushare:

```text
branch corrente
+
tag release
```

Solo verso il remote già configurato e documentato.

NON eseguire:

```text
force push
push --force
reset
rebase distruttivo
```

---

# 20. Verifica remota

Dopo il push verificare che:

```text
branch
commit
tag
```

siano presenti sul remote.

Se possibile usare i comandi Git già disponibili per verificare il riferimento remoto.

Non modificare altro dopo il push.

---

# 21. Version Baseline

Registrare nel report:

```text
Branch:
Commit:
Tag:
Remote:
Working tree:
Backend test:
Android build:
```

Questa diventerà la baseline per:

```text
MOD-021 Android Release APK
MOD-022 Server Deployment Preparation
```

---

# 22. Documentation

Produrre:

```text
docs/self-hosted-audit/40-mod020-release-commit-tag-push.md
```

Struttura:

```text
# MOD-020 — Release Commit, Tag & Push

## 1. Objective
## 2. Pre-Commit Repository State
## 3. Secret Audit
## 4. .gitignore Verification
## 5. Backend Test Gate
## 6. Build Verification
## 7. Staged Files
## 8. Commit
## 9. Release Tag
## 10. Push
## 11. Remote Verification
## 12. Release Baseline
## 13. CLAUDE.md Update
## 14. Final Verdict
```

---

# 23. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

```text
MOD-020 status
Current Project State
Current Focus
Release baseline
Commit
Tag
Next Step
```

Dopo un push riuscito indicare:

```text
RELEASE BASELINE VERSIONED
```

Non inserire il diff completo.

---

# 24. Anti-Hallucination

NON dichiarare:

```text
1446/1446 PASS
```

se il test non è stato realmente eseguito in MOD-020.

NON dichiarare:

```text
PUSH SUCCESSFUL
```

senza verifica.

NON dichiarare:

```text
WORKING TREE CLEAN
```

senza `git status`.

NON dichiarare:

```text
TAG CREATED
```

senza verifica.

NON inventare remote, branch, tag o versione.

---

# 25. STOP Conditions

Fermarsi immediatamente se:

- un test backend fallisce;
- viene trovato un secret;
- esiste un file non attribuibile;
- compare una modifica frontend non documentata;
- compare una migration;
- il remote non è quello atteso;
- il repository richiede autenticazione non disponibile;
- il push richiede force push;
- la convenzione di versioning non è determinabile;
- il working tree non può essere reso coerente.

Documentare e STOP.

---

# 26. Definition of Done

MOD-020 è completo quando:

- working tree verificato;
- modifiche classificate;
- secret audit PASS;
- `.gitignore` verificato;
- backend suite eseguita e PASS;
- build gate PASS;
- staging verificato;
- commit creato;
- tag release creato;
- branch pushato;
- tag pushato;
- remote verificato;
- working tree post-release verificato;
- `CLAUDE.md` aggiornato;
- release baseline documentata.

---

# 27. Final Output

```text
CLAUDE.md updated: YES/NO

Branch:
<value>

Pre-commit working tree:
X tracked modified
X untracked

Secret audit:
PASS / FAIL

.gitignore:
PASS / FINDINGS

Backend test:
X/X PASS / FAIL

Android build:
PASS / FAIL / NOT RUN

Frontend:
UNCHANGED / FINDINGS

Database:
NO CHANGES / FINDINGS

Staged files:
X

Commit:
<hash>

Commit message:
<message>

Release tag:
<tag>

Push branch:
SUCCESS / FAIL

Push tag:
SUCCESS / FAIL

Remote verification:
PASS / FAIL

Post-release working tree:
CLEAN / NOT CLEAN

Release baseline:
<commit> + <tag>

Next step:
MOD-021 — Android Release APK

Final verdict:
RELEASE VERSIONED / BLOCKED
```

---

# 28. Regola finale

MOD-020 rappresenta il **punto di non ritorno della fase di sviluppo corrente**.

Una volta completato con successo:

```text
CODE FREEZE
↓
COMMIT
↓
TAG
↓
PUSH
```

la baseline diventa la sorgente ufficiale per la release Android e per il deployment server.

Da quel momento eventuali modifiche al codice devono essere trattate come nuove modifiche successive alla release.

Al termine: **STOP**.

Non generare l'APK definitiva in questo MOD.

Non configurare il server in questo MOD.

Non fare deployment in questo MOD.
