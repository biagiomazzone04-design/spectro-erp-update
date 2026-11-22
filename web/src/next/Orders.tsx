import { AnimatePresence, LayoutGroup, motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import { PHASES, byPriority, daysBetween, dueLabel, formatMeters, isOpen, todayIso, type Commessa, type Phase } from './model'
import { Dot, Icon, KindTag, OrderCard, PhaseChip } from './ui'
import { Empty } from './Today'

export type OrderFilter = 'all' | 'spectro' | 'zentha' | 'late' | 'blocked'

const FILTERS: { key: OrderFilter; label: string }[] = [
  { key: 'all', label: 'Tutti' },
  { key: 'zentha', label: 'Zentha' },
  { key: 'spectro', label: 'Spectro' },
  { key: 'late', label: 'In ritardo' },
  { key: 'blocked', label: 'Bloccati' }
]

export function Orders({ commesse, onOpen, filter, setFilter }: {
  commesse: Commessa[]; onOpen: (c: Commessa) => void; filter: OrderFilter; setFilter: (f: OrderFilter) => void
}) {
  const [view, setView] = useState<'board' | 'list'>('board')
  const [query, setQuery] = useState('')
  const today = todayIso()

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return commesse.filter(c => {
      if (c.phase === 'annullato') return false
      if (c.phase === 'consegnato' && (!c.due || daysBetween(c.due, today) > 30)) return false
      if (filter === 'spectro' && c.kind !== 'spectro') return false
      if (filter === 'zentha' && c.kind !== 'zentha') return false
      if (filter === 'late' && c.urgency !== 'late') return false
      if (filter === 'blocked' && c.phase !== 'bloccato') return false
      return !q || `${c.client} ${c.code} ${c.title} ${c.lines.map(l => l.name).join(' ')}`.toLowerCase().includes(q)
    }).sort(byPriority)
  }, [commesse, filter, query, today])

  const columns: { key: Phase; label: string; hint: string }[] = [
    { key: 'bloccato', label: 'Da sbloccare', hint: 'Materiale o cliente' },
    ...PHASES
  ]
  const counts = (f: OrderFilter) => commesse.filter(c => isOpen(c) && (f === 'all' || (f === 'late' ? c.urgency === 'late' : f === 'blocked' ? c.phase === 'bloccato' : c.kind === f))).length

  return (
    <div className="screen orders">
      <header className="screen-head">
        <div>
          <p className="eyebrow">Commesse</p>
          <h1>Ordini</h1>
          <p className="lead">Ordini Spectro e Zentha nelle stesse fasi. Tocca una card per il dettaglio.</p>
        </div>
        <div className="segmented" role="tablist" aria-label="Vista">
          <button className={view === 'board' ? 'on' : ''} onClick={() => setView('board')}><Icon name="board" size={16} />Fasi</button>
          <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}><Icon name="list" size={16} />Elenco</button>
        </div>
      </header>

      <div className="toolbar">
        <label className="search"><Icon name="search" size={18} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Cerca cliente, codice o prodotto" /></label>
        <div className="chips">
          {FILTERS.map(item => (
            <button key={item.key} className={`chip ${filter === item.key ? 'on' : ''}`} onClick={() => setFilter(item.key)}>
              {filter === item.key ? <motion.span layoutId="chip-bg" className="chip-bg" /> : null}
              <span>{item.label}</span><b>{counts(item.key)}</b>
            </button>
          ))}
        </div>
      </div>
      <p className="preview-note"><Icon name="lock" size={14} /> Anteprima in sola lettura: lo spostamento delle card tra le fasi arriva con la commessa unica.</p>

      <AnimatePresence mode="wait">
        {view === 'board' ? (
          <motion.div key="board" className="board" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <LayoutGroup>
              {columns.map(column => {
                const items = visible.filter(c => c.phase === column.key)
                if (column.key === 'bloccato' && !items.length) return null
                return (
                  <section key={column.key} className={`column col-${column.key}`}>
                    <header><span>{column.label}</span><b>{items.length}</b></header>
                    <small>{column.hint}</small>
                    <div className="column-body">
                      <AnimatePresence>{items.map(c => <OrderCard key={c.key} c={c} onOpen={onOpen} />)}</AnimatePresence>
                      {!items.length ? <div className="column-empty">Vuoto</div> : null}
                    </div>
                  </section>
                )
              })}
            </LayoutGroup>
          </motion.div>
        ) : (
          <motion.div key="list" className="table-wrap" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <table className="table">
              <thead><tr><th></th><th>Cliente</th><th>Ordine</th><th>Fase</th><th>Quantità</th><th>Consegna</th></tr></thead>
              <tbody>
                {visible.map(c => (
                  <tr key={c.key} onClick={() => onOpen(c)} tabIndex={0} onKeyDown={event => { if (event.key === 'Enter') onOpen(c) }}>
                    <td><Dot urgency={c.urgency} /></td>
                    <td><b>{c.client}</b><small>{c.title}</small></td>
                    <td><KindTag kind={c.kind} /> {c.code}</td>
                    <td><PhaseChip c={c} /></td>
                    <td>{c.kind === 'zentha' ? `${c.pieces} pz` : c.meters ? formatMeters(c.meters) : '—'}</td>
                    <td className={`due ${c.urgency}`}>{c.phase === 'bloccato' ? c.blockedReason : dueLabel(c)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!visible.length ? <Empty text="Nessun ordine con questi filtri." /> : null}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
