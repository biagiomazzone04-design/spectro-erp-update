# AGENTS.md — Spectro ERP

Queste istruzioni valgono per l'intero repository.

## Regole non negoziabili
- Leggi prima `SPECTRO_CODEX_START_HERE.md`.
- Package/app id invariato: `com.spectro.app`.
- Non cambiare o rigenerare la chiave di firma e non committare segreti.
- Il workflow usa `SPECTRO_SIGNING_BUNDLE_B64`: non hardcodarlo e non stamparlo.
- Vietate migrazioni distruttive, reset DB automatici e DROP indiscriminati.
- I dati presenti devono sopravvivere agli aggiornamenti.
- Backup vecchi devono restare importabili o essere migrati esplicitamente e testati.
- Un ordine mantiene la stessa identità attraverso processo, produzione, evasione e storico.
- Timeline, criticità, note e allegati non devono sparire nei cambi di stato.
- Prima di considerare una task completa eseguire `gradle :app:testDebugUnitTest` e una build Android.
- Le modifiche economiche e di magazzino richiedono test automatici.
- `main` resta stabile; lavorare da `spectro-3.0-beta` su un branch dedicato Beta 7.

## UI
- Mobile first.
- Nero profondo, fucsia fluo primario, arancio fluo per attenzione, testo bianco.
- Mai sovrapporre fucsia e arancio se il contrasto peggiora.
- Header leggibile, logo grande, `SPECTRO` sempre su una sola riga.
