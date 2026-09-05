# Spectro ERP Native 2.1.0

Applicazione ERP Android nativa per la gestione operativa di una stamperia tessile.

Tecnologie: Kotlin, Jetpack Compose, SQLite locale, Material 3. Funzionamento core offline.

## Moduli
- Dashboard produzione / costi / utile.
- Produzione e eventi negativi.
- Ordini e lavorazioni con workflow stato.
- Magazzino tessuti con soglia scorta minima.
- Rubrica clienti con storico e indicatori.
- Registro criticità cliente con foto, timeline, responsabilità e ritardi.
- Scadenze e incassi clienti.
- Ricerca globale.
- Backup portabile con database + allegati.
- Report PDF produzione, ordine/lavorazione e dossier criticità.
- Temi grafici animati con gradienti.

## Build
Il workflow `.github/workflows/main.yml` crea un APK RELEASE firmato.
Prima della prima build RELEASE configura i GitHub Secrets descritti in `ISTRUZIONI_AGGIORNAMENTO_V2.1.0.md`.

Artifact: `SpectroERP-v2.1.0-RELEASE`.

## Compatibilità dati
Database schema v2 con migrazione automatica dalla prima versione nativa. Il formato backup v4 continua a leggere i backup Spectro Native precedenti.
