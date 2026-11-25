# Spectro Beta 18 — Design QA

## Riferimenti verificati

- Dashboard desktop con priorità, KPI e consegne imminenti.
- Kanban Ordini unificato Spectro/Zentha per fase.
- Wizard Zentha desktop e mobile per cliente, sport, destinatario, prodotti, taglie e riepilogo.
- Dashboard direzionale con gerarchia tipografica e colori funzionali.

## Controlli eseguiti

- Il frontend TypeScript e CSS compila senza errori con Vite.
- Le nuove viste riutilizzano il sistema tipografico e cromatico Spectro esistente.
- La bacheca Ordini resta leggibile con scorrimento orizzontale su viewport stretti.
- Il wizard Zentha usa step espliciti, pulsanti sempre raggiungibili e campi raggruppati per decisione.
- Nessun prezzo viene mostrato o salvato dai profili privi di `finance.view`.
- I dossier Zentha aperti dalla vista Ordini usano i campi, le azioni e i PDF corretti.
- Modali e drawer rilasciano sempre il blocco dello scroll alla chiusura.
- I form di creazione mostrano errori contestuali senza perdere i valori già inseriti.
- Android mantiene testi adattivi, editor a schermo intero e bozza Zentha locale persistente.
- Sono rispettati contrasto semantico, target interattivi e `prefers-reduced-motion` già presenti.

## Esito

final result: passed
