# Spectro Cloud — configurazione Supabase

Il progetto è predisposto per `https://fbfxtyowcxgagseqdlyz.supabase.co`.

1. Aprire **Supabase → SQL Editor** ed eseguire nell’ordine le migrazioni `202610100001`, `202610100002`, `202610170003` e `202610170004`. Le ultime due sono ripetibili e riparano in sicurezza installazioni già avviate.
2. In **Authentication → Providers → Email** abilitare email/password. In produzione lasciare attiva la conferma email.
3. Creare il primo account dall’app o dal portale desktop.
4. Con quel primo account autenticato chiamare una sola volta l’RPC `bootstrap_spectro_organization('Spectro')`. La funzione è protetta da lock e smette di funzionare dopo la creazione della prima organizzazione.
5. La chiave **publishable** del progetto è già configurata nelle build Android e web. In caso di rotazione, aggiornare `SPECTRO_SUPABASE_ANON_KEY` e `VITE_SUPABASE_ANON_KEY`.

La chiave `sb_publishable_` è configurazione client pubblica. La chiave `service_role` o una `secret key` non devono mai essere inserite nell’app, nel portale web o nel repository.

## Ruoli iniziali

- **Admin**: accesso completo, utenti, ruoli, audit, backup e impostazioni.
- **Produzione**: clienti in lettura; ordini, produzione, magazzino, criticità e attività operative.
- **Commerciale**: dashboard, clienti, ordini, finanza e report.
- **Zentha**: area Zentha, clienti e attività.
- **Sola lettura**: consultazione senza modifiche.

È possibile creare ruoli aggiuntivi e applicare override `allowed=true/false` a un singolo utente tramite `member_permission_overrides`.

## Protezioni incluse

- isolamento per organizzazione;
- Row Level Security su tutte le tabelle esposte;
- sessioni utente Supabase, nessuna chiave privilegiata nei client;
- soft-delete e revisione ottimistica dei record ERP;
- audit append-only delle modifiche;
- bucket allegati privato, organizzato con percorso `{organization_id}/...`.
- workspace completo limitato a chi possiede `sync.use`, con SHA-256 e trasferimento a blocchi da 45 MB (fino a 1 GB complessivo);
- pubblicazione atomica con revisione attesa: un client obsoleto riceve `SYNC_CONFLICT`;
- mirror `erp_records` aggiornato nello stesso commit e leggibile solo in base ai permessi del modulo.

