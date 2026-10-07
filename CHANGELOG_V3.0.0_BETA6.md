# Spectro ERP 3.0.0 Beta 6

## Navigazione e scheda cliente
- Navigazione contestuale migliorata: Cliente -> Ordini -> Dettaglio ordine -> Indietro torna alla lista ordini dello stesso cliente, senza rientrare direttamente in Dashboard.
- La sezione del profilo cliente aperta (Magazzino, Ordini, Criticità, Statistiche) viene ricordata quando si apre e si richiude un ordine.
- Scheda cliente riorganizzata in quattro aree chiare e cliccabili: MAGAZZINO, ORDINI, CRITICITÀ, STATISTICHE.
- Il Magazzino cliente distingue giacenze attive e storico tessuti esauriti.

## Ordini e storico
- Gli ordini mostrano in primo piano il nome assegnato dall'utente; il codice SPE resta un riferimento secondario.
- Tessuti e metraggi sono evidenziati in MAIUSCOLO e grassetto nelle liste e nei dettagli.
- Note e immagini dell'ordine rimangono visibili nel dossier anche dopo il passaggio in produzione.
- Le modifiche a note e allegati vengono registrate nella Timeline dell'ordine.
- Il dossier ordine mostra dati originali, tessuti, produzioni, note produttive, immagini e Timeline/criticità.

## Produzioni multiple e aggregazione giornaliera
- Un singolo ordine può avere più registrazioni produttive senza perdere lo storico precedente.
- Le produzioni della stessa giornata collegate allo stesso ordine vengono raggruppate visivamente in un'unica card.
- La card aggregata mostra tessuti, metri complessivi, carta totale, numero di lavorazioni, note e fatturato.
- Le singole produzioni restano record indipendenti nel database e sono consultabili dal dettaglio dell'ordine.
- Completando/Evadendo l'ordine, l'ordine sparisce dagli attivi ma rimane nello storico cliente e nelle statistiche con tutte le produzioni collegate.

## UI Spectro Neon
- Header ridisegnato per impedire la spezzatura verticale di SPECTRO.
- Logo ingrandito e sfondo dell'app portato a nero puro per integrarlo visivamente senza riquadro evidente.
- Nuovo tema Spectro Neon: nero + fucsia fluo come accento principale + arancio fluo per attenzione/priorità.
- Fucsia e arancio non vengono sovrapposti nei controlli principali.
- Testi secondari schiariti per una maggiore leggibilità.

## PDF
- Dossier storico ordine aggiornato per includere tutte le produzioni collegate, note e immagini.
- Colori PDF allineati al tema Spectro Neon.

## Compatibilità
- Package invariato: `com.spectro.app`.
- versionCode: 17.
- versionName: `3.0.0-beta6`.
- Nessuna migrazione distruttiva del database.
- Firma RELEASE invariata.
