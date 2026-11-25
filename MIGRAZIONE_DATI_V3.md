# Migrazione dati: Spectro ERP 2.x -> 3.0 Beta 1

## Regole
1. Il database esistente non viene eliminato.
2. Le nuove tabelle vengono create durante `onUpgrade`.
3. I campi nuovi degli ordini vengono aggiunti mantenendo i record precedenti.
4. Le vecchie giacenze tessuto vengono trasformate in un singolo collo `LEGACY-xxxx` con gli stessi metri.
5. Gli ordini senza codice 3.0 ricevono un codice legacy coerente senza cambiare il loro ID.
6. Il backup 3.0 include le nuove entità e gli allegati.

## Prima di installare la Beta
- conservare il backup pre-3.0 fuori dal telefono;
- non cancellare la versione stabile prima di aver verificato la firma;
- conservare l'APK stabile precedente.

## Prima apertura
Alla prima apertura il database viene aggiornato da schema 2 a schema 3. Verificare subito:
- clienti;
- ordini;
- produzioni;
- magazzino e metri legacy;
- immagini/allegati esistenti.

Creare poi un nuovo backup 3.0 di prova e verificarne il ripristino prima di considerare la Beta stabile.

## Aggiornamenti successivi fino a Beta 4

### Schema 3 -> 4
- immagini multiple per singolo collo;
- titolo e immagini per ordini Cotone.

### Schema 4 -> 5 (Beta 4)
- nuova tabella `economic_works` per altri ricavi/lavorazioni economiche;
- nuova tabella `operational_tasks` per attività operative;
- nuovi indici per data, cliente e stato;
- nessuna tabella preesistente viene eliminata.

### Backup Beta 4
Il backup portabile formato v7 comprende anche:
- lavori economici e immagini;
- attività operative e immagini;
- immagini dei colli;
- immagini Cotone;
- Timeline e criticità.

I backup precedenti rimangono leggibili: i nuovi moduli vengono inizializzati vuoti quando non presenti nel file importato.
