import { AnimatePresence, motion } from 'framer-motion'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { buildCatalog } from './catalog'
import { buildCommesse, dueLabel, formatDate, formatMeters, monthRevenue, plotterStats, type CloudRecord, type Commessa } from './model'
import { Orders, type OrderFilter } from './Orders'
import { Today } from './Today'
import { Zentha } from './Zentha'
import { createZenthaOrder, currentSession, isDemo, loadAccess, loadRecords, signIn, supabase, type Access, type NewZenthaLine } from './source'
import { CommandPalette, Drawer, Icon, KindTag, PhaseChip, Stepper, Toasts, urgencyText, type Command, type Toast } from './ui'

type View = 'oggi' | 'ordini' | 'zentha'
const NAV: { key: View; label: string; icon: string }[] = [
  { key: 'oggi', label: 'Oggi', icon: 'today' },
  { key: 'ordini', label: 'Ordini', icon: 'orders' },
  { key: 'zentha', label: 'Zentha', icon: 'zentha' }
]

const viewFromHash = (): View => {
  const hash = location.hash.replace('#', '')
  return hash === 'ordini' || hash === 'zentha' ? hash : 'oggi'
}

export function App() {
  const [phase, setPhase] = useState<'loading' | 'login' | 'ready' | 'error'>('loading')
  const [message, setMessage] = useState('')
  const [access, setAccess] = useState<Access | null>(null)
  const [records, setRecords] = useState<CloudRecord[]>([])
  const [view, setView] = useState<View>(viewFromHash)
  const [filter, setFilter] = useState<OrderFilter>('all')
  const [zenthaNew, setZenthaNew] = useState(0)
  const [selected, setSelected] = useState<Commessa | null>(null)
  const [palette, setPalette] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])
  const [refreshing, setRefreshing] = useState(false)

  const toast = useCallback((text: string, tone: Toast['tone'] = 'ok') => {
    const id = Date.now() + Math.random()
    setToasts(items => [...items, { id, text, tone }])
    setTimeout(() => setToasts(items => items.filter(item => item.id !== id)), 4200)
  }, [])

  const load = useCallback(async (silent = false) => {
    try {
      if (!silent) setPhase('loading')
      if (!isDemo() && !(await currentSession())) { setPhase('login'); return }
      const nextAccess = await loadAccess()
      setAccess(nextAccess)
      setRecords(await loadRecords(nextAccess))
      setPhase('ready')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Impossibile caricare i dati.')
      setPhase('error')
    }
  }, [])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    const onHash = () => setView(viewFromHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setPalette(open => !open) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  // Aggiornamento automatico ogni 60 s quando la scheda è visibile.
  useEffect(() => {
    if (phase !== 'ready' || access?.demo) return
    const timer = setInterval(() => { if (document.visibilityState === 'visible' && !selected) void load(true) }, 60000)
    return () => clearInterval(timer)
  }, [phase, access, selected, load])

  const commesse = useMemo(() => buildCommesse(records), [records])
  const catalog = useMemo(() => buildCatalog(records), [records])
  const plotters = useMemo(() => plotterStats(records), [records])
  const revenue = useMemo(() => monthRevenue(records), [records])

  const go = (next: View) => { location.hash = next; setView(next) }
  const goFromToday = (target: 'orders' | 'zentha', extra?: string) => {
    if (target === 'zentha') { if (extra === 'new') setZenthaNew(n => n + 1); go('zentha'); return }
    setFilter((extra as OrderFilter) || 'all'); go('ordini')
  }

  const commands: Command[] = useMemo(() => [
    { id: 'new-zentha', label: 'Nuovo ordine Zentha', hint: 'Azione', icon: 'plus', run: () => goFromToday('zentha', 'new') },
    { id: 'go-today', label: 'Vai a Oggi', hint: 'Navigazione', icon: 'today', run: () => go('oggi') },
    { id: 'go-orders', label: 'Vai a Ordini', hint: 'Navigazione', icon: 'orders', run: () => go('ordini') },
    { id: 'go-late', label: 'Ordini in ritardo', hint: 'Filtro', icon: 'alert', run: () => goFromToday('orders', 'late') },
    ...commesse.map(c => ({ id: c.key, label: `${c.client} · ${c.code}`, hint: `${c.kind === 'zentha' ? 'Zentha' : 'Spectro'} · ${dueLabel(c)}`, icon: c.kind === 'zentha' ? 'zentha' : 'orders', run: () => setSelected(c) }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [commesse])

  const createOrder = async (order: Record<string, unknown>, items: NewZenthaLine[]) => {
    if (!access) return 'Sessione non disponibile.'
    try {
      const result = await createZenthaOrder(access, order, items)
      toast(`Ordine ${result.order_code} creato${access.demo ? ' (demo, non salvato)' : ': arriverà anche sul telefono'}`)
      if (!access.demo) await load(true)
      return null
    } catch (error) {
      const text = error instanceof Error ? error.message : 'Creazione non riuscita.'
      toast(text, 'error')
      return text
    }
  }

  if (phase === 'loading') return <Splash />
  if (phase === 'login') return <Login onDone={() => load()} />
  if (phase === 'error' || !access) return <Splash error={message} onRetry={() => load()} />

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark" />SPECTRO</div>
        <nav>
          {NAV.map(item => (
            <button key={item.key} className={view === item.key ? 'active' : ''} onClick={() => go(item.key)} aria-current={view === item.key ? 'page' : undefined}>
              {view === item.key ? <motion.span layoutId="nav-pill" className="nav-pill" transition={{ type: 'spring', stiffness: 400, damping: 34 }} /> : null}
              <Icon name={item.icon} /><span>{item.label}</span>
            </button>
          ))}
        </nav>
        <button className="search-trigger" onClick={() => setPalette(true)}><Icon name="search" size={18} /><span>Cerca</span><kbd>Ctrl K</kbd></button>
        <div className="sidebar-foot">
          <span className="who"><b>{access.name}</b><small>{access.demo ? 'Dati dimostrativi' : access.role || 'Utente'}</small></span>
          <a className="classic-link" href="/"><Icon name="external" size={16} />Portale classico</a>
        </div>
      </aside>

      <main className="main">
        <div className="topbar">
          <span className="beta-badge">Nuova interfaccia · anteprima</span>
          {access.demo ? <span className="demo-badge">Dati di esempio</span> : null}
          <button className="ghost refresh" onClick={async () => { setRefreshing(true); await load(true); setRefreshing(false); toast('Dati aggiornati') }} disabled={refreshing || access.demo}>
            <motion.span animate={{ rotate: refreshing ? 360 : 0 }} transition={{ repeat: refreshing ? Infinity : 0, duration: 0.9, ease: 'linear' }}><Icon name="repeat" size={16} /></motion.span>Aggiorna
          </button>
        </div>
        <AnimatePresence mode="wait">
          <motion.div key={view} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.22 }}>
            {view === 'oggi' ? <Today name={access.name} commesse={commesse} plotters={plotters} revenue={revenue} canFinance={access.canFinance} onOpen={setSelected} onGo={goFromToday} /> : null}
            {view === 'ordini' ? <Orders commesse={commesse} onOpen={setSelected} filter={filter} setFilter={setFilter} /> : null}
            {view === 'zentha' ? <Zentha key={zenthaNew} catalog={catalog} commesse={commesse} canFinance={access.canFinance} canCreate={access.canManageZentha} startNew={zenthaNew > 0} onOpen={setSelected} onCreate={createOrder} /> : null}
          </motion.div>
        </AnimatePresence>
      </main>

      <nav className="tabbar">
        {NAV.map(item => (
          <button key={item.key} className={view === item.key ? 'active' : ''} onClick={() => go(item.key)}><Icon name={item.icon} /><span>{item.label}</span></button>
        ))}
        <button onClick={() => setPalette(true)}><Icon name="search" /><span>Cerca</span></button>
      </nav>

      <Drawer open={!!selected} onClose={() => setSelected(null)} title={selected?.code || 'Dettaglio'}>
        {selected ? <Detail c={selected} /> : null}
      </Drawer>
      <CommandPalette open={palette} onClose={() => setPalette(false)} commands={commands} />
      <Toasts items={toasts} />
    </div>
  )
}

