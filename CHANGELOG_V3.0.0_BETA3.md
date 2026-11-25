# Spectro ERP 3.0.0 Beta 3

## Ricerca globale
- I risultati della ricerca sono ora apribili.
- Cliente: apre un profilo operativo con magazzino e ordini.
- Ordine sublimazione: apre dettaglio/storico e consente esportazione PDF.
- Produzione, Cotone, Zentha, Magazzino e Timeline/Criticità sono navigabili dai risultati pertinenti.

## Ordini e bozze
- Gli ordini Sublimazione e Cotone salvano automaticamente una bozza locale durante la compilazione.
- Se la schermata/app viene chiusa accidentalmente, la bozza viene recuperata.
- Pulsante "Scarta bozza" per rimuoverla volontariamente.
- Nelle card operative viene mostrato prima il nome ordine scelto dall'utente; il codice SPE rimane riferimento interno.

## Cotone
- Nome ordine, stato, consegna, tessuto, metri, costi e consuntivo.
- Allegati immagini.
- Apertura dettaglio e PDF ordine.
- Gestione magazzino/collo collegata quando il tessuto è aziendale.

## Magazzino
- Vista organizzata Cliente/Azienda -> Tessuto -> Carichi/Lotti -> Colli.
- Tessuti mostrati in maiuscolo.
- Mini riepiloghi delle giacenze.
- Immagini multiple per ogni singolo collo.

## Dashboard e statistiche
- Metri carta stampati nel mese corrente.
- Metri Cotone.
- Utile mensile.
- Anteprima metri stampati per cliente.
- Statistiche mensili con viste Riepilogo, Fatturato, Clienti, Metri e Utile.
- Esportazione PDF mensile dettagliata.

## UI / Branding
- Nuovo logo Spectro fornito dall'utente per icona, splash, adaptive icon e logo interno.
- Testi secondari più chiari e leggibili nel tema scuro.

## Dati e compatibilità
- Database aggiornato da v3 a v4 con migrazione non distruttiva.
- Aggiunti campi immagini colli e dati/immagini Cotone.
- Backup aggiornato per i nuovi campi.
- Package invariato: `com.spectro.app`.
- versionCode: 14.
- versionName: `3.0.0-beta3`.
