# Spectro ERP 3.0.0 Beta 5

## Ordini: produzione, evasione e storico
- Lo stesso ordine resta collegato alla propria produzione: non viene duplicato quando passa in produzione.
- `Completa ed evadi ordine` porta l'ordine allo stato Evaso, registra la data effettiva e lo rimuove dagli Ordini attivi.
- Produzione, nota tecnica, immagini, dati economici e timeline/criticità restano collegati e consultabili nello storico.
- La nota di produzione nuova o modificata genera automaticamente un evento nella timeline dell'ordine.
- Un ordine evaso può essere riaperto senza perdere la timeline.
- La cancellazione della registrazione produttiva è bloccata per un ordine già archiviato: occorre prima riaprire l'ordine.
- Nella scheda della produzione archiviata il pulsante distruttivo di eliminazione non viene mostrato.

## Clienti e statistiche
- La scheda cliente distingue Ordini attivi e Storico ordini evasi.
- Lo storico cliente mostra data di evasione, criticità, metri di carta e fatturato della produzione collegata.
- Le statistiche mensili conteggiano gli ordini evasi per cliente e nel totale mese.
- Il report PDF mensile include l'elenco degli ordini evasi e il numero di criticità collegate.

## Attività operative
- Icona Attività dedicata nella barra superiore con badge numerico.
- Badge rosso quando esistono attività urgenti/scadute.
- Filtro Urgenti nella sezione Attività.
- Campioni, ricerche grafiche e progetti con scadenza vengono ordinati mettendo prima ciò che richiede attenzione.

## Magazzino
- Scelta tessuto gerarchica: Cliente/Proprietario → Tessuto → Carico/Lotto → Colli.
- I tessuti con giacenza 0 mt non compaiono più nel Magazzino Attuale.
- I carichi esauriti NON vengono cancellati: rimangono nello Storico Magazzino.
- Nuove viste `ATTUALE` e `STORICO`.
- Lo Storico è ricercabile per cliente, tessuto, lotto, data, variante, fornitore e collo.
- Restano consultabili metri iniziali, metri residui, colli, movimenti e immagini/cartellini.
- La scheda cliente del magazzino mostra mini dashboard con giacenza, numero tessuti e colli attivi.

## Sicurezza dati
- Nessuna migrazione distruttiva introdotta in Beta 5.
- Schema database invariato rispetto a Beta 4 (v5).
- Formato backup invariato rispetto a Beta 4 (v7).
- Package invariato: `com.spectro.app`.
- Firma release invariata.

## Versione
- versionCode: 16
- versionName: `3.0.0-beta5`