function Detail({ c }: { c: Commessa }) {
  return (
    <div className="detail">
      <div className="detail-tags"><KindTag kind={c.kind} /><PhaseChip c={c} /><span className={`due ${c.urgency}`}>{urgencyText[c.urgency]}</span></div>
      <h2>{c.client}</h2>
      <p className="detail-sub">{c.code}{c.title ? ` · ${c.title}` : ''}</p>
      <Stepper c={c} />
      <dl className="facts">
        <div><dt>Ricevuto</dt><dd>{formatDate(c.received)}</dd></div>
        <div><dt>Consegna</dt><dd className={`due ${c.urgency}`}>{c.phase === 'bloccato' ? c.blockedReason : dueLabel(c)}</dd></div>
        {c.kind === 'spectro' ? <div><dt>Metri ordinati</dt><dd>{c.meters ? formatMeters(c.meters) : '—'}</dd></div> : <div><dt>Pezzi</dt><dd>{c.pieces}</dd></div>}
        <div><dt>Carta stampata</dt><dd>{c.printedMeters ? formatMeters(c.printedMeters) : '—'} <small>{c.productions} produzioni</small></dd></div>
      </dl>
      {c.lines.length ? (
        <section>
          <h3>Prodotti</h3>
          <ul className="lines">
            {c.lines.map((line, i) => <li key={i}><b>{line.name}</b><span>{line.quantity} pz</span>{line.sizes ? <small>{line.sizes}</small> : null}{line.variant ? <small>{line.variant}</small> : null}</li>)}
          </ul>
        </section>
      ) : null}
      {c.notes ? <section><h3>Note</h3><p className="notes">{c.notes}</p></section> : null}
      <a className="ghost wide" href="/"><Icon name="external" size={16} />Modifica nel portale classico</a>
    </div>
  )
}

function Splash({ error, onRetry }: { error?: string; onRetry?: () => void }) {
  return (
    <div className="splash">
      <div className="brand big"><span className="brand-mark" />SPECTRO</div>
      {error ? <><p className="form-error">{error}</p><button className="primary-action" onClick={onRetry}>Riprova</button></> : <div className="loader" />}
    </div>
  )
}

function Login({ onDone }: { onDone: () => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <div className="splash">
      <motion.form className="login" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
        onSubmit={async event => {
          event.preventDefault(); setBusy(true); setError('')
          try { await signIn(email, password); onDone() } catch (err) { setError(err instanceof Error ? err.message : 'Accesso non riuscito.') } finally { setBusy(false) }
        }}>
        <div className="brand big"><span className="brand-mark" />SPECTRO</div>
        <p className="lead">Nuova interfaccia · anteprima</p>
        <label className="field">Email<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required /></label>
        <label className="field">Password<input type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required /></label>
        {error ? <p className="form-error">{error}</p> : null}
        <button className="primary-action wide" disabled={busy}>{busy ? 'Accesso…' : 'Accedi'}</button>
        <a className="ghost wide" href="?demo#oggi">Guarda la demo senza accedere</a>
      </motion.form>
    </div>
  )
}

// Mantiene la sessione allineata se l'accesso avviene nel portale classico.
supabase.auth.onAuthStateChange(() => undefined)
