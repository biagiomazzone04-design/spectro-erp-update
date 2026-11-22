// Modello della nuova interfaccia: trasforma i record del mirror cloud in
// "commesse" uniche (ordini Spectro e Zentha) con fase, urgenza e avanzamento.

export type CloudRecord = {
  id: string
  module: string
  entity_type: string
  payload: Record<string, unknown>
  updated_at: string
  revision: number
}

export type Phase = 'ricevuto' | 'grafica' | 'approvazione' | 'stampa' | 'pronto' | 'consegnato' | 'bloccato' | 'annullato'

export const PHASES: { key: Phase; label: string; hint: string }[] = [
  { key: 'ricevuto', label: 'Ricevuto', hint: 'Ordine registrato' },
  { key: 'grafica', label: 'Grafica', hint: 'Bozza in preparazione' },
  { key: 'approvazione', label: 'Approvazione', hint: 'In attesa del cliente' },
  { key: 'stampa', label: 'In stampa', hint: 'Nel piano produttivo' },
  { key: 'pronto', label: 'Pronto', hint: 'Da consegnare' },
  { key: 'consegnato', label: 'Consegnato', hint: 'Ultimi 30 giorni' }
]

export type Urgency = 'late' | 'soon' | 'ok' | 'none'

export type Commessa = {
  key: string
  recordId: string
  kind: 'spectro' | 'zentha'
  code: string
  client: string
  title: string
  received: string
  due: string
  status: string
  phase: Phase
  blockedReason: string
  meters: number
  pieces: number
  printedMeters: number
  productions: number
  daysLeft: number | null
  urgency: Urgency
  progress: number
  lines: { name: string; quantity: number; sizes: string; variant: string }[]
  notes: string
  raw: CloudRecord
}

const p = (row: CloudRecord) => row.payload
const str = (value: unknown) => String(value ?? '').trim()
const num = (value: unknown) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

export const todayIso = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export const daysBetween = (from: string, to: string) =>
  Math.round((Date.parse(`${to}T12:00:00`) - Date.parse(`${from}T12:00:00`)) / 86400000)

function workPhase(status: string, blocker: string): { phase: Phase; reason: string } {
  if (blocker === 'WAITING_MATERIAL') return { phase: 'bloccato', reason: 'Manca il materiale' }
  if (blocker === 'WAITING_CLIENT') return { phase: 'bloccato', reason: 'In attesa del cliente' }
  switch (status) {
    case 'DRAFT': case 'RECEIVED': return { phase: 'ricevuto', reason: '' }
    case 'IN_PROCESS': case 'TRANSFERRED_TO_PRODUCTION': return { phase: 'grafica', reason: '' }
    case 'IN_PRODUCTION': case 'IN_PROGRESS': case 'PRINTED': case 'TRANSFERRED': return { phase: 'stampa', reason: '' }
    case 'READY': return { phase: 'pronto', reason: '' }
    case 'COMPLETED': case 'DELIVERED': case 'CLOSED': return { phase: 'consegnato', reason: '' }
    case 'WAITING_MATERIAL': return { phase: 'bloccato', reason: 'Manca il materiale' }
    case 'WAITING_CLIENT': return { phase: 'bloccato', reason: 'In attesa del cliente' }
    case 'ON_HOLD': case 'BLOCKED': return { phase: 'bloccato', reason: 'Sospeso' }
    case 'CANCELLED': return { phase: 'annullato', reason: '' }
    default: return { phase: 'ricevuto', reason: '' }
  }
}

function zenthaPhase(status: string): Phase {
  switch (status) {
    case 'DRAFT': case 'CONFIRMED': return 'ricevuto'
    case 'GRAPHICS': return 'grafica'
    case 'WAITING_APPROVAL': return 'approvazione'
    case 'PRODUCTION': return 'stampa'
    case 'READY': return 'pronto'
    case 'DELIVERED': case 'CLOSED': return 'consegnato'
    case 'CANCELLED': return 'annullato'
    default: return 'ricevuto'
  }
}

function urgencyOf(phase: Phase, daysLeft: number | null): Urgency {
  if (phase === 'consegnato' || phase === 'annullato' || daysLeft === null) return 'none'
  if (daysLeft < 0) return 'late'
  if (daysLeft <= 3) return 'soon'
  return 'ok'
}

const phaseProgress: Record<Phase, number> = {
  ricevuto: 0.1, grafica: 0.3, approvazione: 0.45, stampa: 0.65, pronto: 0.9, consegnato: 1, bloccato: 0, annullato: 0
}

/** Righe di un ordine Zentha: dentro l'ordine (Beta 19) o come record separati. */
function zenthaLines(order: CloudRecord, records: CloudRecord[]) {
  const inline = p(order).items
  const rows: Record<string, unknown>[] = Array.isArray(inline)
    ? inline.filter(line => line && typeof line === 'object') as Record<string, unknown>[]
    : records.filter(row => row.entity_type === 'zentha_order_item' && str(p(row).orderId) === str(p(order).id)).map(p)
  return rows.map(line => ({
    name: str(line.productName) || 'Prodotto',
    quantity: num(line.quantity),
    sizes: str(line.sizeBreakdown),
    variant: [str(line.variant), str(line.color)].filter(Boolean).join(' · ')
  }))
}

