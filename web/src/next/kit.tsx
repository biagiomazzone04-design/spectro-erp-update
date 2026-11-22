import { motion } from 'framer-motion'
import { useMemo, useState, type ReactNode } from 'react'
import { formatDate, formatMeters, todayIso, type CloudRecord } from './model'
import { Icon } from './ui'

// Mattoni comuni delle sezioni: periodo, tabella, grafici, campi.

export const p = (row: CloudRecord) => row.payload
export const s = (value: unknown) => String(value ?? '').trim()
export const n = (value: unknown) => { const v = Number(value); return Number.isFinite(v) ? v : 0 }
export const euro = (value: number) => new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(value)
export const int = (value: number) => new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0 }).format(value)
export const terminal = /COMPLETED|DELIVERED|CLOSED|CANCELLED|DONE|RESOLVED|PAID|ARCHIVED/

const LABELS: Record<string, string> = {
  OPEN: 'Aperta', RESOLVED: 'Risolta', CLOSED: 'Chiusa', MONITORING: 'In osservazione', WAITING_CLIENT: 'Attesa cliente',
  LOW: 'Bassa', MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Critica', INFO: 'Info', BLOCKING: 'Bloccante',
  TODO: 'Da fare', IN_PROGRESS: 'In corso', DONE: 'Fatta', CANCELLED: 'Annullata', NORMAL: 'Normale',
  COMPLETED: 'Completata', PAID: 'Pagata', RECEIVED: 'Ricevuto', IN_PROCESS: 'In lavorazione', IN_PRODUCTION: 'In produzione',
  READY: 'Pronto', DELIVERED: 'Consegnato', REACTIVE: 'Reattivo', PIGMENT: 'Pigmento', OTHER: 'Altro',
  SAMPLE: 'Campione', GRAPHICS_SEARCH: 'Ricerca grafica', CLIENT_REQUEST: 'Richiesta cliente', PREPARATION: 'Preparazione',
  GRAPHICS: 'Grafica', APPROVAL: 'Approvazione', MATERIAL: 'Materiale', ORDER_CHANGE: 'Modifica ordine', PAYMENT: 'Pagamento',
  QUALITY: 'Qualità', INFORMATION: 'Informazioni', CLIENT: 'Cliente', INTERNAL: 'Interna', SUPPLIER: 'Fornitore', MIXED: 'Mista',
  TO_VERIFY: 'Da verificare', NONE: 'Nessuna', INTACT: 'Intatto', DEPLETED: 'Esaurito', BLOCKED: 'Bloccato', HOUSE: 'Aziendale'
}
export const label = (value: unknown) => LABELS[s(value)] || s(value).replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase()) || '—'

export function StatusPill({ value, tone }: { value: unknown; tone?: 'ok' | 'warn' | 'danger' | 'pink' | 'neutral' }) {
  const v = s(value)
  const auto = tone || (terminal.test(v) ? 'ok' : /BLOCK|CRITICAL|HIGH|LATE/.test(v) ? 'danger' : /WAIT|PROGRESS|MEDIUM|MONITOR|OPEN|TODO/.test(v) ? 'warn' : 'neutral')
  return <span className={`pill ${auto}`}>{label(v)}</span>
}

// ---------------------------------------------------------------------------
// Periodo
// ---------------------------------------------------------------------------
export type Period = { mode: string; start: string; end: string }
const shiftIso = (iso: string, days: number) => { const d = new Date(`${iso}T12:00:00`); d.setDate(d.getDate() + days); return d.toISOString().slice(0, 10) }

export function periodFor(mode: string): Period {
  const today = todayIso()
  if (mode === 'previous_month') {
    const d = new Date(`${today.slice(0, 7)}-01T12:00:00`); d.setMonth(d.getMonth() - 1)
    const start = d.toISOString().slice(0, 10)
    return { mode, start, end: shiftIso(`${today.slice(0, 7)}-01`, -1) }
  }
  if (mode === 'year') return { mode, start: `${today.slice(0, 4)}-01-01`, end: today }
  if (mode === 'all') return { mode, start: '2000-01-01', end: '2999-12-31' }
  if (/^\d+$/.test(mode)) return { mode, start: shiftIso(today, -Number(mode) + 1), end: today }
  return { mode: 'current_month', start: `${today.slice(0, 7)}-01`, end: today }
}

