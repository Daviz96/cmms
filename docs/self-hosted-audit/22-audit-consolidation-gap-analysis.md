# AUDIT CONSOLIDATION — Gap Analysis e pianificazione dei prossimi MOD

## Ruolo
Sei il coding agent incaricato dell’analisi tecnica del progetto Atlas CMMS self-hosted.

In questa fase **NON devi modificare codice applicativo, configurazione Docker o infrastruttura**. Devi produrre una fotografia consolidata e aggiornata dello stato dell’audit e identificare con precisione problemi, finding e restrizioni ancora aperti. La decisione sul prossimo MOD verrà presa dal responsabile tecnico dopo la revisione del report.

## 1. Obiettivo
Rispondere con evidenza documentale e dal codice a:
1. quali MOD sono completati;
2. quali sono verificati;
3. quali finding sono accettati;
4. quali problemi restano aperti;
5. quali funzionalità sono limitate;
6. quali limitazioni dipendono realmente dal licensing;
7. quali funzionalità sono tecnicamente implementabili;
8. quali aspetti non sono verificati;
9. quali sono le priorità;
10. quale singolo MOD è consigliabile affrontare successivamente.

**Non implementare nulla in questa fase.**

## 2. Fonti e priorità
Usa, in quest’ordine:
1. `CLAUDE.md`;
2. audit più recente;
3. verification;
4. implementation;
5. architecture/design/decisions;
6. requirements;
7. codice;
8. test.

Le informazioni più recenti prevalgono sulle obsolete.

Per MOD-004B/004C considera consolidato:
- MOD-004B: **PASS WITH FINDINGS**;
- MOD-004C: **PASS**;
- VF-01: Low, accettato;
- VF-02: Info, accettato;
- O-01: informativo, nessuna azione;
- targeted: 6/6 PASS;
- suite: 1445/1445 PASS;
- MOD-004C non ha modificato il codice;
- `21-mod004c-e2e-verification.md` è fonte primaria per storage/MinIO/nginx runtime.

## 3. Matrice MOD
Individua tutti i MOD realmente presenti nella documentazione. Per ciascuno:
- ID;
- obiettivo;
- stato;
- implementazione;
- verifica;
- test;
- finding;
- rischio residuo;
- dipendenze;
- attività ancora necessarie.

Usa solo stati supportati:
`PASS`, `PASS WITH FINDINGS`, `IN PROGRESS`, `PARTIAL`, `NOT VERIFIED`, `OPEN`, `BLOCKED`.

Crea una tabella:
| MOD | Obiettivo | Stato | Test | Verification | Findings | Rischio residuo |
|---|---|---|---|---|---|---|

## 4. Findings
Consolida tutti i finding rilevanti:
- Critical / High / Medium / Low / Info.

Per ciascuno:
```text
ID
Severity
Componente
Descrizione
Stato
Impatto
Decisione già presa
Azione futura
```

Distingui `Accepted`, `Open`, `Resolved`, `Informational`.
Non trasformare automaticamente Low/Info in un nuovo MOD.

## 5. Licensing audit
Analizza il codice per:
- licensing checks;
- feature flags;
- entitlement/subscription checks;
- cloud/self-hosted checks;
- limiti artificiali;
- controlli backend/frontend;
- endpoint che negano funzionalità;
- controlli duplicati frontend/backend.

Per ogni restrizione:
```text
Feature
Dove viene bloccata
Backend / Frontend / Entrambi
Condizione
Tipo di controllo
Comportamento con licenza attuale
Funzionalità desiderata
Possibilità tecnica
Dipendenze
Rischi
```

## 6. Classificazione delle restrizioni
Classifica ogni feature come:
- **A — Solo UI restriction**
- **B — Backend licensing restriction**
- **C — Frontend + backend**
- **D — Feature realmente assente**
- **E — Dipendenza esterna**
- **F — Non determinato**

Per ogni feature determina, se possibile:
1. entry point;
2. endpoint;
3. controller/service;
4. modello dati;
5. codice backend;
6. codice frontend;
7. punto del licensing check;
8. necessità reale del controllo;
9. modifiche necessarie;
10. test;
11. rischi.

Per `F` dichiarare esplicitamente `UNKNOWN / DA VERIFICARE`.

## 7. Impatto architetturale
Classifica ogni possibile intervento:
- Local change;
- Cross-layer change;
- Data model change;
- Architecture change;
- Infrastructure change;
- Security-sensitive.

Non proporre ancora implementazioni dettagliate.

## 8. Sicurezza e multi-tenancy
Verifica che le potenziali future modifiche non implichino bypass di:
- tenant isolation;
- authorization;
- organization/company boundaries;
- role/ownership checks;
- audit logging;
- file access controls;
- licensing enforcement.

