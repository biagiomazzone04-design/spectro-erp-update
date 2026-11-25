# Spectro ERP Native 3.2.11 Beta 21

> Multiutente/cloud: vedere [SPECTRO_MULTIUSER_SETUP.md](SPECTRO_MULTIUSER_SETUP.md) per autenticazione Supabase, ruoli, portale Windows/Mac e attivazione sicura.

ERP Android nativo per produzione tessile, ordini, magazzino a colli, criticità, Cotone, Zentha, attività operative e gestione economica.

## Identità Android preservata
- applicationId/package: `com.spectro.app`
- minSdk: 26
- compileSdk/targetSdk: 36
- versionCode: 38
- versionName: `3.2.11-beta21`
- firma RELEASE: stesso secret GitHub `SPECTRO_SIGNING_BUNDLE_B64`

## Funzioni principali
- Ordini sublimatici con passaggio allo stesso ordine produttivo senza perdere ID o Timeline.
- Bozze automatiche per ordini, produzione, Cotone e aggiornamenti Timeline.
- Timeline/criticità ordine con immagini, responsabilità, gravità e storico permanente.
- Magazzino cliente/azienda organizzato per tessuto, lotto/carico e singolo collo.
- Movimenti e scarico colli collegati alla produzione.
- Modulo Cotone con preventivo/consuntivo, immagini e costi.
- Modulo Zentha con catalogo analitico costi/listini, ordini personalizzati, pagamenti e PDF differenziati per contabilità, produzione e controllo completo.
- Modulo Altri ricavi per lavorazioni economiche e materiali imputati.
- Modulo Attività per campioni, ricerche grafiche e lavori operativi.
- Ricerca globale e profilo cliente operativo.
- Dashboard e statistiche mensili con fatturato, metri, utile, altri ricavi e criticità.
- PDF dark professionali con KPI, grafici e dettagli.
- Navigazione Indietro interna e tasto Android Back.
- Backup portabile con allegati e compatibilità dei backup precedenti.
- Sincronizzazione cloud automatica e persistente dopo le modifiche, con retry senza duplicare revisioni già allineate.
- Portale web analitico con KPI, filtri, tabelle operative e dossier cliccabili nel rispetto dei permessi economici.

## Migrazione dati
Il database attuale usa schema 13. L'aggiornamento dalle Beta precedenti è non distruttivo: clienti, ordini, produzioni, Timeline, magazzino, allegati e collegamenti Zentha non vengono eliminati.

Non è presente alcun reset automatico del database.

## Build
Il workflow `.github/workflows/main.yml` compila `main` e `spectro-3.0-beta`, verifica la firma e pubblica l'artifact `SpectroERP-v3.2.11-BETA21`.

Prima di installare una nuova Beta creare sempre un backup dall'app attualmente funzionante e installare l'APK sopra l'app esistente, senza disinstallarla.