export const inPeriod = (date: string, period: Period) => !!date && date.slice(0, 10) >= period.start && date.slice(0, 10) <= period.end

export function PeriodPicker({ period, onChange }: { period: Period; onChange: (p: Period) => void }) {
  const options = [['current_month', 'Mese corrente'], ['previous_month', 'Mese precedente'], ['30', '30 giorni'], ['90', '90 giorni'], ['year', 'Anno'], ['all', 'Tutto']]
  return (
    <div className="period">
      <div className="segmented scroll">
        {options.map(([key, text]) => <button key={key} className={period.mode === key ? 'on' : ''} onClick={() => onChange(periodFor(key))}>{text}</button>)}
      </div>
      <label className="date-range">
        <input type="date" value={period.start} onChange={e => e.target.value && onChange({ mode: 'custom', start: e.target.value, end: period.end })} />
        <span>→</span>
        <input type="date" value={period.end} onChange={e => e.target.value && onChange({ mode: 'custom', start: period.start, end: e.target.value })} />
      </label>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Intestazione sezione e statistiche
// ---------------------------------------------------------------------------
export function Head({ eyebrow, title, lead, children }: { eyebrow: string; title: string; lead?: ReactNode; children?: ReactNode }) {
  return (
    <header className="screen-head">
      <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{lead ? <p className="lead">{lead}</p> : null}</div>
      {children ? <div className="head-actions">{children}</div> : null}
    </header>
  )
}

export type Stat = { label: string; value: string; hint?: string; tone?: 'pink' | 'danger' | 'warning' | 'ok' | 'neutral' }
export function Stats({ items }: { items: Stat[] }) {
  return (
    <section className="stat-grid">
      {items.map(item => (
        <motion.div key={item.label} className={`kpi tone-${item.tone || 'neutral'}`} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
          <span className="kpi-label">{item.label}</span>
          <strong className="kpi-value">{item.value}</strong>
          {item.hint ? <span className="kpi-hint">{item.hint}</span> : null}
        </motion.div>
      ))}
    </section>
  )
}

/** Colonne verticali per giorno (metri o importi). */
export function DailyChart({ days, values, format = formatMeters, color = 'pink' }: { days: string[]; values: number[]; format?: (v: number) => string; color?: 'pink' | 'orange' }) {
  const max = Math.max(1, ...values)
  const step = Math.max(1, Math.ceil(days.length / 10))
  return (
    <div className="daily-chart" role="img" aria-label="Andamento giornaliero">
      <div className="daily-bars" style={{ gridTemplateColumns: `repeat(${days.length}, 1fr)` }}>
        {values.map((value, i) => (
          <div key={days[i]} className="daily-slot" title={`${formatDate(days[i])}: ${format(value)}`}>
            <motion.div className={`daily-bar ${color} ${value === 0 ? 'is-zero' : ''}`} initial={{ height: 0 }} animate={{ height: `${Math.max(1.5, (value / max) * 100)}%` }} transition={{ delay: Math.min(i, 30) * 0.012, type: 'spring', stiffness: 150, damping: 22 }} />
          </div>
        ))}
      </div>
      <div className="daily-axis" style={{ gridTemplateColumns: `repeat(${days.length}, 1fr)` }}>
        {days.map((day, i) => <span key={day}>{i % step === 0 ? new Date(`${day}T12:00:00`).getDate() : ''}</span>)}
      </div>
    </div>
  )
}

export function daysOf(period: Period, data: string[]) {
  const end = period.mode === 'all' ? (data.sort().at(-1) || todayIso()) : period.end
  const start = period.mode === 'all' ? shiftIso(end, -29) : period.start
  const count = Math.min(92, Math.max(1, Math.round((Date.parse(end) - Date.parse(start)) / 86400000) + 1))
  return Array.from({ length: count }, (_, i) => shiftIso(end, i - count + 1))
}

/** Classifica orizzontale (clienti, plotter, tessuti…). */
export function Ranking({ rows, format }: { rows: { name: string; value: number; extra?: string }[]; format: (v: number) => string }) {
  const max = Math.max(1, ...rows.map(r => r.value))
  return (
    <div className="ranking">
      {rows.map((row, i) => (
        <div key={row.name} className="rank-row">
          <span className="rank-name">{row.name}{row.extra ? <small>{row.extra}</small> : null}</span>
          <div className="rank-track"><motion.i initial={{ width: 0 }} animate={{ width: `${(row.value / max) * 100}%` }} transition={{ delay: i * 0.04, duration: 0.6, ease: 'easeOut' }} /></div>
          <b>{format(row.value)}</b>
        </div>
      ))}
      {!rows.length ? <div className="empty">Nessun dato nel periodo.</div> : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Tabella con ricerca e ordinamento
// ---------------------------------------------------------------------------
export type Column<T> = { key: string; title: string; render: (row: T) => ReactNode; sort?: (row: T) => string | number; align?: 'right'; hideOnPhone?: boolean }

export function DataTable<T>({ rows, columns, onOpen, rowKey, empty = 'Nessun elemento.', initialSort, search }: {
  rows: T[]; columns: Column<T>[]; onOpen?: (row: T) => void; rowKey: (row: T) => string; empty?: string; initialSort?: { key: string; dir: 1 | -1 }; search?: (row: T) => string
}) {
  const [sort, setSort] = useState(initialSort || { key: '', dir: 1 as 1 | -1 })
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(60)
  const sorted = useMemo(() => {
    const q = query.trim().toLowerCase()
    const filtered = search && q ? rows.filter(row => search(row).toLowerCase().includes(q)) : rows
    const col = columns.find(c => c.key === sort.key)
    if (!col?.sort) return filtered
    return [...filtered].sort((a, b) => {
      const x = col.sort!(a), y = col.sort!(b)
      return (typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'it')) * sort.dir
    })
  }, [rows, columns, sort, query, search])
  return (
    <div className="table-block">
      {search ? <label className="search"><Icon name="search" size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Cerca…" /></label> : null}
      <div className="table-wrap">
        <table className="table">
          <thead><tr>{columns.map(col => (
            <th key={col.key} className={`${col.align === 'right' ? 'right' : ''} ${col.hideOnPhone ? 'hide-phone' : ''}`}>
              {col.sort ? <button className={`th-sort ${sort.key === col.key ? 'on' : ''}`} onClick={() => setSort(cur => ({ key: col.key, dir: cur.key === col.key ? (cur.dir === 1 ? -1 : 1) : -1 }))}>
                {col.title}{sort.key === col.key ? (sort.dir === 1 ? ' ↑' : ' ↓') : ''}
              </button> : col.title}
            </th>
          ))}</tr></thead>
          <tbody>
            {sorted.slice(0, limit).map(row => (
              <tr key={rowKey(row)} onClick={onOpen ? () => onOpen(row) : undefined} tabIndex={onOpen ? 0 : undefined} onKeyDown={e => { if (onOpen && e.key === 'Enter') onOpen(row) }} className={onOpen ? 'clickable' : ''}>
                {columns.map(col => <td key={col.key} className={`${col.align === 'right' ? 'right' : ''} ${col.hideOnPhone ? 'hide-phone' : ''}`}>{col.render(row)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
        {!sorted.length ? <div className="empty">{empty}</div> : null}
      </div>
      {sorted.length > limit ? <button className="ghost wide" onClick={() => setLimit(l => l + 100)}>Mostra altri {Math.min(100, sorted.length - limit)} di {sorted.length - limit}</button> : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Dettaglio generico di un record
// ---------------------------------------------------------------------------
export function Facts({ items }: { items: [string, ReactNode][] }) {
  return <dl className="facts">{items.filter(([, v]) => v !== null && v !== undefined && v !== '').map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
}

export function Section({ title, lead, children, actions }: { title: string; lead?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-head"><div><h2>{title}</h2>{lead ? <p>{lead}</p> : null}</div>{actions}</div>
      {children}
    </section>
  )
}

export const dateOf = (row: CloudRecord, ...keys: string[]) => keys.map(k => s(p(row)[k]).slice(0, 10)).find(Boolean) || ''
export const meters = formatMeters
