# Spectro ERP 3.0.0 Beta 4

## Bozze e storico ordini
- Bozza automatica persistente durante creazione e modifica degli ordini sublimatici.
- Bozza automatica persistente durante creazione/modifica dell'ordine produttivo e durante il passaggio ordine -> produzione.
- Bozza automatica persistente per gli ordini Cotone.
- Bozza automatica persistente per ogni nuovo aggiornamento della Timeline/Criticità.
- Chiudere una schermata o l'app non elimina più i dati in compilazione; la bozza viene rimossa solo dopo salvataggio riuscito o con "Scarta bozza".
- Il passaggio di un ordine in produzione mantiene lo stesso WorkOrder e quindi tutta la Timeline precedente.
- La nota tecnica di produzione viene registrata automaticamente nella Timeline; le modifiche successive della nota creano uno storico prima/dopo.
- Dalla registrazione produttiva è possibile aprire direttamente lo storico completo dell'ordine collegato.

## PDF professionali dark
- Nuovo motore PDF con sfondo nero e palette Spectro (bianco, arancio, rosso, magenta, verde e ciano).
- Testi più grandi e leggibili.
- Dashboard a card, KPI e grafici a barre nei report economici.
- Nuovo stile applicato a produzione, ordini, Cotone, storico, criticità, altri ricavi, statistiche mensili e Zentha.
- Report mensile con dettaglio clienti, metri carta, Cotone, altri ricavi, costi, utile e criticità.
- Conteggio criticità per cliente nel report mensile, includendo Timeline ordine e criticità CRM precedenti.

## Altri ricavi / lavori economici
- Nuovo modulo "Altri ricavi" per trasferimenti, lavorazioni conto terzi e ricavi non derivanti da una stampa Spectro.
- Cliente opzionale, data, titolo lavoro, quantità, prezzo unitario o fatturato lordo manuale.
- Materiali utilizzati con quantità e costo unitario (es. carta protezione, cotone, ecc.).
- Altri costi, note, immagini, stato, fatturato lordo, costi totali e utile netto automatici.
- Esportazione PDF analitica.
- I valori confluiscono nel fatturato e nell'utile mensile.

## Attività operative
- Nuovo modulo "Attività" per campioni, ricerca grafiche, richieste cliente, preparazione lavoro e attività generiche.
- Cliente e ordine collegabili.
- Priorità, stato, data, scadenza, descrizione e immagini.
- Accesso diretto all'ordine collegato.
- Attività aperte mostrate negli alert della Dashboard.

## Navigazione
- Pulsante Indietro nell'header dei moduli interni.
- Gestione del tasto Indietro Android.
- Possibilità di tornare alla sezione precedente senza chiudere e riaprire l'app.

## Criticità clienti
- Criticità della Timeline visibili direttamente nella scheda cliente come elementi dettagliati e apribili.
- Accesso all'ordine e allo storico completo dalla criticità.
- Le vecchie criticità CRM rimangono disponibili separatamente.
- Statistiche mensili con numero di criticità per cliente e totale mensile.

## Dashboard e statistiche
- Gli altri ricavi entrano nel fatturato totale e nell'utile.
- Costi degli altri lavori visibili nella redditività.
- Conteggio attività operative aperte negli alert.
- KPI mensili aggiuntivi: altri ricavi, altri lavori e criticità.

## Compatibilità
- Package invariato: `com.spectro.app`.
- Database: versione 5, migrazione non distruttiva da Beta 3.
- Backup: formato v7, include nuovi lavori economici, attività e relativi allegati.
- versionCode: 15.
- versionName: `3.0.0-beta4`.
