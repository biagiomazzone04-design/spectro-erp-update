# SPECTRO_CURRENT_STATE.md

## Snapshot handoff
Base: **Spectro ERP 3.0.0 Beta 6**.
- versionCode 19
- versionName `3.1.0-beta2`
- applicationId `com.spectro.app`
- SQLite schema 7
- backup formato v8
- branch di sviluppo usato: `spectro-3.0-beta`

## Funzioni già presenti
Clienti, WorkOrder, produzioni multiple, timeline, criticità, magazzino, colli, movimenti, Cotone, altri ricavi/lavori, attività, ricerca globale, PDF, backup/restore e Zentha base (prodotti, ordini, righe, costi extra, pagamenti, split utile).

Zentha include inoltre il catalogo analitico del listino 13/10/2026, costo unitario scomposto, snapshot di codice/descrizione/varianti e tre PDF separati per contabilità, produzione e controllo completo.

## Beta 6
Ha introdotto navigazione contestuale, home cliente, produzioni giornaliere raggruppate, dossier ordine più completo e tema Spectro Neon.

## Stato build
Lo snapshot Beta 6 con autenticazione, permessi e workspace cloud è stato compilato con `assembleDebug` e verificato con i test unitari. L’installazione su un dispositivo reale resta una verifica operativa da eseguire prima del rilascio.

## Bug aperto prioritario
Nel profilo cliente (esempio Goccia) lo storico mostra solo alcuni ordini, mentre esistono altre lavorazioni passate.
Causa individuata: la vista usa soprattutto `WorkOrder`, mentre molte produzioni storiche sono `ProductionRecord` legacy con `workOrderId == null`.
I dati non vanno inventati o duplicati: serve uno storico unificato.
