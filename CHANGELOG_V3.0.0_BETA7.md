# Spectro ERP 3.0.0 Beta 7

- Storico Cliente Unificato con ordini moderni, produzioni legacy e Cotone, ricerca per nome/riferimento/tessuto/disegno/data, dettagli e PDF. Le produzioni collegate restano nel WorkOrder, senza duplicare conteggi.
- Zentha ERP 2.0 con nove sezioni operative, prodotti e costi scomposti, varianti/taglie, magazzino separato, movimenti, scorte minime, listini e fasce personalizzabili, override cliente.
- Ordini con snapshot prezzo/costo/variante, documenti, distinta squadra, personalizzazioni e mockup approvati; workflow di 11 stati con data/ora. Conservati anche gli stati legacy e l'annullamento.
- Economia completa con sconto, ricavi trasporto/altro, extra, margini, split e pagamenti multipli. Statistiche per mese/anno, cliente, prodotto, taglia, ordine e tempi consegna disponibili.
- Sette PDF: conferma, distinta, produzione, packing, taglie, economia e storico. Costi interni nel solo report economico; testi lunghi paginati e allegati elencati.
- Scarico Zentha transazionale e ripetibile, riallineamento differenze e reso all'annullamento. Ordini, righe, costi extra e documenti mantengono la loro identità.
- Backup v8 con nuovi dati e documenti; compatibilità v7/legacy; controlli di integrità e rollback.
- Correzioni Beta 6: bozze cancellate solo dopo salvataggio riuscito; pulizia bozze v1; evento e nuova consegna nella stessa transazione; stock tessile e colli allineati anche senza selezione esplicita (FIFO); resi legacy identificabili quando il collo originale è ignoto; immagini tutte consultabili; PDF timeline senza taglio a otto righe/tre immagini.
- Schema 6 additivo, versione Android 18 / `3.0.0-beta7`, artifact `SpectroERP-v3.0.0-BETA7-RELEASE`. Package e firma invariati; nessun backend cloud introdotto.

Dettagli e limiti di verifica: [MIGRATION_BETA7.md](MIGRATION_BETA7.md).
