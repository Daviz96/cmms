# MOD-016 — Polish Translation Audit & Correction

Audit mirato e correzione delle traduzioni **polacche** (`pl`) dell'app mobile Atlas.
**Solo file di localizzazione modificato** (`mobile/i18n/translations/pl.ts`); nessuna modifica
a backend/API/DB/licensing/logica/frontend/production. Nessuna nuova dipendenza. Altre lingue
non toccate. Secret non presenti.

```text
Backend logic changes: NONE
Production changes: NONE
New dependencies: NONE
Files modified:
  mobile/i18n/translations/pl.ts                         (67 correzioni)
  api/src/main/resources/mailMessages_pl_PL.properties   (1 typo, su richiesta esplicita del responsabile; solo risorsa i18n, nessuna logica)
  home/src/i18n/translations/pl.ts                        (follow-up su richiesta: 4 correzioni + 17 chiavi mancanti aggiunte; vedi §15)
```

> **Esito in una riga:** trovati e corretti errori polacchi **reali e verificabili** — 4
> **placeholder rotti** (nome variabile `{{...}}` tradotto), 2 **chiavi mancanti**, e ~61
> traduzioni sbagliate/fuorvianti o incoerenti (es. `save`="Ratować"→**Zapisz**,
> `due_date`="Dwie daty"→**Termin**, `parts`="Strony"→**Części**, `links`="Spinki do
> mankietów"→**Powiązania**, `meters`="Metry"→**Liczniki**). **1331 = 1331 chiavi** (integrità
> ripristinata), tsc/prettier/consistency **clean**. Nessuna riscrittura completa: ~5% delle
> stringhe toccato, mirato agli errori.

---

## 1. Objective

Migliorare concretamente la qualità del polacco partendo da errori reali e verificabili,
mantenendo invariati comportamento e architettura. Ciclo INVENTORY → IDENTIFY → VERIFY CONTEXT
→ CORRECT → STATIC CHECKS → BUILD → RUNTIME → DOCUMENT.

## 2. Localization Architecture

```text
translation library:      i18next + react-i18next
locale files:             mobile/i18n/translations/*.ts  (16 lingue)
Polish locale:            mobile/i18n/translations/pl.ts   (chiave: 'pl')
English/base locale:      mobile/i18n/translations/en.ts   (lingua base)
fallback locale:          en  (i18n.ts: lng:'en', fallbackLng:'en')
loading mechanism:        import statico in i18n.ts → resources.{pl:{translation:plJSON}}
key structure:            flat (keySeparator:false); chiave = identificatore, valore = stringa
interpolation:            {{var}} (i18next), escapeValue:false
```

## 3. Inventory

