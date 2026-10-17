# SPECTRO_CURRENT_STATE.md

## Snapshot handoff
Base: **Spectro ERP 3.0.0 Beta 6**.
- versionCode 22
- versionName `3.1.3-beta5`
- applicationId `com.spectro.app`
- SQLite schema 8
- backup formato v8
- branch di sviluppo usato: `spectro-3.0-beta`

## Funzioni già presenti
Clienti, WorkOrder, produzioni multiple, timeline, criticità, magazzino, colli, movimenti, Cotone, altri ricavi/lavori, attività, ricerca globale, PDF, backup/restore e Zentha base (prodotti, ordini, righe, costi extra, pagamenti, split utile).

Zentha include inoltre il catalogo analitico del listino 13/10/2026, costo unitario scomposto, snapshot di codice/descrizione/varianti e tre PDF separati per contabilità, produzione e controllo completo.

Il flusso di autenticazione consente di reinviare autonomamente l’email di conferma da Android e dal portale web. I messaggi Supabase più comuni sono tradotti e ricordano di usare esclusivamente il link più recente, perché ogni reinvio rende obsoleti quelli precedenti.

La Beta 4 ripara il permesso di sincronizzazione degli Admin esistenti, aggiunge i preset Supervisore e Attività/Zentha senza prezzi, sanifica lato server il mirror web per chi non possiede `finance.view`, ripristina lo scorrimento di Altri moduli e aggiunge le scorciatoie alla dashboard.

La Beta 5 aggiunge la migrazione locale schema 7 → 8 e un controllo auto-riparante per `cloud_sync_state`. Corregge le installazioni esistenti in cui il database era già marcato schema 7 prima dell'introduzione della sincronizzazione cloud, senza riscrivere i record ERP.

## Beta 6
Ha introdotto navigazione contestuale, home cliente, produzioni giornaliere raggruppate, dossier ordine più completo e tema Spectro Neon.

## Stato build
Lo snapshot Beta 6 con autenticazione, permessi e workspace cloud è stato compilato con `assembleDebug` e verificato con i test unitari. L’installazione su un dispositivo reale resta una verifica operativa da eseguire prima del rilascio.

## Bug aperto prioritario
Nel profilo cliente (esempio Goccia) lo storico mostra solo alcuni ordini, mentre esistono altre lavorazioni passate.
Causa individuata: la vista usa soprattutto `WorkOrder`, mentre molte produzioni storiche sono `ProductionRecord` legacy con `workOrderId == null`.
I dati non vanno inventati o duplicati: serve uno storico unificato.
