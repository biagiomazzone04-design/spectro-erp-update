# Database Spectro Beta 7

File `spectro_native.db`, schema **6**. Il backup portabile usa formato **8**.

## Migrazione 5 → 6
`Beta7Migration.apply` aggiunge colonne dopo averne verificato l'esistenza con `PRAGMA table_info`. Non elimina o riscrive le colonne precedenti. Le migrazioni delle versioni più vecchie continuano a essere applicate in sequenza.

Colonne aggiunte:
- `zentha_products`: linea, costi stampa, confezione, accessori, packaging, altri. Il costo base resta `standard_cost`; i nuovi costi partono da zero.
- `zentha_orders`: contatto, sconto, ricavo trasporto, altri ricavi e `stock_posted` (false iniziale).
- `zentha_order_items`: `variant_id` nullable e snapshot di descrizione, taglia, SKU. Prezzo/costo restano nelle colonne esistenti. Una riga rappresenta una variante; più taglie richiedono più righe.

Tabelle nuove (nomi SQLite effettivi):
- `zentha_variants`: prodotto, SKU, taglia, colore, vestibilità, tipologia, attivo.
- `zentha_balances`: giacenza, soglia e ubicazione; una riga per variante.
- `zentha_movements`: quantità con segno, tipo, data/ora, ordine opzionale e motivo.
- `zentha_priceLists`, `zentha_priceRules`: listini e fasce inclusive per prodotto/variante; regole cliente alternative alle regole listino.
- `zentha_clientProfiles`: cliente, listino assegnato, note.
- `zentha_roster`: atleta, numero, taglie, kit opzionale, note.
- `zentha_customizations`: ordine/riga, tipo, contenuto, note, approvazione e allegati.
- `zentha_events`: data/ora, stato precedente/successivo e descrizione.

Indici per riferimenti ordine/prodotto, SKU variante univoco se valorizzato (senza distinzione maiuscole), giacenza e profilo cliente univoci. Seed dei soli nomi Standard, Società Sportive, Rivenditori, VIP. Nessun prezzo, variante, quantità o timestamp storico viene inventato.

## Invarianti
Salvataggi ordine, righe, costi extra, movimenti ed eventi sono transazionali. Gli ID di ordine, righe e costi extra restano invariati durante modifica. Le personalizzazioni di una riga rimossa restano sull'ordine. Catalogo e listini non riscrivono gli snapshot storici. Gli ordini si annullano, senza cancellarne lo storico.

Lo scarico è esplicito. Ogni riallineamento confronta quantità richieste e movimenti netti già registrati; una ripetizione non duplica lo scarico. Quantità negative di giacenza e movimenti con segno scorretto sono rifiutati. Un fallimento su una variante annulla tutta la transazione. L'annullamento restituisce lo scarico netto.

Le relazioni nuove sono validate dal repository e prima del ripristino. Lo schema resta SQLite locale, senza introdurre backend o identificatori cloud fittizi.

## Storico cliente
WorkOrder + ProductionRecord di tipo PRODUCTION con `workOrderId == null` + Cotone. Il clientId è autorevole; confronto normalizzato del nome solo per record senza clientId. Le produzioni collegate compaiono nel dossier del WorkOrder. Nessuna deduplicazione euristica per titolo/riferimento, nessuna conversione dei legacy. Le statistiche continuano a sommare i record produttivi una volta.

## Backup
Il codec v8 include tutte le entità e gli allegati nuovi. ZIP portabile con percorsi `attachment://`, ripristinati in una nuova directory privata. File mancanti/duplicati e relazioni o giacenze Zentha incoerenti sono rifiutati. Il database viene sostituito in transazione; gli allegati preesistenti non vengono cancellati.

Il test di migrazione usa lo schema originale Beta 6 (commit `5d8132296ee50be195055f6028367fbaf127cc53`) e confronta ogni colonna di ogni tabella preesistente prima/dopo, ripetendo poi la migrazione.
