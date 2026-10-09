# Spectro ERP 3.0.0 Beta 7.1 — navigazione e controllo operativo

## Correzioni
- “Altri moduli” era una Column priva di scroll: ora è un centro moduli con griglia scorrevole, adattiva, ricerca e categorie. Zentha e Altri ricavi sono in evidenza.
- Il contenuto rispetta lo spazio della barra inferiore e della tastiera, così l'ultima azione non resta nascosta dietro la navigazione.
- Le destinazioni della barra inferiore hanno etichette; controlli principali da almeno 48 dp. Il marchio SPECTRO si adatta allo spazio mantenendo una riga.

## Accesso rapido e interazioni
- Preferiti personalizzabili con la stella, persistenti sul dispositivo e disponibili nella Dashboard.
- Pulsante Moduli accessibile da ogni sezione; stato di scroll delle schermate mantenuto quando si cambia destinazione.
- Transizioni brevi di scorrimento/dissolvenza, risposta alla pressione delle schede, animazione degli elementi filtrati e selezione della barra. Nessuna animazione ciclica decorativa; rispettata la scala animazioni del sistema.

## Zentha
- Selettore delle nove aree sempre disponibile durante lo scroll.
- KPI cliccabili verso ordini aperti, approvazioni, produzione, ritardi, pronti, scorte basse, residui e utile mensile.
- Filtri ordini espliciti, quantità risultati e totali della vista; filtro sotto scorta; scorciatoie Mese/Anno/Tutti nelle statistiche.

## Compatibilità
Schema SQLite 6 e backup v8 invariati. Nessuna migrazione dei dati; preferiti in preferenze locali separate dal database aziendale. Package `com.spectro.app` e configurazione firma invariati. Multiutente non ancora attivo: requisiti confermati e setup successivo in `SPECTRO_CLOUD_SETUP.md`.

## Verifica

Esito: **35 test passati, 0 falliti, 0 saltati**; `:app:testDebugUnitTest` e `:app:assembleDebug` completati con successo. Versione Android 19 / `3.0.0-beta7.1`. Artifact release `SpectroERP-v3.0.0-BETA7.1-RELEASE`.
Test Compose su viewport piccola con font ingranditi, ricerca, apertura Zentha/Altri ricavi, preferiti e separazione dalla barra inferiore; test logici dei filtri Zentha. Rieseguiti test di dati/migrazioni/backup Beta 7 e build debug. Il controllo UI automatico usa Robolectric; resta utile il riscontro sul telefono reale.
