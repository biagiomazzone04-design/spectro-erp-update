# Spectro ERP Native 2.1.0 — Aggiornamento operativo

## Branding
- Nuova icona app con logo Spectro Print su fondo nero.
- Nuovo logo trasparente nell'header in alto a sinistra.
- Splash screen aggiornato.
- Tema predefinito `Spectro Flame`: gradiente arancio / fucsia / arancio.

## Date UX
- Nuovo selettore data a tre ruote: giorno, mese, anno.
- Usato in dashboard, produzione, evento negativo, ordini/lavorazioni, criticità e scadenze.
- Le date opzionali possono essere svuotate con un tocco.

## Ordini e lavorazioni
- Stati estesi: Ricevuto, Attesa materiale, Attesa cliente, In lavorazione, Stampato, Trasferito, Pronto, Consegnato, Sospeso, Bloccato.
- Aggiornamento stato direttamente dalla lista.
- Ricerca e filtro "solo attivi".
- Evidenza automatica delle consegne scadute.
- Esportazione PDF del riepilogo ordine/lavorazione.

## Storico criticità cliente
- Nuovo archivio criticità collegato al cliente e al riferimento ordine.
- Categoria, responsabilità, gravità, stato, data apertura.
- Data consegna iniziale e nuova data consegna.
- Calcolo automatico dei giorni di ritardo quando sono presenti entrambe le date.
- Descrizione dei fatti e note interne.
- Allegati foto/screenshot salvati nello storage privato.
- Timeline append-only con nuovi aggiornamenti e nuove immagini.
- Dossier PDF della criticità con cronologia.
- Indicatori nel profilo cliente: numero criticità e giorni di ritardo documentati.

## Scadenze clienti
- Importo, cliente, riferimento ordine/fattura, data scadenza.
- Pagato / da pagare, data pagamento, metodo e note.
- Evidenza automatica delle scadenze superate.
- Totale da incassare e totale scaduto.

## Magazzino
- Soglia minima configurabile per ogni tessuto.
- Badge SCORTA BASSA quando la giacenza raggiunge la soglia.

## Dashboard e ricerca
- Pannello "Da controllare" con conteggi automatici:
  - criticità aperte;
  - pagamenti scaduti;
  - scorte basse;
  - ordini oltre la data di consegna.
- Ricerca globale dall'icona lente nell'header attraverso clienti, produzione, ordini, criticità e scadenze.

## Backup
- Formato backup aggiornato a v4.
- Include anche criticità, timeline, scadenze e relativi allegati.
- Compatibile con backup Spectro Native precedenti.

## Database
- Migrazione automatica SQLite da schema 1 a schema 2.
- I dati esistenti non vengono cancellati durante un normale aggiornamento firmato con la stessa chiave.

## Firma aggiornamenti
- Workflow GitHub convertito a APK RELEASE firmato in modo permanente tramite GitHub Secrets.
- Dalla prima installazione della versione firmata, le release future potranno essere installate sopra la precedente senza disinstallare.
