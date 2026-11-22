import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { PHASES, dueLabel, formatMeters, type Commessa } from './model'

// ---------------------------------------------------------------------------
// Icone lineari (24×24, tratto 1.75) — nessuna libreria esterna
// ---------------------------------------------------------------------------
const paths: Record<string, string> = {
  today: 'M4 6h16M4 12h10M4 18h7M18 15l2 2 3-4',
  orders: 'M4 5h6v6H4zM14 5h6v6h-6zM4 15h6v4H4zM14 15h6v4h-6z',
  zentha: 'M8 3l-4 3 2 3 2-1v13h8V8l2 1 2-3-4-3-2 2h-4z',
  stats: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-5-5',
  close: 'M6 6l12 12M18 6L6 18',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  back: 'M19 12H5M11 6l-6 6 6 6',
  check: 'M5 12l5 5 9-11',
  lock: 'M6 11h12v9H6zM9 11V8a3 3 0 0 1 6 0v3',
  alert: 'M12 4l9 16H3zM12 10v4M12 17v.5',
  printer: 'M7 9V4h10v5M6 18H4v-7h16v7h-2M7 14h10v6H7z',
  list: 'M8 6h12M8 12h12M8 18h12M4 6h.5M4 12h.5M4 18h.5',
  board: 'M4 4h5v16H4zM10 4h5v10h-5zM16 4h4v13h-4z',
  repeat: 'M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21v-1a6 6 0 0 1 12 0v1M16 3.5a4 4 0 0 1 0 7.5M22 21v-1a6 6 0 0 0-4-5.6',
  box: 'M3 7l9-4 9 4-9 4zM3 7v10l9 4 9-4V7M12 11v10',
  euro: 'M18 6a7 7 0 1 0 0 12M4 10h10M4 14h10',
  cloud: 'M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9a4.5 4.5 0 0 0 1 9z',
  grid: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z'
}

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={paths[name] || paths.check} />
    </svg>
  )
}

// ---------------------------------------------------------------------------
// Numeri animati e mini-grafici
// ---------------------------------------------------------------------------
export function AnimatedNumber({ value, format }: { value: number; format: (n: number) => string }) {
  const [shown, setShown] = useState(0)
  const from = useRef(0)
  useEffect(() => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduce) { setShown(value); return }
    const start = performance.now(); const initial = from.current
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / 700)
      const eased = 1 - Math.pow(1 - t, 3)
      setShown(initial + (value - initial) * eased)
      if (t < 1) frame = requestAnimationFrame(tick)
      else from.current = value
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return <>{format(shown)}</>
}

export function Sparkline({ values, tone = 'pink' }: { values: number[]; tone?: 'pink' | 'orange' }) {
  const max = Math.max(1, ...values)
  const w = 120, h = 36
  const points = values.map((v, i) => `${(i / Math.max(1, values.length - 1)) * w},${h - (v / max) * (h - 4) - 2}`).join(' ')
  return (
    <svg className={`spark ${tone}`} viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" aria-hidden="true">
      <polyline points={`0,${h} ${points} ${w},${h}`} className="spark-fill" />
      <polyline points={points} className="spark-line" />
    </svg>
  )
}

export function Bars({ values, labels, capacity }: { values: number[]; labels: string[]; capacity?: number }) {
  const max = Math.max(1, capacity || 0, ...values)
  return (
    <div className="bars" role="img" aria-label="Metri di carta stampati per giorno">
      {capacity ? <div className="bars-capacity" style={{ bottom: `${(capacity / max) * 100}%` }}><span>media {formatMeters(capacity)}</span></div> : null}
      {values.map((value, i) => (
        <div key={labels[i]} className="bar-slot" title={`${labels[i]}: ${formatMeters(value)}`}>
          <motion.div className={`bar ${value === 0 ? 'is-zero' : ''}`} initial={{ height: 0 }} animate={{ height: `${Math.max(2, (value / max) * 100)}%` }} transition={{ delay: i * 0.025, type: 'spring', stiffness: 140, damping: 20 }} />
        </div>
      ))}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Card e chip
// ---------------------------------------------------------------------------
export function Kpi({ label, value, format, hint, tone, spark, onClick }: {
  label: string; value: number; format: (n: number) => string; hint?: string; tone?: 'danger' | 'warning' | 'pink' | 'neutral'; spark?: number[]; onClick?: () => void
}) {
  return (
    <motion.button className={`kpi tone-${tone || 'neutral'}`} onClick={onClick} whileHover={{ y: -3 }} whileTap={{ scale: 0.98 }} layout>
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value"><AnimatedNumber value={value} format={format} /></strong>
      {hint ? <span className="kpi-hint">{hint}</span> : null}
      {spark ? <Sparkline values={spark} tone={tone === 'warning' ? 'orange' : 'pink'} /> : null}
    </motion.button>
  )
}

export const urgencyText = { late: 'In ritardo', soon: 'A rischio', ok: 'In tempo', none: 'Senza data' } as const

export function Dot({ urgency }: { urgency: Commessa['urgency'] }) {
  return <span className={`dot ${urgency}`} title={urgencyText[urgency]} />
}

export function PhaseChip({ c }: { c: Commessa }) {
  const label = c.phase === 'bloccato' ? 'Bloccato' : c.phase === 'annullato' ? 'Annullato' : PHASES.find(item => item.key === c.phase)?.label
  return <span className={`phase-chip ${c.phase}`}>{label}</span>
}

export function KindTag({ kind }: { kind: Commessa['kind'] }) {
  return <span className={`kind-tag ${kind}`}>{kind === 'zentha' ? 'Zentha' : 'Spectro'}</span>
}

export function OrderCard({ c, onOpen, compact }: { c: Commessa; onOpen: (c: Commessa) => void; compact?: boolean }) {
  return (
    <motion.button layout layoutId={compact ? undefined : `card-${c.key}`} className={`order-card urgency-${c.urgency}`} onClick={() => onOpen(c)}
      initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.96 }} whileHover={{ y: -2 }} whileTap={{ scale: 0.985 }}>
      <div className="order-top">
        <span className="order-client">{c.client}</span>
        <Dot urgency={c.urgency} />
      </div>
      <div className="order-title">{c.title || c.code}</div>
      <div className="order-meta">
        <KindTag kind={c.kind} />
        <span className="order-code">{c.code}{c.kind === 'zentha' && c.pieces ? ` · ${c.pieces} pz` : ''}{c.kind === 'spectro' && c.meters ? ` · ${formatMeters(c.meters)}` : ''}</span>
      </div>
      {!compact ? <div className={`order-due ${c.urgency}`}>{c.phase === 'bloccato' ? c.blockedReason : dueLabel(c)}</div> : null}
      <div className="progress"><motion.i initial={{ width: 0 }} animate={{ width: `${Math.round(c.progress * 100)}%` }} transition={{ duration: 0.8, ease: 'easeOut' }} /></div>
    </motion.button>
  )
}

