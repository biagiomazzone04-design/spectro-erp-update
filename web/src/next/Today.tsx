import { motion } from 'framer-motion'
import { useMemo } from 'react'
import { byPriority, daysBetween, formatDate, formatEuro, formatMeters, isOpen, todayIso, type Commessa, type PlotterStats } from './model'
import { Bars, Icon, Kpi, OrderCard } from './ui'

type Props = {
  name: string
  commesse: Commessa[]
  plotters: PlotterStats[]
  revenue: number
  canFinance: boolean
  onOpen: (c: Commessa) => void
  onGo: (view: 'orders' | 'zentha', filter?: string) => void
}

const greeting = () => {
  const hour = new Date().getHours()
  return hour < 13 ? 'Buongiorno' : hour < 18 ? 'Buon pomeriggio' : 'Buonasera'
}

export function Today({ name, commesse, plotters, revenue, canFinance, onOpen, onGo }: Props) {
  const today = todayIso()
  const open = useMemo(() => commesse.filter(isOpen).sort(byPriority), [commesse])
  const late = open.filter(c => c.urgency === 'late')
  const blocked = open.filter(c => c.phase === 'bloccato')
  const upcoming = open.filter(c => c.daysLeft !== null && c.daysLeft >= 0 && c.daysLeft <= 7)
  const priority = open.filter(c => c.phase !== 'bloccato').slice(0, 6)
  const weekMeters = plotters.reduce((sum, plotter) => sum + plotter.week, 0)
  const daily = plotters[0]?.daily.map((day, i) => plotters.reduce((sum, plotter) => sum + (plotter.daily[i]?.meters || 0), 0)) || []
  const nextDays = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(`${today}T12:00:00`); d.setDate(d.getDate() + i)
    const iso = d.toISOString().slice(0, 10)
    return { iso, items: open.filter(c => c.due === iso) }
  })
  const dateLabel = new Date().toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric', month: 'long' })

  return (
    <div className="screen today">
      <header className="screen-head">
        <div>
          <p className="eyebrow">{dateLabel}</p>
          <h1>{greeting()}, {name}</h1>
          <p className="lead">
            {late.length ? <><b className="text-danger">{late.length} {late.length === 1 ? 'ordine in ritardo' : 'ordini in ritardo'}</b> · </> : <><b className="text-ok">Nessun ritardo</b> · </>}
            {upcoming.length} consegne nei prossimi 7 giorni{blocked.length ? <> · <b className="text-warning">{blocked.length} da sbloccare</b></> : null}
          </p>
        </div>
        <button className="primary-action" onClick={() => onGo('zentha', 'new')}><Icon name="plus" />Nuovo ordine Zentha</button>
      </header>

      <section className="kpi-grid">
        <Kpi label="Ordini aperti" value={open.length} format={n => String(Math.round(n))} hint={`${open.filter(c => c.kind === 'zentha').length} Zentha · ${open.filter(c => c.kind === 'spectro').length} Spectro`} tone="pink" onClick={() => onGo('orders')} />
        <Kpi label="In ritardo" value={late.length} format={n => String(Math.round(n))} hint={late.length ? `il più vecchio: ${-Math.min(...late.map(c => c.daysLeft ?? 0))} g` : 'tutto in tempo'} tone={late.length ? 'danger' : 'neutral'} onClick={() => onGo('orders', 'late')} />
        <Kpi label="Carta stampata · settimana" value={weekMeters} format={formatMeters} hint="ultimi 14 giorni" spark={daily} tone="pink" />
        {canFinance
          ? <Kpi label="Fatturato del mese" value={revenue} format={formatEuro} hint="produzioni registrate" tone="warning" />
          : <Kpi label="Da sbloccare" value={blocked.length} format={n => String(Math.round(n))} hint="materiale o cliente" tone={blocked.length ? 'warning' : 'neutral'} onClick={() => onGo('orders', 'blocked')} />}
      </section>

      <div className="today-grid">
        <section className="panel">
          <div className="panel-head">
            <div><h2>Priorità</h2><p>Prima i ritardi, poi le consegne più vicine</p></div>
            <button className="ghost" onClick={() => onGo('orders')}>Tutti gli ordini <Icon name="arrow" size={16} /></button>
          </div>
          <div className="card-grid">
            {priority.map(c => <OrderCard key={c.key} c={c} onOpen={onOpen} />)}
            {!priority.length ? <Empty text="Nessun ordine aperto. Crea il primo ordine Zentha dal pulsante in alto." /> : null}
          </div>
        </section>

        <div className="side-stack">
          <section className="panel">
            <div className="panel-head"><div><h2>Plotter</h2><p>Metri di carta per giorno, ultime 2 settimane</p></div></div>
            <div className="plotters">
              {plotters.map(plotter => (
                <motion.div key={plotter.name} className="plotter" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                  <div className="plotter-head">
                    <strong>{plotter.name}</strong>
                    <span>oggi <b>{formatMeters(plotter.today)}</b> · settimana <b>{formatMeters(plotter.week)}</b></span>
                  </div>
                  <Bars values={plotter.daily.map(day => day.meters)} labels={plotter.daily.map(day => formatDate(day.date))} capacity={plotter.avgPerDay} />
                </motion.div>
              ))}
              {!plotters.length ? <Empty text="Nessuna produzione registrata." /> : null}
            </div>
          </section>

          <section className="panel">
            <div className="panel-head"><div><h2>Da sbloccare</h2><p>Ordini fermi e motivo</p></div></div>
            <div className="mini-list">
              {blocked.map(c => (
                <button key={c.key} className="mini-row" onClick={() => onOpen(c)}>
                  <Icon name="alert" size={18} /><span><b>{c.client}</b><small>{c.blockedReason} · {c.code}</small></span>
                </button>
              ))}
              {!blocked.length ? <Empty text="Niente di fermo." /> : null}
            </div>
          </section>

          <section className="panel">
            <div className="panel-head"><div><h2>Prossimi 7 giorni</h2><p>Consegne previste</p></div></div>
            <div className="week-strip">
              {nextDays.map(day => (
                <div key={day.iso} className={`week-day ${day.items.length ? 'has' : ''}`}>
                  <span>{new Date(`${day.iso}T12:00:00`).toLocaleDateString('it-IT', { weekday: 'short' })}</span>
                  <b>{new Date(`${day.iso}T12:00:00`).getDate()}</b>
                  <div className="week-dots">{day.items.slice(0, 4).map(c => <i key={c.key} className={c.kind} title={`${c.client} · ${c.code}`} onClick={() => onOpen(c)} />)}</div>
                </div>
              ))}
            </div>
            <p className="footnote">{upcoming.length ? `Prossima: ${upcoming[0].client}, ${daysBetween(today, upcoming[0].due) === 0 ? 'oggi' : formatDate(upcoming[0].due)}` : 'Nessuna consegna in settimana.'}</p>
          </section>
        </div>
      </div>
    </div>
  )
}

export function Empty({ text }: { text: string }) {
  return <div className="empty">{text}</div>
}
