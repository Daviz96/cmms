# Atlas CMMS — Audit e sblocco funzionalità per installazione self-hosted

## Obiettivo

Questo repository è un fork locale di Atlas CMMS (`Grashjs/cmms`) destinato a una installazione **self-hosted interna aziendale**.

Il compito è eseguire prima un **audit tecnico completo** del codice sorgente e solo successivamente proporre le modifiche necessarie per rendere disponibili, nella nostra build self-hosted, le funzionalità che risultano bloccate da entitlement/licensing.

**NON modificare inizialmente il codice.** La prima fase deve essere esclusivamente analisi e documentazione.

---

## 1. Cosa vogliamo ottenere

Determinare, per ogni funzionalità premium/licensed:

1. dove è definita;
2. dove viene controllata;
3. se il controllo è backend;
4. se esiste anche un controllo frontend;
5. quali controller/API sono coinvolti;
6. quali service sono coinvolti;
7. quali model/entity/database sono coinvolti;
8. se la funzionalità è realmente implementata;
9. se il blocco è esclusivamente commerciale;
10. quali modifiche servirebbero nella build self-hosted;
11. quali test esistono e quali devono essere aggiunti.

Non assumere che un entitlement significhi automaticamente che la feature sia implementata: seguire il flusso completo del codice.

---

## 2. Funzionalità prioritarie

### Core CMMS

- Unlimited Assets
- Unlimited Users
- Unlimited Locations
- Asset Hierarchy
- Custom Roles
- Custom Permissions
- Unlimited Work Orders
- Work Order History
- File Attachments
- Checklists
- Parts
- Unlimited Parts

### Manutenzione

- PM Schedules
- Unlimited PM Schedules
- Meters
- Unlimited Meters
- Meter-based preventive maintenance
- Condition-based PM
- Asset Downtime
- Work Order Linking
- Labor / Time Tracking
- Cost Tracking

### Organizzazione aziendale

- Customers
- Vendors
- Request Portal
- Notifications
- Email Notifications
- API
- Webhooks
- Workflow

### Autenticazione

- LDAP
- LDAP synchronization
- Active Directory tramite LDAP
- SSO / OAuth2

Queste feature devono ricevere priorità alta nell'audit.

---

## 3. Limiti quantitativi

Individuare tutti i controlli relativi ai limiti numerici, in particolare:

- Assets
- Locations
- Parts
- PM schedules
- Active Work Orders
- Checklists
- Meters
- Users

Per ogni limite documentare:

```text
limite
↓
metodo che lo verifica
↓
LicenseEntitlement richiesto
↓
controller/service
↓
eventuale controllo frontend
```

Non limitarsi a cercare il numero: seguire il flusso completo.

---

## 4. LicenseEntitlement

Analizzare completamente `LicenseEntitlement` e costruire un inventario di **tutti** gli entitlement.

Per ogni entitlement:

```text
ENTITLEMENT
├── significato
├── backend usage
├── frontend usage
├── controller
├── service
├── repository/model
├── configurazione
├── test
├── dipendenze esterne
└── classificazione
```

Usare queste categorie:

### 🟢 UNLOCK_SIMPLE
La feature è già completamente implementata e il blocco è sostanzialmente un controllo entitlement/licenza.

### 🟡 UNLOCK_PLUS_MODIFICATION
La feature esiste ma richiede piccole modifiche backend/frontend oltre al gate.

### 🟠 SIGNIFICANT_MODIFICATION
La feature è parzialmente implementata oppure dipende da componenti commerciali/servizi esterni.

### 🔴 NOT_IMPLEMENTED
La feature non è realmente implementata e non può essere ottenuta semplicemente modificando il licensing.

### ⚪ ALREADY_AVAILABLE
La feature è già disponibile senza interventi.

---

## 5. Non fare un bypass globale

È espressamente vietato iniziare con modifiche del tipo:

```java
return true;
```

dentro `hasEntitlement()` o funzioni equivalenti.