// ---------------------------------------------------------------------------
// Pannello laterale, notifiche, barra comandi
// ---------------------------------------------------------------------------
export function Drawer({ open, onClose, children, title }: { open: boolean; onClose: () => void; children: ReactNode; title: string }) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])
  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.aside className="drawer" role="dialog" aria-label={title} initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', stiffness: 260, damping: 30 }}>
            <button className="icon-button drawer-close" onClick={onClose} aria-label="Chiudi"><Icon name="close" /></button>
            {children}
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  )
}

export type Toast = { id: number; text: string; tone?: 'ok' | 'error' }

export function Toasts({ items }: { items: Toast[] }) {
  return (
    <div className="toasts" aria-live="polite">
      <AnimatePresence>
        {items.map(item => (
          <motion.div key={item.id} className={`toast ${item.tone || 'ok'}`} initial={{ opacity: 0, y: 20, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10 }}>
            <Icon name={item.tone === 'error' ? 'alert' : 'check'} size={18} />{item.text}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}

export type Command = { id: string; label: string; hint: string; icon: string; run: () => void }

export function CommandPalette({ open, onClose, commands }: { open: boolean; onClose: () => void; commands: Command[] }) {
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (q ? commands.filter(cmd => `${cmd.label} ${cmd.hint}`.toLowerCase().includes(q)) : commands).slice(0, 9)
  }, [commands, query])
  useEffect(() => { if (open) { setQuery(''); setActive(0) } }, [open])
  useEffect(() => { setActive(0) }, [query])
  const run = (cmd?: Command) => { if (!cmd) return; onClose(); cmd.run() }
  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div className="palette" role="dialog" aria-label="Barra comandi" initial={{ opacity: 0, y: -16, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -8 }}>
            <div className="palette-input"><Icon name="search" /><input autoFocus value={query} placeholder="Cerca un ordine, un cliente o un’azione…"
              onChange={event => setQuery(event.target.value)}
              onKeyDown={event => {
                if (event.key === 'ArrowDown') { event.preventDefault(); setActive(i => Math.min(filtered.length - 1, i + 1)) }
                if (event.key === 'ArrowUp') { event.preventDefault(); setActive(i => Math.max(0, i - 1)) }
                if (event.key === 'Enter') run(filtered[active])
                if (event.key === 'Escape') onClose()
              }} /><kbd>Esc</kbd></div>
            <ul>
              {filtered.map((cmd, i) => (
                <li key={cmd.id}><button className={i === active ? 'active' : ''} onMouseEnter={() => setActive(i)} onClick={() => run(cmd)}>
                  <Icon name={cmd.icon} size={18} /><span>{cmd.label}</span><small>{cmd.hint}</small>
                </button></li>
              ))}
              {!filtered.length ? <li className="palette-empty">Nessun risultato per “{query}”</li> : null}
            </ul>
          </motion.div>
        </>
      ) : null}
    </AnimatePresence>
  )
}

export function Stepper({ c }: { c: Commessa }) {
  const index = PHASES.findIndex(phase => phase.key === c.phase)
  return (
    <ol className="stepper">
      {PHASES.map((phase, i) => (
        <li key={phase.key} className={i < index ? 'done' : i === index ? 'current' : ''}>
          <span className="stepper-dot">{i < index ? <Icon name="check" size={12} /> : null}</span>
          <span className="stepper-label">{phase.label}</span>
        </li>
      ))}
    </ol>
  )
}
