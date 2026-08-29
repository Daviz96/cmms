# MOD-017 — Polish i18n Key Integrity & Literal UI Audit

## 1. Contesto

MOD-016 è concluso con `PASS`. La localizzazione polacca mobile è stata corretta e verificata a runtime.

Durante MOD-016 è emerso però un problema più generale nella gestione delle translation keys. Alcune stringhe UI vengono passate a i18next come testo inglese letterale, ad esempio:

```text
t('Sign out')
t('Version')
```

Le relative chiavi risultano assenti dalla lingua base e quindi i18next può mostrare la chiave stessa come fallback.

Questo MOD deve analizzare il problema **esclusivamente dal punto di vista della lingua polacca**.

La lingua inglese/base viene utilizzata soltanto come riferimento tecnico per capire le chiavi e il comportamento i18n.

NON è richiesto alcun audit linguistico o correzione delle altre lingue.

---

## 2. Obiettivo

Individuare e correggere i problemi i18n che producono una UI polacca non tradotta, errata o incoerente a causa di:

- chiavi mancanti;
- literal English keys;
- fallback sulla chiave inglese;
- traduzioni polacche mancanti;
- placeholder non coerenti.

Workflow:

```text
SOURCE AUDIT
↓
IDENTIFY PL IMPACT
↓
VERIFY CONTEXT
↓
MINIMAL FIX
↓
STATIC CHECKS
↓
BUILD
↓
RUNTIME VERIFY IN POLISH
↓
DOCUMENT
```

Target obbligatori:

```text
Sign out
Version
```

Cercare anche eventuali casi analoghi che abbiano effettivo impatto sulla lingua polacca.

---

## 3. Scope

### Consentito

- analizzare le chiamate i18n del mobile;
- analizzare `en.ts` come lingua base di riferimento;
- analizzare il locale polacco;
- individuare literal UI keys con impatto PL;
- correggere le key necessarie;
- correggere esclusivamente le relative traduzioni polacche;
- aggiungere check statici pertinenti;
- buildare e verificare l'app;
- aggiornare documentazione;
- aggiornare `CLAUDE.md`.

### NON consentito

NON correggere:

- traduzioni tedesche;
- traduzioni italiane;
- traduzioni francesi;
- traduzioni spagnole;
- altre lingue non polacche.

NON eseguire un audit linguistico generale delle altre locale.

NON modificare:

- backend;
- API;
- database;
- licensing;
- production;
- Caddy;
- DNS;
- logica applicativa non necessaria.

Non effettuare refactoring generale.

---

## 4. Fonti da leggere

Prima di modificare:

1. `CLAUDE.md`;
2. `docs/self-hosted-audit/36-mod016-polish-translation-audit.md`;
3. `mobile/i18n/i18n.ts`;
4. `mobile/i18n/translations/en.ts`;
5. file di traduzione polacco;
6. solo i componenti che utilizzano le stringhe individuate.

Non analizzare l'intero repository.

---

## 5. Analisi del sistema i18n

Verificare la configurazione reale e documentare brevemente:

```text
translation library:
base locale:
Polish locale:
fallback behavior:
keySeparator:
translation loading:
```

Non assumere il comportamento: verificarlo nel codice.

---

## 6. Audit delle literal keys

Cercare nel codice mobile pattern come:

```text
t('Literal English text')
t("Literal English text")
```

Per ogni candidato determinare:

```text
File:
Line:
Call:
Key:
Exists in en.ts:
Exists in Polish:
Visible in UI:
Polish impact:
```

Distinguere:

```text
intentional key
vs
literal UI text accidentally used as key
```

NON correggere automaticamente ogni stringa letterale.

---

## 7. Target obbligatori

Verificare innanzitutto:

```text
Sign out
Version
```

Per ciascuno determinare:

- dove viene utilizzato;
- se è visibile nell'interfaccia;
- se esiste già una key equivalente;
- quale key dovrebbe essere utilizzata;
- se la traduzione polacca esiste;
- se la UI polacca mostra la parola inglese come fallback.

Non creare duplicati se esiste già una key semanticamente equivalente.

---

## 8. Lingua base

`en.ts` deve essere utilizzato esclusivamente come **riferimento tecnico**.

