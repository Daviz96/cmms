# MOD-016 — Polish Translation Audit & Correction

## 1. Contesto

MOD-015 è completato con esito `PASS`. M-BUG-1 è stato riprodotto, corretto e verificato con release APK reale e regression completa.

Il prossimo obiettivo è migliorare la qualità delle traduzioni **polacche** dell'app Atlas.

Esempio già osservato:

```text
Save → "ratuj"
```

mentre nel contesto di un'azione UI potrebbe essere `Zapisz`. Questo è un esempio da verificare nel contesto reale, non una regola da applicare automaticamente.

## 2. Obiettivo

Eseguire un audit mirato delle traduzioni polacche e correggere gli errori effettivi:

```text
INVENTORY
→ IDENTIFY
→ VERIFY CONTEXT
→ CORRECT
→ STATIC CHECKS
→ BUILD
→ RUNTIME VERIFY
→ DOCUMENT
```

La traduzione deve essere corretta, naturale, coerente e adatta a un'interfaccia software aziendale.

## 3. Scope

Consentito:
- analizzare file di localizzazione;
- analizzare i punti d'uso quando il contesto è ambiguo;
- correggere traduzioni polacche errate;
- correggere typo, calchi e termini impropri;
- uniformare terminologia chiaramente incoerente;
- creare check automatici pertinenti;
- build e verifica mobile;
- aggiornare documentazione e `CLAUDE.md`.

NON modificare backend, API, DB, licensing, logica applicativa non necessaria, Caddy, DNS o production. Non modificare altre lingue salvo necessità tecnica documentata. Non fare refactoring generale.

## 4. Fonti

Prima leggere:
1. `CLAUDE.md`;
2. `docs/self-hosted-audit/35-mod015-mobile-bug-fix.md`;
3. documentazione localizzazione pertinente;
4. file di traduzione mobile;
5. solo i componenti che usano una stringa quando il contesto è ambiguo.

Non analizzare l'intero repository.

## 5. Inventory

Identificare il sistema reale:

```text
translation library:
locale files:
Polish locale:
English/base locale:
fallback locale:
translation loading mechanism:
```

Non assumere framework o percorsi senza verificarli.

## 6. Confronto con lingua base

Confrontare locale polacco e lingua base per trovare:
- chiavi mancanti;
- traduzioni sospette;
- significati errati;
- incoerenze;
- grammatica;
- calchi;
- traduzioni innaturali.

NON riscrivere automaticamente tutte le stringhe. Preservare quelle già corrette.

## 7. Verifica contestuale

Non correggere una traduzione basandosi soltanto sulla parola inglese.

Per ogni caso dubbio verificare il punto d'uso.

Per esempio, `Save` va interpretato come azione UI e non tradotto meccanicamente.

Se la scelta rimane ambigua:

```text
REVIEW REQUIRED
```

e non inventare una decisione.

## 8. Terminologia

Creare un piccolo glossario dei termini UI ricorrenti:

```text
English | Polish | Context | Decision
```

Usarlo per evitare incoerenze. Non creare un documento enorme.

## 9. Categorie

Classificare, quando utile:

```text
LITERAL_TRANSLATION
WRONG_MEANING
GRAMMAR
TYPO
INCONSISTENCY
UI_CONTEXT
TECHNICAL_TERM
UNNATURAL_POLISH
MISSING_TRANSLATION
```

Non modificare una formulazione semplicemente perché esiste un'alternativa stilistica se quella attuale è corretta.

## 10. Priorità

```text
P1 = significato sbagliato/fuorviante, azione UI errata, messaggio confusivo
P2 = incoerenza, grammatica, traduzione innaturale, typo evidente
P3 = miglioramento stilistico minore
```

Correggere prima P1/P2.

## 11. Termini tecnici

Non tradurre automaticamente nomi propri, prodotti, acronimi, codici, identificatori o termini tecnici che devono rimanere invariati.

Verificare l'uso reale nel progetto.

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

Non alterare placeholder, interpolation, pluralization, escape o markup.

## 13. Key Integrity

Verificare, secondo il sistema reale:

```text
base locale keys == Polish locale keys
```

Rilevare:
- missing keys;
- extra keys;
- duplicate keys;
- obsolete keys;
- placeholder mismatch.

Non eliminare chiavi obsolete senza verificarne l'uso.

## 14. Automazione

Se utile, creare un check per:

```text
missing keys
extra keys
placeholder mismatch
```

Non introdurre dipendenze non necessarie.

## 15. Modifiche

Le correzioni devono essere mirate, leggibili e facilmente revisionabili.

