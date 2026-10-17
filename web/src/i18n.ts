const translations: Record<string, string> = {
  PRODUCTION: 'Produzione', NEGATIVE: 'Negativo', PRODUCTION_RECORD: 'Registrazione produttiva',
  WORK_ORDER: 'Ordine', INVENTORY_ITEM: 'Tessuto', FABRIC_ROLL: 'Collo', STOCK_MOVEMENT: 'Movimento di magazzino',
  ROLL_USAGE: 'Utilizzo tessuto', CLIENT: 'Cliente', CLIENT_ISSUE: 'Criticità cliente', ISSUE_UPDATE: 'Aggiornamento criticità',
  ORDER_TIMELINE_EVENT: 'Evento timeline', COTTON_ORDER: 'Ordine cotone', ZENTHA_ORDER: 'Ordine Zentha',
  ZENTHA_ORDER_ITEM: 'Articolo Zentha', ZENTHA_PRODUCT: 'Prodotto Zentha', ZENTHA_PAYMENT: 'Pagamento Zentha',
  ZENTHA_EXTRA_COST: 'Costo extra Zentha', PAYMENT_DEADLINE: 'Scadenza di pagamento', ECONOMIC_WORK: 'Lavoro economico',
  OPERATIONAL_TASK: 'Attività operativa', APP_SETTINGS: 'Impostazioni',

  NEW: 'Nuovo', TODO: 'Da fare', OPEN: 'Aperto', IN_PROCESS: 'In lavorazione', IN_PROGRESS: 'In corso',
  WAITING: 'In attesa', WAITING_CLIENT: 'Attesa cliente', WAITING_MATERIAL: 'Attesa materiale', ON_HOLD: 'Sospeso',
  READY: 'Pronto', COMPLETED: 'Completato', DONE: 'Completata', DELIVERED: 'Consegnato', CLOSED: 'Chiuso',
  CANCELLED: 'Annullato', PAID: 'Pagato', UNPAID: 'Da pagare', RESOLVED: 'Risolto', CONFIRMED: 'Confermato',
  PARTIALLY_PAID: 'Parzialmente pagato', ARCHIVED: 'Archiviato', DEPLETED: 'Esaurito', ACTIVE: 'Attivo',
  SUSPENDED: 'Sospeso',

  LOW: 'Bassa', NORMAL: 'Normale', MEDIUM: 'Media', HIGH: 'Alta', URGENT: 'Urgente', CRITICAL: 'Critica',
  INFO: 'Informativa', WARNING: 'Attenzione', BLOCKING: 'Bloccante', NONE: 'Nessuno', TO_VERIFY: 'Da verificare',

  GRAPHICS_SEARCH: 'Ricerca grafica', GRAPHIC_SEARCH: 'Ricerca grafica', SAMPLE: 'Campione', PREPARATION: 'Preparazione',
  OTHER: 'Altro', DESIGN: 'Disegno', RESEARCH: 'Ricerca', QUOTE: 'Preventivo', REMINDER: 'Promemoria',
  CUSTOMER_REQUEST: 'Richiesta cliente', INTERNAL: 'Interna', QUALITY: 'Qualità', DELAY: 'Ritardo', NOTE: 'Nota',
  DELIVERY: 'Consegna', MATERIAL: 'Materiale', MACHINE: 'Macchinario', CUSTOMER: 'Cliente', SUPPLIER: 'Fornitore',
  COMPANY: 'Azienda', HOUSE: 'Azienda', CLIENT_OWNED: 'Del cliente', REACTIVE: 'Reattiva', PIGMENT: 'Pigmento',

  CASH: 'Contanti', BANK_TRANSFER: 'Bonifico', CARD: 'Carta', CHECK: 'Assegno', UNSPECIFIED: 'Non specificato',
  DEFAULT: 'Dal ruolo', ALLOW: 'Consenti', DENY: 'Nega'
}

export function translateCode(value: unknown, fallback = '—') {
  const raw = String(value ?? '').trim()
  if (!raw) return fallback
  const normalized = raw.replace(/[\s-]+/g, '_').toUpperCase()
  if (translations[normalized]) return translations[normalized]
  return raw.replaceAll('_', ' ').toLowerCase().replace(/^./, character => character.toUpperCase())
}

export function moduleLabel(value: unknown) {
  const modules: Record<string, string> = {
    dashboard: 'Dashboard', clients: 'Clienti', orders: 'Ordini', production: 'Produzione', inventory: 'Magazzino',
    issues: 'Timeline e criticità', cotton: 'Cotone', zentha: 'Zentha', finance: 'Economia', tasks: 'Attività',
    settings: 'Impostazioni', reports: 'Report', backup: 'Backup', users: 'Utenti', audit: 'Registro attività', sync: 'Cloud'
  }
  const key = String(value ?? '').toLowerCase()
  return modules[key] || translateCode(value)
}

export function formatItalianDate(value: unknown, includeTime = false) {
  const raw = String(value ?? '')
  if (!raw) return '—'
  const parsed = new Date(raw.length === 10 ? `${raw}T12:00:00` : raw)
  if (Number.isNaN(parsed.getTime())) return raw
  return includeTime ? parsed.toLocaleString('it-IT') : parsed.toLocaleDateString('it-IT')
}

export function isoDate(value: unknown) {
  const raw = String(value ?? '')
  if (!raw) return ''
  const parsed = new Date(raw.length === 10 ? `${raw}T12:00:00` : raw)
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString().slice(0, 10)
}
