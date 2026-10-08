# SPECTRO_PROJECT_CONTEXT.md

## Identità tecnica
- Android Kotlin + Jetpack Compose
- SQLite nativo
- package/applicationId `com.spectro.app`
- compile/target SDK 36
- Java 17
- Gradle 8.13 in GitHub Actions
- firma release custodita nei GitHub Secrets

## Moduli
- Dashboard e statistiche
- Clienti
- Ordini / WorkOrder
- Produzioni giornaliere
- Timeline e criticità
- Magazzino tessuti per cliente e aziendale
- Colli e movimenti
- Cotone
- Altri lavori economici
- Attività operative
- Ricerca globale
- PDF e backup
- Zentha Sport

## Regole di dominio
### Ordine
Lo stesso ordine attraversa `IN_PROCESS -> IN_PRODUCTION -> COMPLETED/DELIVERED/CLOSED` senza duplicarsi.

### Produzione
Un ordine può avere più `ProductionRecord`; la UI può raggrupparli ma il DB conserva i singoli record.

### Criticità
Timeline e criticità restano collegate all'ordine anche dopo l'evasione.

### Magazzino
Gerarchia: `Cliente/Azienda -> Tessuto -> Lotto/Carico -> Collo -> Movimento`.
I colli a 0 m spariscono dalla vista attiva ma restano nello storico.

### Backup
Backup e restore sono funzioni critiche e devono includere allegati e nuove entità.

## UI/PDF
Tema Spectro Neon: nero, fucsia, arancio, testo bianco molto leggibile. PDF professionali con fondo nero, KPI, grafici, font grandi e dettaglio completo.
