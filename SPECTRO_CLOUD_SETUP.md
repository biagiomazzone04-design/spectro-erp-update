# Spectro Cloud — configurazione e accessi concordati

## Stato
La correzione UI Beta 7.1 è Android locale. Non attiva login, sincronizzazione, account o permessi cloud. Questo documento definisce la fase successiva richiesta dall'utente, evitando di scambiare preferiti o schermate nascoste per un sistema di sicurezza.

Scelte confermate: PWA installabile su Windows e Mac; quattro utenti iniziali; amministrazione dall'app Android; autorizzazioni modificabili in qualsiasi momento. Nessun server/progetto cloud già disponibile.

## Preparazione del progetto Supabase
1. Aprire https://supabase.com/dashboard e creare l'account personale del titolare.
2. Creare un'organizzazione, poi **New project**. Nome suggerito: **Spectro ERP**. Scegliere una regione europea e il piano adatto dopo averne verificato i limiti correnti.
3. Generare una password database robusta e conservarla nel proprio gestore password. Non inserirla in chat, screenshot pubblici o Git.
4. Attendere lo stato operativo del progetto. Comunicare solo il **Project URL** (es. `https://<project-ref>.supabase.co`). Non occorre fornire adesso password, secret key, service_role o connection string.
5. Non caricare ancora backup reali e non creare bucket pubblici. Prima vanno implementati e verificati schema, policy di accesso e importazione.

La creazione del progetto richiede l'account del titolare. In questa sessione non è stato creato né configurato un servizio esterno. La successiva distribuzione della PWA richiederà anche l'account di hosting e il dominio/URL da inserire negli URL di autenticazione consentiti.

## Profili iniziali e autorizzazioni
I profili sono modelli iniziali, non vincoli rigidi. Le facoltà di scrittura degli altri tre utenti non sono state concordate: partire dalla consultazione e consentire al titolare di abilitarle esplicitamente, dopo aver verificato la sincronizzazione bidirezionale.

| Profilo | Visibilità iniziale | Economia | Amministrazione |
| --- | --- | --- | --- |
| Titolare, Android | Tutti i moduli; gestione completa | Completa | Inviti, sospensioni, permessi, audit |
| Supervisore | Tutti i moduli | Completa | Nessuna di default |
| Zentha amministrativo | Ordini e funzioni Zentha autorizzate | Zentha: prezzi, costi, margini, pagamenti | Nessuna di default |
| Operatore | Ordini/produzione, attività e campioni, Zentha operativo | Nessuna | Nessuna di default |

Matrice modificabile per utente: modulo × consultazione, creazione, modifica, annullamento, esportazione. Separare inoltre prezzi di vendita, costi/margini, pagamenti e amministrazione utenti. La distinzione evita che un accesso agli ordini attribuisca automaticamente accesso contabile.

Prevedere anteprima degli accessi effettivi, conferma delle modifiche, audit con attore/data/valori prima e dopo, sospensione dell'utente e prevenzione della rimozione dell'ultimo amministratore. Ogni richiesta verifica autorizzazioni attuali sul server: i ruoli inclusi in un token non bastano per revocare un accesso immediatamente.

## Architettura della fase successiva
- Supabase Auth: inviti ai quattro utenti, accesso personale, recupero account. Nessuna iscrizione pubblica al gestionale.
- PostgreSQL: area aziendale e appartenenze, profili e override per utente, permessi verificati da policy/RPC server. Deny by default.
- Dati operativi e finanziari separati: le risposte per l'operatore non devono contenere campi economici, anche se l'interfaccia li nasconde. Niente replica del backup completo al browser.
- Storage privato: allegati classificati operativi/contabili, accesso verificato e URL temporanei. Fatture, report economici e mockup con prezzi richiedono autorizzazione contabile.
- PWA responsive: desktop con menu laterale, tabelle filtrabili, ricerca, dettagli; pagine e API protette. Installabile dal browser, senza eseguibili distinti Windows/Mac.
- Android: database locale mantenuto, autenticazione e sessione personale, sincronizzazione tracciata. Package/firma e ID locali invariati.
- PWA inizialmente online per i dati: cache solo delle risorse statiche, nessuna cache offline di dati sensibili o risposte autenticate. La revoca blocca nuove consultazioni; un documento già scaricato non può essere ritirato dal dispositivo del destinatario.

## Sequenza di implementazione dopo il setup
1. Ambiente di prova separato dai dati operativi; schema utenti/permessi e test di isolamento.
2. Migrazione locale additiva per UUID, metadati e versioni; mappatura delle relazioni senza sostituire ID esistenti. Backup aggiornato e round-trip testato prima dell'importazione.
3. Importazione di una copia di backup: idempotenza, conteggi e checksum allegati, rapporto anomalie. I vecchi record mantengono le date note; non inventare uno storico di modifiche.
4. Sincronizzazione Android → server tramite outbox persistente, operazioni idempotenti, retry, tombstone per cancellazioni e audit.
5. PWA in consultazione per i tre utenti; viste operative/finanziarie realmente separate.
6. Editor permessi sul client amministratore e verifica immediata delle revoche su sessioni già aperte.
7. Scrittura da PWA solo con versione record attesa, conflitti espliciti, aggiornamenti atomici di stock/pagamenti e download delle modifiche su Android. Vietato sovrascrivere silenziosamente modifiche offline.
8. Collaudo con i quattro account; solo dopo passaggio ai dati reali.

## Criteri di accettazione
- Un utente senza economia non riceve importi da API, ricerca, esportazioni, realtime, file o errori.
- Cambiare un ID nell'URL non concede accesso a un record non autorizzato.
- Il cambio permessi vale anche per una sessione già aperta; i link allegati hanno scadenza limitata.
- Nessun service secret nel codice Android/PWA; nessun segreto in Git.
- Import ripetuto senza duplicazioni, backup allegati verificato, stock mai doppiamente scaricato.
- Due modifiche concorrenti producono un conflitto gestito, non perdita di dati.
- Prove Android, Chrome/Edge Windows e Safari/Chrome Mac, incluse PWA e ritorno dalla modalità offline.
