# Changelog Spectro ERP 3.0.0 Beta 1

## Magazzino
- proprietà tessuto Cliente/Azienda;
- metadati tessuto e lotto;
- colli/rotoli individuali con metri iniziali e disponibili;
- carichi multipli;
- registro movimenti: carico, utilizzo ordine, restituzione, rettifica, scarto;
- soglia minima e visibilità delle giacenze;
- allocazione esatta dei colli in produzione e cotone.

## Ordini e produzione
- ciclo ordine esteso;
- ordine in processo collegabile alla produzione mantenendo lo stesso ID;
- passaggio automatico a In produzione;
- modifica ordine mantenuta da elenco, dashboard e dettaglio;
- collegamento ordine/produzione;
- ripristino giacenze quando una produzione viene modificata o eliminata.

## Criticità e Timeline
- diario cronologico per ordine;
- categoria, responsabilità, gravità, stato e variazione consegna;
- più allegati per singolo evento;
- centro criticità;
- esportazione PDF della cronologia con immagini raster disponibili.

## Ricerca globale
- clienti;
- ordini;
- tessuti e colli;
- timeline;
- ordini cotone;
- ordini/prodotti Zentha;
- produzioni.

## Cotone
- reattiva, pigmento o altro;
- costi di produzione manuali €/m;
- prezzo stampa €/m;
- tessuto cliente o aziendale;
- scelta colli per tessuto aziendale;
- quantità ordinate, prodotte, consegnate, difettose, campioni e avanzate;
- ricavo, costo produzione, costo tessuto, costo totale, margine e margine %.

## Zentha
- catalogo prodotti;
- costo e prezzo standard;
- snapshot economico nell'ordine;
- ordini multiprodotto;
- allegati ordine;
- costi extra;
- utile ordine;
- divisione utile configurabile, default 50/50;
- pagamenti e residuo.

## Dati e sicurezza
- database schema 3 con migrazione dalla v2;
- backup schema 5;
- lettura backup native precedenti;
- package `com.spectro.app` invariato;
- firma RELEASE invariata;
- nessuna migrazione distruttiva automatica.