Se una nuova key è necessaria:

```text
base key
↓
Polish translation
```

La correzione deve rispettare la struttura e le convenzioni reali del progetto.

Non modificare le traduzioni delle altre lingue per completare la nuova key.

Se una modifica alla lingua base è tecnicamente necessaria per il funzionamento corretto della key, limitarla allo stretto indispensabile e documentarla.

---

## 9. Naming delle keys

Prima di creare una nuova key verificare le convenzioni esistenti.

Se esiste già una key semanticamente equivalente, riutilizzarla.

Non introdurre una nuova convenzione di naming.

Il risultato deve rimanere compatibile con la configurazione i18n già presente.

---

## 10. Traduzioni polacche

Per ogni key che richiede una correzione PL verificare il contesto reale.

Non tradurre automaticamente la parola inglese.

Esempio:

```text
Save
```

deve essere interpretato nel contesto dell'azione UI.

La scelta della traduzione deve essere coerente con il polacco già utilizzato nel progetto.

Se la traduzione è ambigua:

```text
REVIEW REQUIRED
```

Non inventare.

---

## 11. Key integrity PL

Verificare almeno:

```text
base keys
vs
Polish keys
```

Individuare:

- missing keys in PL;
- extra keys in PL;
- duplicate keys;
- placeholder mismatch.

Non modificare le altre locale.

---

## 12. Placeholder e markup

Preservare esattamente:

```text
{{variable}}
{variable}
%s
%d
HTML
Markdown
newline escapes
```

Non modificare accidentalmente:

- placeholder;
- interpolation;
- pluralization;
- escape sequence;
- markup;
- key names.

Se esiste un mismatch PL, correggerlo solo se la soluzione è determinabile dal codice e dalla lingua base.

---

## 13. Literal-key Detection

Se possibile creare un check che segnali le UI label passate direttamente come testo a `t(...)` quando:

- sono effettivamente visibili;
- non sono key previste;
- producono fallback;
- hanno impatto sulla UI polacca.

Il check deve evitare falsi positivi evidenti.

Non introdurre un framework o dipendenze complesse per ottenere questo risultato.

Se un check affidabile non è possibile, documentare il limite.

---

## 14. Implementazione

Per ogni problema PL confermato:

1. identificare/riutilizzare la key corretta;
2. modificare la lingua base solo se tecnicamente necessario;
3. aggiungere/correggere la traduzione polacca;
4. sostituire eventuale `t('literal')` con `t('key')`;
5. verificare placeholder e formato.

Il diff deve essere minimo.

---

## 15. Build

Dopo le modifiche eseguire un build Android reale usando il percorso già verificato dal progetto.

Non considerare sufficiente un semplice controllo statico.

Generare una nuova APK perché le traduzioni sono incluse nel bundle dell'app.

---

## 16. Runtime verification

Installare la nuova APK sull'AVD `atlas_test`.

Verificare con lingua polacca:

```text
Settings
Version
Logout
Sign out
```

e tutte le schermate direttamente interessate.

Verificare:

- traduzione polacca corretta;
- assenza di fallback inglese per i casi corretti;
- assenza di errori i18n;
- placeholder corretti;
- layout non danneggiato.

---

## 17. Regression

Eseguire almeno:

```text
Launch
Login
Dashboard
Work Orders
Assets
Settings
Logout
```

Lo scopo è verificare che le modifiche i18n non abbiano introdotto regressioni funzionali.

Non ripetere automaticamente tutta la suite di MOD-015 se non necessaria.

---

## 18. Evidence

Per i problemi principali registrare:

```text
Key:
Before:
After:
Context:
Reason:
Polish impact:
Evidence:
```

Per i casi UI più importanti usare screenshot before/after quando utile.

Non inserire secret o dati personali.

---

## 19. Security

NON includere o modificare:

- Firebase credentials;
- API key;
- JWT;
- password;
- token;
- dati personali.

---

## 20. Git

Prima e dopo:

```powershell
git status
git diff
git diff --check
```

Il diff deve contenere esclusivamente modifiche pertinenti a questo MOD.

NON eseguire:

```text
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

---

## 21. Documentation

Produrre:

```text
docs/self-hosted-audit/37-mod017-polish-i18n-key-audit.md
```

Struttura:

```text
# MOD-017 — Polish i18n Key Integrity & Literal UI Audit

## 1. Objective
## 2. Localization Architecture
## 3. Polish Scope
## 4. Literal Key Audit
## 5. Findings
## 6. Key Corrections
## 7. Polish Translation Corrections
## 8. Key/Placeholder Integrity
## 9. Static Checks
## 10. Build
## 11. Runtime Verification
## 12. Regression
## 13. Remaining Polish Issues
## 14. CLAUDE.md Update
## 15. Final Verdict
```

Il report deve contenere:

```text
Literal keys analyzed:
Confirmed PL-impact issues:
Corrections:
P1:
P2:
P3:
Missing PL keys:
Extra PL keys:
Placeholder issues:
Files modified:
```

---

## 22. CLAUDE.md

Aggiornare sempre `CLAUDE.md`.

Aggiornare:

- MOD-017;
- stato i18n polacco;
- eventuali regole sulle translation keys;
- Known Issues;
- Documentation Map.

Non inserire l'intero elenco delle key nel `CLAUDE.md`.

Il dettaglio rimane nel report MOD-017.

---

## 23. Anti-Hallucination

NON inventare:

- traduzioni polacche;
- key;
- convenzioni;
- fallback;
- comportamento i18next;
- requisiti linguistici.

Ordine:

```text
documentazione
→ codice
→ file locale
→ contesto UI
→ runtime evidence
→ REVIEW REQUIRED
```

Se una traduzione non è determinabile:

```text
NON MODIFICARE
DOCUMENTARE
```

---

## 24. STOP Conditions

Fermarsi se:

- la soluzione richiede backend/API;
- emerge una modifica architetturale;
- serve una nuova dipendenza significativa;
- è necessario modificare la logica funzionale;
- il comportamento atteso non è determinabile;
- per risolvere il problema PL sarebbe necessario modificare sistematicamente altre lingue.

In questi casi:

```text
documentare
STOP
```

---

## 25. Definition of Done

MOD-017 è completo quando:

- audit delle literal i18n con impatto PL eseguito;
- `Sign out` verificato;
- `Version` verificato;
- eventuali casi analoghi PL classificati;
- key necessarie corrette;
- traduzioni polacche corrette;
- key integrity PL verificata;
- placeholder integrity PL verificata;
- static checks eseguiti;
- release APK generata;
- correzioni verificate sull'app in polacco;
- regression minima passata;
- documentazione aggiornata;
- `CLAUDE.md` aggiornato.

---

## 26. Final Output

```text
CLAUDE.md updated: YES/NO

Localization system: IDENTIFIED/BLOCKED
Polish locale: IDENTIFIED/BLOCKED

Literal keys analyzed: X
Confirmed PL-impact issues: X

Sign out:
FOUND / NOT FOUND
FIXED / NOT A BUG / REVIEW

Version:
FOUND / NOT FOUND
FIXED / NOT A BUG / REVIEW

Additional PL issues:
NONE / LIST

Keys created/reused:
LIST

Polish corrections:
X

Missing PL keys after fix:
X
Extra PL keys:
X
Duplicate keys:
X
Placeholder mismatches:
X

Typecheck: PASS/FAIL/N/A
Lint: PASS/FAIL/N/A
Translation consistency: PASS/FAIL/N/A

Android build:
PASS/FAIL/BLOCKED

Runtime verification in Polish:
PASS/FAIL/PARTIAL

Regression:
PASS/FAIL/PARTIAL

Other-language changes:
NONE / LIST

Backend changes:
NONE / LIST

Production changes:
NONE / LIST

New dependencies:
NONE / LIST

Remaining Polish issues:
NONE / LIST

Final verdict:
PASS / PASS WITH FINDINGS / FAIL

Next step:
MOD-018 / USER DECISION
```

## 27. Regola finale

MOD-017 riguarda **solo la qualità e l'integrità i18n con impatto sulla lingua polacca**.

La lingua inglese viene usata come riferimento tecnico.

Le altre lingue NON sono oggetto del lavoro.

Non trasformare questo MOD in un audit linguistico generale.

Al termine: **STOP**.
