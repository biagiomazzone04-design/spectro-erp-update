# SPECTRO_ARCHITECTURE.md

## Architettura attuale
`Jetpack Compose UI -> SpectroViewModel -> SpectroRepository -> SpectroDatabase(SQLite)` con storage privato per allegati, backup custom e GitHub Actions per test/build/signing.

## Regola Beta 7
Non introdurre ancora il cloud completo nella stessa release dello storico cliente e di Zentha ERP 2.0. Prima stabilizzare il modello locale.

## Storico Cliente Unificato
Combinare:
1. `WorkOrder` moderni;
2. `ProductionRecord` legacy con `workOrderId == null`;
3. Cotone, dove appropriato alla vista cliente.

Deduplicazione obbligatoria: un `ProductionRecord` con `workOrderId != null` appartiene al WorkOrder e non deve comparire di nuovo come voce legacy.

## Zentha ERP 2.0
Separare chiaramente anagrafica prodotto, varianti/taglie, stock, movimenti, listini, clienti/listino, ordini, righe per variante, distinta atleta, personalizzazioni, workflow, pagamenti, costi e statistiche.
Prezzi e costi delle righe ordine devono essere snapshot storici.
