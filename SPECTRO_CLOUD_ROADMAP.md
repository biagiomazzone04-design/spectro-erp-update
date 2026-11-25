# Spectro Cloud / Multiutente - roadmap

Beta 5 rimane locale/offline e NON attiva ancora alcun servizio cloud.
Questa scelta evita di sincronizzare comportamenti ancora in fase di stabilizzazione.

## Obiettivo
Un unico Spectro ERP utilizzabile da:
- Android / smartphone e tablet;
- browser desktop / PC;
- più utenti contemporaneamente;
- ruoli e permessi personalizzabili;
- aggiornamenti realtime;
- funzionamento offline con sincronizzazione successiva.

## Architettura prevista
1. App Android: database locale per uso offline.
2. Backend centrale: PostgreSQL + API + autenticazione + realtime.
3. Web app/PWA: interfaccia desktop collegata allo stesso backend.
4. Object storage: foto, cartellini, PDF, allegati e documenti.
5. Audit log: chi ha creato/modificato/completato ogni record.

## Prima fase a costo minimo
Valutazione prevista:
- Supabase Free: database PostgreSQL, autenticazione e realtime;
- Cloudflare R2: allegati e immagini;
- Cloudflare Pages: web app/PWA.

Obiettivo iniziale: costo 0 €/mese finché i limiti gratuiti sono sufficienti.

## Evoluzione entro circa 10 €/mese
Quando Spectro diventa production-critical:
- piccolo VPS;
- PostgreSQL gestito sul VPS;
- API Spectro e realtime;
- Cloudflare R2 per allegati;
- Cloudflare Pages/PWA per il client web.

## Permessi previsti
Esempi:
- Admin: accesso completo;
- Produzione: ordini, produzione, magazzino e attività, senza margini/costi sensibili;
- Commerciale: clienti, ordini, pagamenti e fatturato;
- Zentha: solo moduli Zentha autorizzati;
- ruoli personalizzati per singolo utente.

## Regole per la futura sincronizzazione
Prima del cloud verranno introdotti in una release dedicata:
- UUID globali per record sincronizzabili;
- createdAt / updatedAt;
- createdBy / updatedBy;
- versione record;
- stato sincronizzazione;
- soft-delete dove necessario;
- gestione conflitti;
- audit log.

Queste modifiche NON vengono aggiunte a Beta 5 per non aumentare il rischio di regressioni sul database reale.