Non vogliamo:

- distruggere il sistema di licensing;
- introdurre comportamenti imprevedibili;
- modificare accidentalmente feature non correlate;
- nascondere dipendenze commerciali;
- creare una build difficile da mantenere.

Prima comprendere l'architettura.

Se l'audit dimostra che una modalità centralizzata `self-hosted` è appropriata, proporla come modifica separata.

---

## 6. Backend e frontend

L'audit deve comprendere entrambi.

Per ogni feature verificare:

```text
Frontend
   ↓
feature flag / entitlement
   ↓
API
   ↓
Controller
   ↓
Service
   ↓
LicenseService / entitlement
   ↓
Database
```

Rimuovere un gate backend potrebbe non bastare se il frontend:

- nasconde il menu;
- disabilita un pulsante;
- impedisce la creazione;
- non visualizza una pagina;
- non carica una sezione.

Al contrario, non modificare il frontend se il controllo è esclusivamente server-side e non necessario.

---

## 7. Servizi esterni

Per ogni feature distinguere tra:

### Funzione locale
Esempi:

- Asset hierarchy
- Custom roles
- Work order history

### Funzione con dipendenze esterne
Possibili esempi:

- OAuth2
- SMTP
- Google Cloud
- SendGrid
- Keygen
- Paddle

Non assumere che rimuovere l'entitlement sia sufficiente: verificare il flusso reale.

---

## 8. LDAP / Active Directory

Analizzare completamente il supporto LDAP.

Individuare:

- LDAP client
- LDAP authentication
- LDAP user search
- LDAP synchronization
- user creation
- user update
- user disable
- role mapping
- OU mapping
- attribute mapping
- scheduled synchronization
- configurazione
- entitlement/licensing
- frontend configuration

L'obiettivo futuro è poter collegare Atlas all'Active Directory aziendale.

Non implementare ancora la configurazione AD: documentare prima ciò che esiste.

---

## 9. Work Order History

Verificare:

- audit repository
- revisioni
- storico
- timestamp
- utente
- diff
- endpoint/API
- frontend
- entitlement

Determinare se è una funzione completa semplicemente protetta da licenza.

---

## 10. Asset management

Analizzare:

- Asset CRUD
- Asset hierarchy
- parent/child
- Locations
- Asset downtime
- meters
- attachments
- parts
- work orders collegati
- preventive maintenance collegata agli asset

La struttura futura deve poter rappresentare, ad esempio:

```text
Stabilimento
└── Reparto
    └── Macchina
        ├── Motore
        ├── Pompa
        ├── Quadro elettrico
        └── Componenti
```

---

## 11. Preventive Maintenance

Analizzare:

- PM schedule
- calendar
- recurrence
- meter trigger
- condition trigger
- automatic work order creation
- checklist
- asset association
- parts
- notifications

Determinare quali funzioni sono realmente implementate e quali sono protette da entitlement.

---

## 12. Allegati e storage

Verificare completamente:

```text
upload
↓
backend
↓
storage
↓
MinIO / filesystem
↓
database metadata
↓
download
↓
frontend
```

Nel nostro deployment Atlas utilizza MinIO.

Verificare inoltre:

- dimensioni massime;
- tipi file;
- autenticazione;
- autorizzazioni;
- cancellazione;
- associazione con asset/work order;
- entitlement `FILE_ATTACHMENTS`.

---

## 13. Database

Non modificare lo schema durante l'audit.

Individuare:

- PostgreSQL entities;
- migrations;
- MinIO usage;
- filesystem usage;
- dati persistenti;
- tabelle coinvolte dalle feature premium.

---

## 14. Test

Per ogni feature sbloccabile verificare i test esistenti.

Se non esistono, indicare quali dovrebbero essere creati.

Esempi:

```text
Asset limit
    test <= limit
    test > limit
    test unlimited entitlement

Custom role
    test create
    test permission
    test unauthorized access

Work order history
    test history enabled
    test history disabled
    test audit integrity
```

