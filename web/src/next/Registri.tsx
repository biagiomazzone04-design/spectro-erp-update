import { motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import type { CloudRecord } from './model'
import { daysBetween, formatDate, todayIso } from './model'
import { DataTable, Facts, Head, PeriodPicker, Section, StatusPill, Stats, euro, inPeriod, label, meters, n, p, periodFor, s, terminal, type Column } from './kit'
import { Drawer } from './ui'

function RecordDrawer({ row, onClose, title, facts, text }: { row: CloudRecord | null; onClose: () => void; title: (r: CloudRecord) => string; facts: (r: CloudRecord) => [string, string][]; text?: (r: CloudRecord) => string }) {
  return (
    <Drawer open={!!row} onClose={onClose} title="Dettaglio">
      {row ? (
        <div className="detail">
          <div className="detail-tags"><StatusPill value={p(row).status ?? (p(row).paid ? 'PAID' : 'OPEN')} /></div>
          <h2>{title(row)}</h2>
          <Facts items={facts(row)} />
          {text && text(row) ? <section><h3>Descrizione</h3><p className="notes">{text(row)}</p></section> : null}
        </div>
      ) : null}
    </Drawer>
  )
}

// ---------------------------------------------------------------------------
export function Criticita({ records }: { records: CloudRecord[] }) {
  const [view, setView] = useState<'open' | 'all'>('open')
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const rows = useMemo(() => records.filter(r => ['client_issue', 'order_timeline_event'].includes(r.entity_type)), [records])
  const isOpen = (r: CloudRecord) => !/RESOLVED|CLOSED/.test(s(p(r).status))
  const visible = rows.filter(r => view === 'all' || isOpen(r))
  const columns: Column<CloudRecord>[] = [
    { key: 'date', title: 'Data', render: r => formatDate(s(p(r).openedDate || p(r).date)), sort: r => s(p(r).openedDate || p(r).date) },
    { key: 'title', title: 'Criticità', render: r => <><b>{s(p(r).title)}</b><small>{r.entity_type === 'client_issue' ? 'Criticità cliente' : 'Evento ordine'} · {label(p(r).category)}</small></>, sort: r => s(p(r).title) },
    { key: 'client', title: 'Cliente', render: r => <>{s(p(r).clientName) || '—'}<small>{s(p(r).orderRef)}</small></>, sort: r => s(p(r).clientName), hideOnPhone: true },
    { key: 'sev', title: 'Gravità', render: r => <StatusPill value={p(r).severity} />, sort: r => ['LOW', 'INFO', 'MEDIUM', 'HIGH', 'CRITICAL', 'BLOCKING'].indexOf(s(p(r).severity)) },
    { key: 'status', title: 'Stato', render: r => <StatusPill value={p(r).status} />, sort: r => s(p(r).status), hideOnPhone: true }
  ]
  return (
    <div className="screen">
      <Head eyebrow="Controllo operativo" title="Timeline e criticità" lead="Ritardi, problemi e responsabilità, collegati a clienti e ordini." />
      <Stats items={[
        { label: 'Aperte', value: String(rows.filter(isOpen).length), tone: rows.some(isOpen) ? 'warning' : 'ok' },
        { label: 'Gravi o bloccanti', value: String(rows.filter(r => isOpen(r) && /HIGH|CRITICAL|BLOCKING/.test(s(p(r).severity))).length), tone: 'danger' },
        { label: 'Risolte', value: String(rows.filter(r => !isOpen(r)).length), tone: 'ok' },
        { label: 'Giorni di ritardo documentati', value: String(rows.reduce((t, r) => t + n(p(r).delayDays), 0)) }
      ]} />
      <Section title="Registro" actions={<div className="segmented small"><button className={view === 'open' ? 'on' : ''} onClick={() => setView('open')}>Aperte</button><button className={view === 'all' ? 'on' : ''} onClick={() => setView('all')}>Tutte</button></div>}>
        <DataTable rows={visible} columns={columns} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'date', dir: -1 }} search={r => JSON.stringify(p(r))} empty="Nessuna criticità." />
      </Section>
      <RecordDrawer row={open} onClose={() => setOpen(null)} title={r => s(p(r).title)} text={r => [s(p(r).description), s(p(r).resolutionNote), s(p(r).internalNotes)].filter(Boolean).join('\n\n')}
        facts={r => [['Cliente', s(p(r).clientName)], ['Ordine', s(p(r).orderRef)], ['Categoria', label(p(r).category)], ['Responsabilità', label(p(r).responsibility)], ['Gravità', label(p(r).severity)], ['Data', formatDate(s(p(r).openedDate || p(r).date))], ['Ritardo', n(p(r).delayDays) ? `${n(p(r).delayDays)} giorni` : '']]} />
    </div>
  )
}