Preferire i file di localizzazione. Se è necessario modificare un componente per il contesto, documentare il motivo.

## 16. Test

Eseguire i controlli realmente disponibili:

```text
typecheck
lint
prettier
translation consistency check
```

Non inventare comandi.

## 17. Build e runtime

Dopo le modifiche eseguire un build mobile reale usando il percorso già verificato dal progetto.

Verificare nell'app almeno le aree toccate dalle correzioni e, come regression minima:

```text
Launch
Login
Dashboard
Work Orders
Assets
Settings
Logout
```

Verificare anche che testi più lunghi non producano problemi evidenti di layout.

## 18. Evidence

Per le correzioni importanti registrare:

```text
Key:
Before:
After:
Context:
Reason:
Evidence:
```

Per casi UI significativi usare screenshot before/after quando utile.

Non includere secret o dati personali.

## 19. Documentation

Produrre:

```text
docs/self-hosted-audit/36-mod016-polish-translation-audit.md
```

Struttura:

```text
# MOD-016 — Polish Translation Audit & Correction
## 1. Objective
## 2. Localization Architecture
## 3. Inventory
## 4. Findings
## 5. Corrections
## 6. Terminology
## 7. Key/Placeholder Integrity
## 8. Static Checks
## 9. Build
## 10. Runtime Verification
## 11. Regression
## 12. Remaining Issues
## 13. CLAUDE.md Update
## 14. Final Verdict
```

Includere riepilogo:

```text
Strings reviewed:
Corrections:
P1:
P2:
P3:
Missing keys:
Placeholder issues:
Files modified:
```

## 20. CLAUDE.md

Aggiornare sempre `CLAUDE.md` con:
- MOD-016;
- stato traduzioni polacche;
- eventuali regole terminologiche importanti;
- Known Issues;
- Documentation Map.

Non copiare l'intero elenco delle traduzioni nel `CLAUDE.md`.

## 21. Git

Prima e dopo:

```powershell
git status
git diff
git diff --check
```

Il diff deve contenere esclusivamente modifiche pertinenti.

NON eseguire:

```text
git reset --hard
git clean
git checkout .
git push
git force-push
```

Non creare commit senza autorizzazione.

## 22. Anti-Hallucination

NON inventare traduzioni attese, glossari ufficiali, requisiti linguistici, comportamento UI, chiavi, fallback o architettura.

Ordine:

```text
documentazione
→ codice
→ contesto d'uso
→ evidenza runtime
→ REVIEW REQUIRED
```

Se una traduzione è ambigua e non determinabile, non modificarla arbitrariamente.

## 23. STOP Conditions

Fermarsi se una modifica richiede:
- cambiamenti alla logica non necessari;
- backend/API;
- modifica architetturale;
- comportamento funzionale;
- dati;
- production.

Documentare e STOP.

## 24. Definition of Done

MOD-016 è completo quando:
- sistema di localizzazione identificato;
- locale polacco identificato;
- inventory eseguito;
- errori P1/P2 corretti;
- eventuali P3 documentati;
- key integrity verificata;
- placeholder integrity verificata;
- controlli statici eseguiti;
- build reale eseguito;
- correzioni principali verificate nell'app;
- regression minima eseguita;
- documentazione prodotta;
- `CLAUDE.md` aggiornato.

## 25. Final Output

```text
CLAUDE.md updated: YES/NO

Localization system: IDENTIFIED/BLOCKED
Polish locale: IDENTIFIED/BLOCKED

Strings reviewed: X
Corrections: X
P1: X
P2: X
P3: X
Missing keys: X
Extra keys: X
Placeholder issues: X

Files modified: LIST

Typecheck: PASS/FAIL/N/A
Lint: PASS/FAIL/N/A
Prettier: PASS/FAIL/N/A
Translation consistency: PASS/FAIL/N/A
Android build: PASS/FAIL/BLOCKED
Runtime verification: PASS/FAIL/PARTIAL
Regression: PASS/FAIL/PARTIAL

Backend changes: NONE/LIST
Production changes: NONE/LIST
New dependencies: NONE/LIST
Remaining translation issues: NONE/LIST

Final verdict: PASS/PASS WITH FINDINGS/FAIL
Next step: MOD-017/USER DECISION
```

## 26. Regola finale

Non trasformare MOD-016 in una riscrittura completa delle traduzioni.

L'obiettivo è migliorare concretamente il polacco partendo dagli errori reali e verificabili, mantenendo invariati comportamento e architettura dell'app.

Al termine: **STOP**.