`Localization system: IDENTIFIED. Polish locale: IDENTIFIED.` `pl.ts` = **1329 chiavi**
iniziali (→ 1331 dopo l'aggiunta delle 2 mancanti). Confronto automatico `pl` vs `en` eseguito
con uno script di consistenza (chiavi, extra, duplicate, placeholder).

## 4. Findings

Riepilogo per categoria (dettaglio nelle correzioni §5). Priorità: P1 = significato sbagliato/
fuorviante o interpolazione rotta; P2 = termine errato/innaturale/incoerenza/typo.

| Categoria | Esempi | Priorità |
|---|---|---|
| PLACEHOLDER_MISMATCH (interpolazione rotta) | `{{daty}}`→`{{date}}`, `{{duplikaty}}`→`{{duplicates}}`, `{{dni}}`→`{{days}}`, `{{priorytet}}`→`{{priority}}` | **P1** |
| MISSING_TRANSLATION | `you_need_a_license`, `id_required` (assenti → fallback EN) | P2 |
| WRONG_MEANING | `save`="Ratować"(salvare una vita), `due_date`="Dwie daty"(due date→"due"≈"dwa"), `asset`="Zaleta"(pregio), `parts`="Strony"(pagine), `links`="Spinki do mankietów"(gemelli da polso), `minutes`="Protokół", `meters`/`meter`="Metry"/"Metr"(unità di lunghezza), `pending`/`PENDING`="Aż do"(fino a), `down`/`DOWN`="W dół"(in giù), `requests`="Upraszanie", `the_numbers`="Księga Liczb"(libro biblico), `compliant`="Uległy"(remissivo), `concerned_asset`="Zaniepokojony…"(asset ansioso), `collapse`="Zawalić się"(crollare), `to_scan`/`scanning`="Skandować"/"Łów"(scandire/caccia), `clear`="Jasne", `downgrade`="Nachylenie"(pendenza), `home`="Dom"(casa-edificio), `NONE`/`none_priority`="Nic" | **P1** |
| UNTRANSLATED | `create_category`="Create Category" (inglese nel file pl) | P1 |
| WRONG_TERM / UNNATURAL | `labors`="Trud", `details`="Bliższe dane", `preview`="Zapowiedź", `count`="Liczyć", `expand`="Zwiększać", `checkout`="Wymeldować się", `PASS`/`FAIL`="Przechodzić"/"Ponieść porażkę", `select`="Wybierać...", `allow`="Umożliwić", `no`="NIE", `*_children`="…dzieci"(bambini) | P2 |
| INCONSISTENCY (bottoni: infinito → imperativo) | `add`="Dodać"→Dodaj, `edit`="Redagować"→Edytuj, `cancel`="Anulować"→Anuluj, `close`, `submit`, `reject`, `approve`, `to_delete`, `go_back`, `upload`, `show`, `rename`, `link`, `create`, `hide`, `print`, `sort` (già usato imperativo nei composti es. "Dodaj część") | P2 |

Nessun caso ambiguo è stato modificato arbitrariamente (vedi §12 REVIEW REQUIRED).

## 5. Corrections

Correzioni principali (Before → After). **67 stringhe** modificate/aggiunte in totale.

### 5.1 Placeholder integrity (P1)

| Key | Before | After |
|---|---|---|
| due_at_date | `Dwie {{daty}}` | `Termin {{date}}` |
| there_are_duplicates | `Istnieją duplikaty: {{duplikaty}}` | `Istnieją duplikaty: {{duplicates}}` |
| days_count | `{{dni}} dni` | `{{days}} dni` |
| priority_label | `{{priorytet}} priorytet` | `{{priority}} priorytet` |

### 5.2 Wrong meaning / untranslated (P1)

| Key | Before | After | Context |
|---|---|---|---|
| save | Ratować | **Zapisz** | azione UI Salva |
| due_date | Dwie daty | **Termin** | data di scadenza WO |
| asset | Zaleta | **Zasób** | asset (coerente con "Aktywa") |
| parts | Strony | **Części** | ricambi |
| links | Spinki do mankietów | **Powiązania** | relazioni WO |
| minutes | Protokół | **Minuty** | unità di durata |
| meters / meter | Metry / Metr | **Liczniki / Licznik** | dispositivi contatore |
| pending / PENDING | Aż do | **Oczekujące** | stato richiesta |
| down / DOWN | W dół | **Niesprawny** | stato asset "Down" |
| requests | Upraszanie | **Zgłoszenia** | nav richieste |
| register | Rejestr | **Zarejestruj się** | azione registrazione |
| request_details | Poproś o szczegóły | **Szczegóły zgłoszenia** | dettagli richiesta |
| BLOCKS | Bloki | **Blokuje** | relazione WO (verbo) |
| the_numbers | Księga Liczb | **Liczby** | sezione analytics |
| compliant | Uległy | **Zgodne** | WO conformi |
| concerned_asset | Zaniepokojony składnik aktywów | **Zasób, którego dotyczy** | asset interessato |
| collapse | Zawalić się | **Zwiń** | comprimi |
| to_scan / scanning | Skandować / Łów | **Skanuj / Skanowanie** | scansione NFC/barcode |
| clear | Jasne | **Wyczyść** | pulisci filtri |
| downgrade | Nachylenie | **Obniż plan** | downgrade abbonamento |
| home | Dom | **Start** | tab Home |
| none_priority / NONE | Nic | **Brak** | priorità nessuna |
| create_category | Create Category | **Utwórz kategorię** | non tradotto |

### 5.3 Missing keys added (P2)

| Key | EN | Added PL |
|---|---|---|
| you_need_a_license | You need a license to access this feature | Aby uzyskać dostęp do tej funkcji, potrzebujesz licencji |
| id_required | ID is required | Identyfikator jest wymagany |

### 5.4 Wrong term / unnatural / inconsistency (P2)

- `labors`→**Robocizna**, `details`→**Szczegóły**, `preview`→**Podgląd**, `count`→**Liczba**,
  `expand`→**Rozwiń**, `checkout`→**Płatność**, `PASS`→**Zaliczono**, `FAIL`→**Niezaliczono**,
  `FLAG`→**Oznaczone**, `select`→**Wybierz...**, `allow`→**Zezwól**, `no`→**Nie**,
  `see_children`/`hide_children`/`view_children`→**…podrzędne**, `SPLIT_TO`→**Podzielone na**.
- Bottoni infinito→imperativo: `add`→**Dodaj**, `cancel`→**Anuluj**, `close`→**Zamknij**,
  `submit`→**Wyślij**, `edit`→**Edytuj**, `reject`→**Odrzuć**, `approve`→**Zatwierdź**,
  `to_delete`→**Usuń**, `go_back`→**Wróć**, `upload`→**Prześlij**, `show`→**Pokaż**,
  `rename`→**Zmień nazwę**, `link`→**Połącz**, `create`→**Utwórz**, `hide`→**Ukryj**,
  `print`→**Drukuj**, `sort`→**Sortuj**.

## 6. Terminology

Glossario minimo dei termini UI ricorrenti applicato per coerenza:

```text
English    | Polish       | Context / Decision
Save       | Zapisz       | azione (non "Ratować"=salvare vita)
Delete     | Usuń         | azione imperativa
Edit       | Edytuj       | azione (non "Redagować")
Cancel     | Anuluj       | azione
Asset      | Zasób        | singolare (plur. "Aktywa")
Part(s)    | Część/Części | ricambi
Meter(s)   | Licznik/Liczniki | dispositivo (non "Metr")
Down       | Niesprawny   | stato asset non operativo
Pending    | Oczekujące   | stato
Requests   | Zgłoszenia   | nav
Scan       | Skanuj       | azione fotocamera/NFC
```

## 7. Key/Placeholder Integrity

`Missing keys: 0  Extra keys: 0  Duplicate keys: 0  Placeholder issues: 0` (dopo le correzioni).

```text
EN keys: 1331  |  PL keys: 1331   (prima: 1329)
Missing in PL: 0   (aggiunte you_need_a_license, id_required)
Extra in PL: 0     Duplicate: 0
Placeholder mismatches: 0  (corretti due_at_date, there_are_duplicates, days_count, priority_label)
```

Verifica con script one-off (`scratchpad/i18ncheck.js`) — nessuna nuova dipendenza, non
committato.

## 8. Static Checks

| Check | Esito |
|---|---|
| `tsc --noEmit` (pl.ts) | **NO type errors** |
| `prettier --check pl.ts` | **All matched files use Prettier code style** |
| Translation consistency (pl vs en) | **PASS** (0 missing/extra/duplicate/placeholder) |
| `git diff --check` | **clean** (nessun whitespace error) |

Diff: **solo `mobile/i18n/translations/pl.ts`** (74 ins / 68 del).

## 9. Build

`Android build: PASS.` **Release** reale (le stringhe i18n sono compilate nel bundle JS via
Hermes → il fix è verificabile senza Metro).

```text
Command : mobile/android/gradlew.bat assembleRelease --no-daemon --console=plain
Esito   : BUILD SUCCESSFUL in 4m 3s (incrementale; solo JS bundle rigenerato)
Artifact: app/build/outputs/apk/release/app-release.apk  (~100 MB, gitignored)
```

## 10. Runtime Verification

`Runtime verification: PASS.` APK release installato sull'AVD `atlas_test`; creato un account
di test con **lingua company = PL** (`AuthContext` → `changeLanguage('pl')` al login) e
verificate le correzioni **realmente renderizzate in polacco** (UIAutomator + screenshot):

| Correzione | Prima | Dopo (a schermo) | Dove |
|---|---|---|---|
| home | Dom | **Start** | tab in basso |
| meters | Metry | **Liczniki** | menu Więcej |
| requests | Upraszanie | **Zgłoszenia** | tab in basso |
| cancel | Anulować | **Anuluj** | dialog logout |
| (assets/parts) | — | **Aktywa / Części** | menu Więcej |

**Layout OK** (screenshot `M16-more-PL.png`/`M16-dashboard-PL.png`/`M16-settings-PL.png`):
nessun troncamento/overlap, testi polacchi (anche più lunghi es. "Dostawcy i wykonawcy",
"Żaden element nie spełnia tego kryterium") entro i limiti. Nessun crash, nessun toast di
errore (il fix M-BUG-1 di MOD-015 è incluso nello stesso build).

## 11. Regression

`Regression: PASS.` Flusso minimo **interamente in polacco**:

```text
Launch          → app avviata, Login screen pulito
Custom Server   → http://10.0.2.2:3000/api salvato
Login (PL acct) → dashboard "Atlas": Otwarte/Wstrzymany/W toku/Kompletny/Dzisiaj/Wysoki priorytet
Work Orders     → "Zlecenia robocze | Moja praca | Priorytet | Status | Żaden element nie spełnia tego kryterium"
Assets          → "Aktywa | Szukaj"  (menu Więcej: Lokalizacje/Aktywa/Części/Liczniki/Ludzie i zespoły/Dostawcy i wykonawcy)
Settings        → "Ustawienia | pltest@example.com | Aktualizuj profil | … | Version 1.0.47"
Logout          → dialog "Potwierdzenie / Czy na pewno chcesz się wylogować? / Anuluj" → torna a Login (PL: "Zaloguj się")
```

Autenticazione, sessione, navigazione, dati e logout tutti funzionanti in `pl`.

## 12. Remaining Issues

Casi **ambigui non modificati** (REVIEW REQUIRED — non inventati):

- `customers`="Wykonawcy" (customer→contractor): mapping di dominio **coerente** in tutto il
  file → lasciato invariato (non è un errore).
- `state`="Państwo" (indirizzo): "State" potrebbe essere provincia/paese → ambiguo, non
  modificato.
- `REPEATING`="Zapobiegawczy" (repeating→preventive): possibile mappatura di dominio → lasciato.
- `audio_description`="Audiodeskrypcja": contesto (nota audio vs audiodescrizione) non
  determinabile → lasciato.
- `COMPLETE`/`complete`="Kompletny" (stato WO): "Zakończone/Ukończone" sarebbe più naturale ma
  "Kompletny" è difendibile → lasciato (P3).

**Backend email locale — `mailMessages_pl_PL.properties` (verifica richiesta dal responsabile):**
confronto vs base `mailMessages.properties` → chiavi e placeholder (`{0}`, `<br>`) tutti presenti/
integri. **1 errore chiaro corretto:** `BLOCKS=bloki` (sost. "blocchi") → **`blokuje`** (verbo,
coerente con `DUPLICATE_OF=jest duplikatem`, `RELATED_TO=jest powiązane z`). Elementi **REVIEW**
lasciati invariati (borderline/di dominio): `asset=Maszyna` (vs generico "Zasób" — incoerente col
mobile ma difendibile per manutenzione), `SPLIT_FROM=jest częścią` (vs "splits from"),
`feedback=Feedback`, `labors=Praca`, `tasks=Podzadania`. *(Nota: file backend/risorsa; modifica solo
di traduzione, nessuna logica; richiede rebuild backend per avere effetto — non eseguito.)*

**Systemic (REVIEW/DEFERRED, tutte le lingue):** alcune label UI usano **chiavi-letterali inglesi**
via `t('Sign out')` / `t('Version')` **assenti da tutti i file di localizzazione** (né en né pl) →
i18next ripiega sulla chiave stessa (inglese) in **ogni** lingua (visibile: "Sign out"/"Version" in
UI polacca). Non è un errore di `pl` (manca anche nella base): richiederebbe aggiungere le chiavi a
`en.ts` + tutte le locale → fuori scope di un fix pl-only. Consigliato follow-up separato.

Nessun altro problema di traduzione P1/P2 residuo noto in `pl`. Altre lingue fuori scope.

## 12b. Addendum — Home App (`home/`) Polish Locale (follow-up su richiesta)

Su richiesta del responsabile è stato verificato anche `home/src/i18n/translations/pl.ts` — app
**Next.js/next-intl** separata (landing/marketing), chiavi **nested** + placeholder single-brace
`{var}`. **Qualità complessiva: alta** (a differenza del mobile, la maggior parte dei termini è
corretta: `save`=Zapisz, `parts`=Części, `links`=Linki, `meters`=Liczniki, `due_date`=Termin,
`asset`=Zasób, ecc.). Verifica: integrity script adattato (`i18ncheck_home.js`) + eval-parse.

Correzioni applicate (**solo dato i18n; verifica statica, no runtime come da richiesta**):

| Key | Prima | Dopo | Tipo |
|---|---|---|---|
| `timers` | Liczniki (contatori, collisione con `meters`) | **Timery** | WRONG_TERM |
| `pricing_1.plan_starter_name` | Rozrusznik (rozrusznik silnika!) | **Startowy** | WRONG_MEANING |
| `create_role_description` | placeholder `{shortBrandName}` | **`{brandName}`** | PLACEHOLDER (match base) |
| `pricing_1.sh_plan_professional_description` | "uprawy roślin" (coltivazione piante) | **"rozwijających się zakładów"** | WRONG_MEANING (EN "growing plants" = impianti industriali, non botanici) |

**17 chiavi mancanti aggiunte** (prima in fallback inglese): `payment_success_title/description`,
`demo_warning`, `delete_demo_data`, `import_pm_success` (`{created}`/`{updated}` preservati),
`recurrence_type`, `recurrence_based_on`, `days_of_week`, `no_recent_work_orders`,
`recent_work_orders`, `subscription_will_cancel_on` (`{date}`), `open_api_docs`, `delete_account`,
`Advantages`, `installation_docs`, `free_cmms.short`, `SSO`.

Integrità post-fix: **EN 1489 / PL 1509**, **Missing 0**, **Placeholder mismatches 0**, Prettier
clean, file `parses OK`. **Non un bug:** `CANCELLED`="Odrzucone" **corrisponde** alla base EN
(`CANCELLED: "Rejected"`) → lasciato. **Lasciate invariate:** 20 chiavi *extra* in PL non presenti
in EN (relazioni `BLOCKS`/`OPEN`/`link_wo`… — inutilizzate/fallback; §13 vieta di eliminare senza
verifica d'uso). Diff: `home/src/i18n/translations/pl.ts` (+23/-4). Nessun altro file toccato.

## 13. CLAUDE.md Update

```text
CLAUDE.md updated: YES
Reason: aggiunto MOD-016 (Polish Translation Audit) a Current focus + Current Project State +
Documentation Workflow/Map; registrato lo stato traduzioni PL (67 correzioni, integrità 1331=1331,
placeholder ripristinati) e la regola terminologica UI (bottoni imperativi, Save=Zapisz).
Nessun dettaglio elenco completo copiato.
```

## 14. Final Verdict

```text
CLAUDE.md updated: YES
Localization system: IDENTIFIED   Polish locale: IDENTIFIED
Strings reviewed: 1331
Corrections: 67 (mobile pl.ts) + 1 (backend email locale) + 4 fix & 17 chiavi (home app pl.ts, §12b) — su richiesta
P1: 32   P2: 35   P3: 0 (REVIEW documentati)
Missing keys: 2 (added)   Extra keys: 0   Placeholder issues: 4 (fixed)
Files modified: mobile/i18n/translations/pl.ts ; api/.../mailMessages_pl_PL.properties (1 typo) ; home/src/i18n/translations/pl.ts (4 fix + 17 chiavi, §15)
Typecheck: PASS   Lint: N/A (nessuno script lint)   Prettier: PASS   Translation consistency: PASS
Android build: PASS (release 4m3s)   Runtime verification: PASS (PL reso)   Regression: PASS (PL)
Backend logic changes: NONE   Production changes: NONE   New dependencies: NONE
Remaining translation issues: REVIEW documentati (asset=Maszyna; 'Sign out'/'Version' literal keys)
Final verdict: PASS
Next step: USER DECISION (altre lingue / altri file locale / MOD-017)
```

**PASS.** Le traduzioni polacche del mobile sono state migliorate concretamente partendo da
errori reali e verificabili (67 correzioni mirate, ~5% delle stringhe), integrità chiavi/
placeholder ripristinata (1331=1331, 0 placeholder rotti), verificate a **runtime in polacco** su
un **release build reale** con regression completa e layout integro. Comportamento e architettura
invariati; nessuna modifica a backend/logica/production. Su richiesta è stato corretto anche 1
typo nel locale email backend (`BLOCKS`) e documentati gli elementi REVIEW.

⏹️ **STOP** — non avvio MOD-017 né altre attività. Le altre lingue e i restanti file locale
(inclusi gli elementi REVIEW e il gap sistemico `Sign out`/`Version`) sono a decisione del
responsabile.