// ---------------------------------------------------------------------------
export function Cotone({ records, canFinance }: { records: CloudRecord[]; canFinance: boolean }) {
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const rows = useMemo(() => records.filter(r => r.entity_type === 'cotton_order'), [records])
  const active = rows.filter(r => !terminal.test(s(p(r).status)))
  const columns: Column<CloudRecord>[] = [
    { key: 'date', title: 'Data', render: r => formatDate(s(p(r).orderDate)), sort: r => s(p(r).orderDate) },
    { key: 'client', title: 'Cliente / ordine', render: r => <><b>{s(p(r).clientName)}</b><small>{s(p(r).orderCode)} · {s(p(r).title)}</small></>, sort: r => s(p(r).clientName) },
    { key: 'fabric', title: 'Tessuto', render: r => <>{s(p(r).fabricName)}<small>{label(p(r).printType)}</small></>, hideOnPhone: true },
    { key: 'progress', title: 'Avanzamento', render: r => { const o = n(p(r).orderedMeters), d = n(p(r).actualProducedMeters); return <div className="stock"><span><b>{meters(d)}</b> / {meters(o)}</span><div className="stock-track"><motion.i initial={{ width: 0 }} animate={{ width: `${o ? Math.min(100, (d / o) * 100) : 0}%` }} /></div></div> }, sort: r => n(p(r).actualProducedMeters) / Math.max(1, n(p(r).orderedMeters)) },
    { key: 'status', title: 'Stato', render: r => <StatusPill value={p(r).status} />, sort: r => s(p(r).status) }
  ]
  return (
    <div className="screen">
      <Head eyebrow="Linea" title="Cotone" lead="Ordini in reattivo e pigmento, con metri prodotti e consegnati." />
      <Stats items={[
        { label: 'Ordini attivi', value: String(active.length), tone: 'pink' },
        { label: 'Metri ordinati (attivi)', value: meters(active.reduce((t, r) => t + n(p(r).orderedMeters), 0)) },
        { label: 'Metri prodotti (attivi)', value: meters(active.reduce((t, r) => t + n(p(r).actualProducedMeters), 0)) },
        { label: 'Difettosi', value: meters(rows.reduce((t, r) => t + n(p(r).defectiveMeters), 0)), tone: 'warning' }
      ]} />
      <Section title="Ordini cotone">
        <DataTable rows={rows} columns={columns} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'date', dir: -1 }} search={r => JSON.stringify(p(r))} empty="Nessun ordine cotone." />
      </Section>
      <RecordDrawer row={open} onClose={() => setOpen(null)} title={r => `${s(p(r).clientName)} · ${s(p(r).orderCode)}`} text={r => s(p(r).notes)}
        facts={r => [['Titolo', s(p(r).title)], ['Tessuto', s(p(r).fabricName)], ['Stampa', label(p(r).printType)], ['Ordinati', meters(n(p(r).orderedMeters))], ['Prodotti', meters(n(p(r).actualProducedMeters))], ['Consegnati', meters(n(p(r).deliveredMeters))], ['Difettosi', meters(n(p(r).defectiveMeters))], ['Consegna', formatDate(s(p(r).dueDate))], ...(canFinance ? [['Prezzo stampa / m', euro(n(p(r).printPricePerMeter))]] as [string, string][] : [])]} />
    </div>
  )
}

