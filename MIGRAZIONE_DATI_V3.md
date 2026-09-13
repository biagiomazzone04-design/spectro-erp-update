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