export function buildCommesse(records: CloudRecord[]): Commessa[] {
  const today = todayIso()
  const productions = records.filter(row => row.entity_type === 'production_record' && str(p(row).type || 'PRODUCTION') === 'PRODUCTION')
  const result: Commessa[] = []
  for (const row of records) {
    const x = p(row)
    if (row.entity_type === 'work_order') {
      const { phase, reason } = workPhase(str(x.status), str(x.blocker))
      const linked = productions.filter(prod => str(p(prod).workOrderId) === str(x.id))
      const due = str(x.dueDate)
      const daysLeft = due ? daysBetween(today, due) : null
      const meters = num(x.orderedMeters)
      const printed = linked.reduce((sum, prod) => sum + num(p(prod).printedPaperMeters), 0)
      result.push({
        key: `spectro-${str(x.id)}`, recordId: row.id, kind: 'spectro',
        code: str(x.orderCode) || `#${str(x.id)}`, client: str(x.client) || 'Cliente', title: str(x.title),
        received: str(x.receivedDate), due, status: str(x.status), phase, blockedReason: reason,
        meters, pieces: 0, printedMeters: printed, productions: linked.length, daysLeft,
        urgency: urgencyOf(phase, daysLeft),
        progress: meters > 0 && printed > 0 ? Math.min(1, printed / meters) : phaseProgress[phase],
        lines: [], notes: str(x.description), raw: row
      })
    }
    if (row.entity_type === 'zentha_order') {
      const phase = zenthaPhase(str(x.status))
      const lines = zenthaLines(row, records)
      const linked = productions.filter(prod => str(p(prod).zenthaOrderId) === str(x.id))
      const due = str(x.dueDate)
      const daysLeft = due ? daysBetween(today, due) : null
      result.push({
        key: `zentha-${str(x.id)}`, recordId: row.id, kind: 'zentha',
        code: str(x.orderCode) || `#${str(x.id)}`, client: str(x.clientName) || 'Cliente',
        title: lines.length ? lines.map(line => line.name).slice(0, 2).join(' + ') + (lines.length > 2 ? ` +${lines.length - 2}` : '') : 'Ordine Zentha',
        received: str(x.orderDate), due, status: str(x.status), phase, blockedReason: '',
        meters: 0, pieces: lines.reduce((sum, line) => sum + line.quantity, 0),
        printedMeters: linked.reduce((sum, prod) => sum + num(p(prod).printedPaperMeters), 0), productions: linked.length,
        daysLeft, urgency: urgencyOf(phase, daysLeft), progress: phaseProgress[phase],
        lines, notes: str(x.notes), raw: row
      })
    }
  }
  return result
}

export const isOpen = (c: Commessa) => c.phase !== 'consegnato' && c.phase !== 'annullato'

/** Ordine di lavoro: prima i ritardi, poi le consegne più vicine, poi senza data. */
export function byPriority(a: Commessa, b: Commessa) {
  const rank = (c: Commessa) => c.urgency === 'late' ? 0 : c.urgency === 'soon' ? 1 : c.urgency === 'ok' ? 2 : 3
  return rank(a) - rank(b) || (a.daysLeft ?? 9999) - (b.daysLeft ?? 9999) || a.received.localeCompare(b.received)
}

// ---------------------------------------------------------------------------
// Produzione per plotter
// ---------------------------------------------------------------------------

export type PlotterStats = {
  name: string
  today: number
  week: number
  avgPerDay: number
  daily: { date: string; meters: number }[]
}

export function plotterStats(records: CloudRecord[]): PlotterStats[] {
  const today = todayIso()
  const productions = records.filter(row => row.entity_type === 'production_record' && str(p(row).type || 'PRODUCTION') === 'PRODUCTION')
  const plotters = [...new Set(productions.map(row => str(p(row).plotter) || 'X1'))].sort()
  const weekStart = (() => {
    const d = new Date(`${today}T12:00:00`)
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
    return d.toISOString().slice(0, 10)
  })()
  const days = Array.from({ length: 14 }, (_, i) => {
    const d = new Date(`${today}T12:00:00`)
    d.setDate(d.getDate() - (13 - i))
    return d.toISOString().slice(0, 10)
  })
  return plotters.map(name => {
    const own = productions.filter(row => (str(p(row).plotter) || 'X1') === name)
    const perDay = new Map<string, number>()
    own.forEach(row => {
      const date = str(p(row).date)
      perDay.set(date, (perDay.get(date) || 0) + num(p(row).printedPaperMeters))
    })
    const active = [...perDay.values()].filter(value => value > 0)
    return {
      name,
      today: perDay.get(today) || 0,
      week: [...perDay.entries()].filter(([date]) => date >= weekStart && date <= today).reduce((sum, [, value]) => sum + value, 0),
      avgPerDay: active.length ? active.reduce((sum, value) => sum + value, 0) / active.length : 0,
      daily: days.map(date => ({ date, meters: perDay.get(date) || 0 }))
    }
  })
}

export function monthRevenue(records: CloudRecord[]) {
  const month = todayIso().slice(0, 7)
  return records
    .filter(row => row.entity_type === 'production_record' && str(p(row).date).startsWith(month))
    .reduce((sum, row) => sum + num(p(row).grossRevenue), 0)
}

export const formatMeters = (value: number) => `${new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0 }).format(value)} m`
export const formatEuro = (value: number) => new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(value)
export const formatPrice = (value: number) => new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
export const formatDate = (iso: string) => {
  if (!iso) return '—'
  const d = new Date(`${iso}T12:00:00`)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('it-IT', { day: 'numeric', month: 'short' })
}
export const dueLabel = (c: Commessa) => {
  if (c.daysLeft === null) return 'Senza consegna'
  if (c.phase === 'consegnato') return `Consegnato · ${formatDate(c.due)}`
  if (c.daysLeft < 0) return `In ritardo di ${-c.daysLeft} g`
  if (c.daysLeft === 0) return 'Consegna oggi'
  if (c.daysLeft === 1) return 'Consegna domani'
  return `Consegna tra ${c.daysLeft} g · ${formatDate(c.due)}`
}
