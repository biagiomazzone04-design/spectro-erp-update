# SPECTRO_CURRENT_STATE.md

## Snapshot handoff
Base: **Spectro ERP 3.0.0 Beta 6**.
- versionCode 17
- versionName `3.0.0-beta6`
- applicationId `com.spectro.app`
- SQLite schema 5
- backup formato v7
- branch di sviluppo usato: `spectro-3.0-beta`

## Funzioni già presenti
Clienti, WorkOrder, produzioni multiple, timeline, criticità, magazzino, colli, movimenti, Cotone, altri ricavi/lavori, attività, ricerca globale, PDF, backup/restore e Zentha base (prodotti, ordini, righe, costi extra, pagamenti, split utile).

## Beta 6
Ha introdotto navigazione contestuale, home cliente, produzioni giornaliere raggruppate, dossier ordine più completo e tema Spectro Neon.

## Stato build
Non c'è una conferma definitiva nella conversazione che lo snapshot Beta 6 sia già stato compilato/installato con successo. Prima di Beta 7 eseguire build completa e correggere eventuali regressioni.

## Bug aperto prioritario
Nel profilo cliente (esempio Goccia) lo storico mostra solo alcuni ordini, mentre esistono altre lavorazioni passate.
Causa individuata: la vista usa soprattutto `WorkOrder`, mentre molte produzioni storiche sono `ProductionRecord` legacy con `workOrderId == null`.
I dati non vanno inventati o duplicati: serve uno storico unificato.