// ---------------------------------------------------------------------------
export function Lavorazioni({ records }: { records: CloudRecord[] }) {
  const [period, setPeriod] = useState(periodFor('year'))
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const today = todayIso()
  const works = useMemo(() => records.filter(r => r.entity_type === 'economic_work' && inPeriod(s(p(r).date), period)), [records, period])
  const payments = useMemo(() => records.filter(r => r.entity_type === 'payment_deadline'), [records])
  const gross = (r: CloudRecord) => n(p(r).manualGrossRevenue) || n(p(r).quantity) * n(p(r).unitPrice)
  const costs = (r: CloudRecord) => (Array.isArray(p(r).materials) ? (p(r).materials as Record<string, unknown>[]).reduce((t, m) => t + n(m.quantity) * n(m.unitCost), 0) : 0) + n(p(r).otherCosts)
  const unpaid = payments.filter(r => p(r).paid !== true)
  const overdue = unpaid.filter(r => s(p(r).dueDate) && s(p(r).dueDate) < today)
  const workCols: Column<CloudRecord>[] = [
    { key: 'date', title: 'Data', render: r => formatDate(s(p(r).date)), sort: r => s(p(r).date) },
    { key: 'title', title: 'Lavorazione', render: r => <><b>{s(p(r).title)}</b><small>{s(p(r).clientName)}</small></>, sort: r => s(p(r).title) },
    { key: 'gross', title: 'Fatturato', render: r => euro(gross(r)), sort: gross, align: 'right' },
    { key: 'net', title: 'Utile', render: r => <span className={gross(r) - costs(r) >= 0 ? 'text-ok' : 'text-danger'}>{euro(gross(r) - costs(r))}</span>, sort: r => gross(r) - costs(r), align: 'right', hideOnPhone: true },
    { key: 'status', title: 'Stato', render: r => <StatusPill value={p(r).status} />, sort: r => s(p(r).status), hideOnPhone: true }
  ]
  const payCols: Column<CloudRecord>[] = [
    { key: 'due', title: 'Scadenza', render: r => { const d = s(p(r).dueDate); const late = p(r).paid !== true && d && d < today; return <span className={late ? 'text-danger' : ''}>{formatDate(d)}{late ? <small>scaduta da {daysBetween(d, today)} g</small> : null}</span> }, sort: r => s(p(r).dueDate) },
    { key: 'client', title: 'Cliente', render: r => <><b>{s(p(r).clientName)}</b><small>{s(p(r).orderRef)}</small></>, sort: r => s(p(r).clientName) },
    { key: 'amount', title: 'Importo', render: r => euro(n(p(r).amount)), sort: r => n(p(r).amount), align: 'right' },
    { key: 'paid', title: 'Stato', render: r => <StatusPill value={p(r).paid ? 'PAID' : 'OPEN'} tone={p(r).paid ? 'ok' : 'warn'} />, sort: r => (p(r).paid ? 1 : 0) }
  ]
  return (
    <div className="screen">
      <Head eyebrow="Controllo" title="Ulteriori lavorazioni" lead="Beni personalizzati, trasferimenti, ricavi e incassi." />
      <PeriodPicker period={period} onChange={setPeriod} />
      <Stats items={[
        { label: 'Fatturato lavorazioni', value: euro(works.reduce((t, r) => t + gross(r), 0)), tone: 'pink' },
        { label: 'Utile lavorazioni', value: euro(works.reduce((t, r) => t + gross(r) - costs(r), 0)), tone: 'ok' },
        { label: 'Da incassare', value: euro(unpaid.reduce((t, r) => t + n(p(r).amount), 0)), hint: `${unpaid.length} scadenze` },
        { label: 'Scaduto', value: euro(overdue.reduce((t, r) => t + n(p(r).amount), 0)), hint: `${overdue.length} scadenze`, tone: overdue.length ? 'danger' : 'ok' }
      ]} />
      <div className="two-col">
        <Section title="Lavorazioni del periodo"><DataTable rows={works} columns={workCols} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'date', dir: -1 }} search={r => JSON.stringify(p(r))} empty="Nessuna lavorazione nel periodo." /></Section>
        <Section title="Scadenze e incassi"><DataTable rows={payments} columns={payCols} rowKey={r => r.id} onOpen={setOpen} initialSort={{ key: 'due', dir: 1 }} empty="Nessuna scadenza." /></Section>
      </div>
      <RecordDrawer row={open} onClose={() => setOpen(null)} title={r => s(p(r).title) || `${s(p(r).clientName)} · ${s(p(r).orderRef)}`} text={r => s(p(r).notes)}
        facts={r => r.entity_type === 'economic_work'
          ? [['Cliente', s(p(r).clientName)], ['Data', formatDate(s(p(r).date))], ['Quantità', String(n(p(r).quantity))], ['Fatturato', euro(gross(r))], ['Costi', euro(costs(r))], ['Utile', euro(gross(r) - costs(r))]]
          : [['Cliente', s(p(r).clientName)], ['Riferimento', s(p(r).orderRef)], ['Importo', euro(n(p(r).amount))], ['Scadenza', formatDate(s(p(r).dueDate))], ['Pagato il', formatDate(s(p(r).paidDate))], ['Metodo', label(p(r).method)]]} />
    </div>
  )
}