Se trovi un possibile bypass, registralo come finding e non modificarlo.

## 9. Test e verification
Per ogni MOD indica:
- unit;
- integration;
- runtime;
- manuale;
- baseline;
- gap.

Per MOD-004C riportare:
```text
Targeted: 6/6 PASS
Full suite: 1445/1445 PASS
Runtime storage/proxy: PASS
Browser reale: non verificato
Full FileController → FileService live: non verificato
```

Non trasformare un gap dichiarato in failure se lo scope era sufficiente.

## 10. Contraddizioni
Cerca conflitti tra documentazione vecchia, documentazione nuova, codice, test e decisioni.
Se trovi un conflitto:
```text
Vecchia informazione
Nuova informazione
Fonte più recente
Decisione da considerare attuale
```
La fonte più recente prevale, salvo evidenza contraria nel codice.

## 11. Known issues
Raccogli solo problemi supportati, separandoli in:
- Security;
- Licensing;
- Functional;
- Reliability;
- Infrastructure;
- Documentation;
- Testing.

Non includere TODO generici o migliorie cosmetiche.

## 12. Priorità
Usa:
- **P0 Critical**
- **P1 High**
- **P2 Medium**
- **P3 Low**

Per ogni gap:
```text
Priorità
Problema/feature
Motivazione
Impatto utente
Impatto tecnico
Rischio
Dipendenze
MOD candidato
```

## 13. Recommended Next MOD
Indica **una sola raccomandazione principale**.
Motivala con:
- problema risolto;
- priorità;
- componenti coinvolti;
- rischi;
- documenti da leggere prima dell’implementazione.

Puoi indicare massimo due alternative, senza trasformare il report in un catalogo di idee. La decisione finale resta al responsabile tecnico.

## 14. Nessuna modifica
Sono consentiti solo:
- lettura;
- ricerca;
- analisi;
- esecuzione di test già esistenti, senza modificarli;
- produzione del report.

NON modificare:
- codice;
- migration;
- database;
- Docker Compose/Dockerfile;
- nginx;
- MinIO;
- configurazioni;
- licensing;
- frontend/backend.

## 15. Context management
Non analizzare l’intero repository automaticamente.

Procedura:
1. leggere `CLAUDE.md`;
2. individuare `docs/`;
3. cercare audit/verification/implementation/decision;
4. costruire una mappa documentale;
5. leggere prima le fonti più recenti;
6. individuare i componenti direttamente correlati;
7. leggere solo il codice necessario;
8. evitare file non correlati.

Non rileggere inutilmente documenti già acquisiti. Non esplorare in profondità MOD non correlati.

## 16. Anti-hallucination
**NON inventare** requisiti, API, modelli, licensing rules, architetture, decisioni, dipendenze o comportamenti non documentati.

Quando manca un’informazione:
1. cercarla nella documentazione;
2. nel codice;
3. nei test;
4. se ancora assente → `UNKNOWN / DA VERIFICARE`.

## 17. Output
Creare:
```text
docs/self-hosted-audit/22-audit-consolidation.md
```

Struttura:
```text
1. Executive Summary
2. Audit Scope
3. Documentation Sources
4. MOD Status Matrix
5. Findings Consolidation
6. Licensing Audit
7. Feature Restriction Classification
8. Architecture Impact
9. Security / Multi-tenancy Review
10. Test & Verification Status
11. Contradictions / Obsolete Information
12. Known Issues
13. Prioritized Gaps
14. Recommended Next MOD
15. Open Questions
16. Conclusion
```

L’Executive Summary deve essere breve e consentire di capire in meno di una pagina:
- cosa è completato;
- cosa è verificato;
- cosa resta;
- quali restrizioni licensing sono state trovate;
- quale prossimo MOD è raccomandato.

## 18. Definition of Done
L’attività è completa quando:
- tutti i MOD documentati sono classificati;
- finding consolidati;
- licensing restrictions mappate;
- feature classificate A-F;
- gap di verifica documentati;
- contraddizioni evidenziate;
- priorità definite;
- una sola raccomandazione principale formulata;
- nessun codice/configurazione modificato;
- `22-audit-consolidation.md` completo;
- repository invariato funzionalmente.

## 19. STOP CONDITION
Al termine:
**STOP.**

Non creare MOD-005.
Non iniziare implementazioni.
Non modificare licensing.
Non applicare fix.

Restituire:
1. sintesi del lavoro;
2. percorso del report;
3. informazioni non determinabili;
4. contraddizioni;
5. Recommended Next MOD.

La decisione sull’implementazione successiva verrà presa dal responsabile tecnico dopo l’analisi del report.
