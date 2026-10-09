# Migrazione e verifica Beta 7

## Aggiornamento
Versione 18 / `3.0.0-beta7`; package `com.spectro.app` e firma release invariati. SQLite 5 → 6, backup 7 → 8. Usare la release firmata con la chiave esistente per aggiornare l'installazione; il debug locale ha la normale firma di sviluppo.

La migrazione aggiunge 15 colonne e 9 tabelle Zentha. Tutte le colonne precedenti, ID, righe economiche, pagamenti, note, immagini e allegati restano conservati. Vengono creati solo quattro listini vuoti. Gli ordini legacy non vengono scaricati automaticamente: occorre associare varianti e confermare lo scarico. Non viene ricostruita una cronologia non disponibile.

Backup vecchi: i campi assenti ricevono valori compatibili (nuovi costi/ricavi zero, variante nulla, stock non scaricato). I nuovi record entrano nel ZIP insieme agli allegati. Durante il restore, nomi file nuovi evitano di sovrascrivere documenti esistenti; un errore di inserimento o integrità annulla la sostituzione DB. Un vecchio JSON con soli percorsi non può ricreare i byte di allegati che non contiene.

## Verifiche automatiche

Esito finale cloud: **30 test passati, 0 falliti, 0 saltati**, `assembleDebug` riuscita. Lo script di setup aggiornato è stato rieseguito con gli stessi esiti.
- Baseline Beta 6: 9 test e `assembleDebug` passati prima delle modifiche.
- Storico Goccia: 2 WorkOrder, 10 produzioni (3 collegate, 7 legacy) → 9 voci; ricerca e ordinamento; identità cliente.
- Prezzi: confini fasce, priorità cliente/variante, sovrapposizioni, listino disattivo; snapshot invariati dopo modifiche catalogo.
- Economia: costi scomposti, sconti/trasporto/extra, quote arrotondate, pagamenti multipli e stati, statistiche per ID prodotto.
- Stock Zentha: segni, giacenza insufficiente, rollback, ripetizione scarico, modifica quantità, annullamento e conservazione allegati/pagamenti.
- Migrazione SQLite reale via Robolectric, schema Beta 6 originale, tutte le colonne/tabelle preesistenti conservate e ripetibilità.
- Backup v8 round-trip completo, compatibilità v7 e JSON legacy, byte e percorsi allegati, rifiuto archivio incompleto, rollback su duplicati e giacenza incoerente.
- Regressioni: vecchie bozze eliminate correttamente; consegna/evento atomici; produzione senza colli espliciti e modifica produzione legacy mantengono stock aggregato e colli coerenti.
- Contenuti dei sette PDF: dettagli, distinta, taglie, personalizzazioni, note lunghe e separazione dei costi interni. La stessa struttura verificata alimenta il renderer Android.

Comando: `gradle --no-daemon --max-workers=4 :app:testDebugUnitTest :app:assembleDebug`.
Report: `app/build/reports/tests/testDebugUnitTest/index.html`.

## Prove su dispositivo ancora necessarie
Nessun dispositivo/emulatore disponibile in questa sessione. Verificare installazione sopra Beta 6 con firma release, navigazione/tastiera, selezione e apertura documenti, rendering/condivisione PDF e restore di una copia del backup operativo. Robolectric non implementa `PdfDocument`: i test coprono il contenuto dei report, non la resa grafica nativa.

La firma release e il workflow di firma non sono stati modificati né eseguiti in cloud locale. Non sono stati letti o inseriti segreti.
