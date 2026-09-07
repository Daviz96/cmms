# Feature proposals — sviluppi futuri

> Cartella dove si conservano i **documenti di design/proposta** per **nuove funzionalità** non ancora implementate.
> Ogni file è una proposta autonoma: obiettivo, cosa esiste già nel codice, gap, approccio consigliato + alternative,
> invasività/rischi, piano di implementazione, test di accettazione, decisioni aperte.
>
> Non sono changelog né stato di produzione: per quello vedi [../PROJECT-STATUS.md](../PROJECT-STATUS.md) e gli
> snapshot datati. Quando una proposta viene implementata e rilasciata, aggiornare `PROJECT-STATUS.md` e lasciare qui
> il documento come storico (marcandone lo stato in testa: PROPOSTA → IN CORSO → FATTO).

## Indice

| Documento | Tema | Stato |
|---|---|---|
| [asset-visibility-scoping.md](asset-visibility-scoping.md) | Visibilità asset per utente (un utente vede/crea WO e richieste solo sugli asset assegnati) | PROPOSTA |

## Convenzioni
- **Nome file:** `kebab-case`, descrittivo (`asset-visibility-scoping.md`).
- **Intestazione:** prima riga `> **Stato: …**` + data + invasività stimata.
- **Ancorare al codice reale:** citare `file:riga` verificati, non solo idee.
- Aggiungere una riga all'**indice** qui sopra per ogni nuovo documento.