Durante l'audit non creare centinaia di test automaticamente: produrre prima il piano.

---

## 15. Output richiesto

Creare nella root del repository:

```text
docs/self-hosted-audit/
```

con almeno:

```text
00-executive-summary.md
01-license-entitlements.md
02-backend-feature-gates.md
03-frontend-feature-gates.md
04-feature-matrix.md
05-ldap-ad.md
06-storage-attachments.md
07-maintenance-pm.md
08-work-orders.md
09-asset-management.md
10-security-considerations.md
11-modification-plan.md
12-test-plan.md
```

---

## 16. Feature matrix

`04-feature-matrix.md` deve contenere:

| Feature | Entitlement | Backend | Frontend | DB/Model | External dependency | Classification | Priority | Files |
|---|---|---|---|---|---|---|---|---|

Quando possibile indicare:

```text
file
classe
metodo
riga
```

Evitare valori vaghi come "probably".

---

## 17. Modification plan

`11-modification-plan.md` deve proporre un piano concreto.

Per ogni modifica:

```text
ID
Feature
Files to modify
Current behavior
Desired behavior
Risk
Dependencies
Tests required
Rollback strategy
```

Esempio:

```text
MOD-001
Feature: Unlimited Assets

Current:
AssetService checks UNLIMITED_ASSETS.

Desired:
Self-hosted policy permits unlimited assets.

Files:
...

Risk:
Low

Tests:
...
```

---

## 18. Regole fondamentali

1. NON modificare il codice durante la prima fase di audit.
2. NON eliminare il sistema di licensing alla cieca.
3. NON assumere che una feature esista solo perché esiste un entitlement.
4. NON assumere che una feature sia assente solo perché il frontend la nasconde.
5. Seguire frontend → API → controller → service → repository/model.
6. Cercare tutte le occorrenze dell'entitlement.
7. Cercare anche i controlli indiretti.
8. Verificare test e migration.
9. Distinguere codice open source da dipendenze commerciali/esterne.
10. Non modificare Docker/production deployment durante l'audit.
11. Non modificare credenziali o secret.
12. Non committare secret.
13. Non rimuovere Keygen/Paddle/altre integrazioni finché non è chiaro il loro ruolo.
14. Prima analizzare, poi proporre, poi modificare.

---

## 19. Seconda fase: implementazione

Dopo aver completato l'audit e prodotto i documenti, **fermarsi**.

Non iniziare automaticamente le modifiche.

Mostrare un riepilogo:

```text
Features fully implemented:
...

Features unlockable:
...

Features requiring modifications:
...

Features not implemented:
...

Recommended implementation order:
...
```

e attendere istruzioni.

---

## 20. Obiettivo finale

Il risultato finale desiderato è una build Atlas CMMS self-hosted adatta all'infrastruttura aziendale:

```text
Atlas CMMS
    |
    +-- Assets illimitati
    +-- Locations
    +-- Asset hierarchy
    +-- Work Orders
    +-- Preventive Maintenance
    +-- Meters
    +-- Checklists
    +-- Parts
    +-- Attachments
    +-- Work Order History
    +-- Labor
    +-- Costs
    +-- Downtime
    +-- Custom Roles
    +-- LDAP / Active Directory
    +-- Email notifications
    +-- API
    +-- Webhooks
    |
    +-- PostgreSQL
    +-- MinIO
    +-- Docker
    |
    +-- Caddy / reverse proxy
```

La priorità è ottenere una soluzione **pulita, prevedibile, testabile e aggiornabile**, non semplicemente far sparire i controlli della licenza.

## 21. Nota legale e di manutenzione

Prima di distribuire la build modificata al di fuori dell'ambiente interno, verificare gli obblighi applicabili alla licenza del repository e alle eventuali componenti commerciali.

Mantenere inoltre le modifiche in commit separati e documentati, in modo da poter sincronizzare in futuro gli aggiornamenti upstream senza perdere le personalizzazioni self-hosted.