// ---------------------------------------------------------------------------
export function Attivita({ records }: { records: CloudRecord[] }) {
  const [open, setOpen] = useState<CloudRecord | null>(null)
  const today = todayIso()
  const tasks = useMemo(() => records.filter(r => r.entity_type === 'operational_task'), [records])
  const columns: { key: string; title: string; match: (r: CloudRecord) => boolean }[] = [
    { key: 'todo', title: 'Da fare', match: r => !['IN_PROGRESS', 'DONE', 'CANCELLED'].includes(s(p(r).status)) },
    { key: 'progress', title: 'In corso', match: r => s(p(r).status) === 'IN_PROGRESS' },
    { key: 'done', title: 'Fatte · ultime', match: r => s(p(r).status) === 'DONE' }
  ]
  const sortTasks = (a: CloudRecord, b: CloudRecord) => (s(p(a).dueDate) || '9999').localeCompare(s(p(b).dueDate) || '9999')
  return (
    <div className="screen">
      <Head eyebrow="Organizzazione" title="Attività" lead="Campioni, grafiche, richieste e preparazioni, ordinate per scadenza." />
      <Stats items={[
        { label: 'Da fare', value: String(tasks.filter(columns[0].match).length), tone: 'pink' },
        { label: 'In corso', value: String(tasks.filter(columns[1].match).length) },
        { label: 'Scadute', value: String(tasks.filter(r => !terminal.test(s(p(r).status)) && s(p(r).dueDate) && s(p(r).dueDate) < today).length), tone: 'danger' },
        { label: 'Completate', value: String(tasks.filter(columns[2].match).length), tone: 'ok' }
      ]} />
      <p className="preview-note">Anteprima in sola lettura: per spostare un’attività usa il portale classico o l’app.</p>
      <div className="board three">
        {columns.map(col => {
          const items = tasks.filter(col.match).sort(sortTasks)
          const shown = col.key === 'done' ? [...items].reverse().slice(0, 12) : items
          return (
            <section key={col.key} className={`column col-${col.key}`}>
              <header><span>{col.title}</span><b>{items.length}</b></header>
              <div className="column-body">
                {shown.map(r => {
                  const due = s(p(r).dueDate); const late = !terminal.test(s(p(r).status)) && due && due < today
                  return (
                    <motion.button key={r.id} className={`order-card ${late ? 'urgency-late' : ''}`} onClick={() => setOpen(r)} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} whileHover={{ y: -2 }}>
                      <div className="order-top"><span className="order-client">{s(p(r).title)}</span>{s(p(r).priority) === 'HIGH' ? <span className="pill danger">Alta</span> : null}</div>
                      <div className="order-title">{s(p(r).clientName) || label(p(r).type)}</div>
                      <div className={`order-due ${late ? 'late' : ''}`}>{due ? (late ? `Scaduta il ${formatDate(due)}` : `Entro ${formatDate(due)}`) : 'Senza scadenza'}</div>
                    </motion.button>
                  )
                })}
                {!shown.length ? <div className="column-empty">Vuoto</div> : null}
              </div>
            </section>
          )
        })}
      </div>
      <RecordDrawer row={open} onClose={() => setOpen(null)} title={r => s(p(r).title)} text={r => s(p(r).description)}
        facts={r => [['Tipo', label(p(r).type)], ['Cliente', s(p(r).clientName)], ['Priorità', label(p(r).priority)], ['Creata il', formatDate(s(p(r).createdDate))], ['Scadenza', formatDate(s(p(r).dueDate))]]} />
    </div>
  )
}
