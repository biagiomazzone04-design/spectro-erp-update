import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { SIZES, SPORT_ICON, TARGET_ICON, sizeBreakdown, type ZenthaModel } from './catalog'
import { byPriority, formatPrice, isOpen, todayIso, type Commessa } from './model'
import type { NewZenthaLine } from './source'
import { Icon, OrderCard } from './ui'
import { Empty } from './Today'

type Props = {
  catalog: ZenthaModel[]
  commesse: Commessa[]
  canFinance: boolean
  canCreate: boolean
  startNew: boolean
  onOpen: (c: Commessa) => void
  onCreate: (order: Record<string, unknown>, items: NewZenthaLine[]) => Promise<string | null>
}

type StepKey = 'cliente' | 'collezione' | 'sport' | 'target' | 'kit' | 'taglie' | 'riepilogo'
const STEP_LABEL: Record<StepKey, string> = {
  cliente: 'Cliente', collezione: 'Collezione', sport: 'Sport', target: 'Per chi', kit: 'Kit e modello', taglie: 'Taglie', riepilogo: 'Riepilogo'
}

const unique = <T,>(values: T[]) => [...new Set(values)]

function parseSizes(text: string) {
  const sizes: Record<string, number> = {}
  text.split(',').forEach(part => {
    const [size, qty] = part.split(/[×x]/).map(value => value.trim())
    if (size && Number(qty) > 0) sizes[size] = Number(qty)
  })
  return sizes
}

export function Zentha({ catalog, commesse, canFinance, canCreate, startNew, onOpen, onCreate }: Props) {
  const [wizard, setWizard] = useState(startNew)
  useEffect(() => { if (startNew) setWizard(true) }, [startNew])
  const orders = useMemo(() => commesse.filter(c => c.kind === 'zentha').sort((a, b) => Number(isOpen(b)) - Number(isOpen(a)) || byPriority(a, b)), [commesse])
  const sports = unique(catalog.map(model => model.sport))

  return (
    <div className="screen zentha">
      <header className="screen-head">
        <div>
          <p className="eyebrow">Abbigliamento sportivo</p>
          <h1>Zentha</h1>
          <p className="lead">{catalog.length} modelli in {sports.length} sport · {orders.filter(isOpen).length} ordini aperti</p>
        </div>
        {!wizard ? <button className="primary-action" onClick={() => setWizard(true)} disabled={!canCreate}><Icon name="plus" />Nuovo ordine Zentha</button> : null}
      </header>

      <AnimatePresence mode="wait">
        {wizard ? (
          <motion.div key="wizard" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 12 }}>
            <Wizard catalog={catalog} commesse={orders} canFinance={canFinance} onCancel={() => setWizard(false)}
              onCreate={async (order, items) => { const error = await onCreate(order, items); if (!error) setWizard(false); return error }} />
          </motion.div>
        ) : (
          <motion.div key="list" className="zentha-home" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <section className="panel">
              <div className="panel-head"><div><h2>Ordini Zentha</h2><p>Aperti prima, poi i consegnati</p></div></div>
              <div className="card-grid">
                {orders.map(c => <OrderCard key={c.key} c={c} onOpen={onOpen} />)}
                {!orders.length ? <Empty text="Ancora nessun ordine Zentha." /> : null}
              </div>
            </section>
            <section className="panel">
              <div className="panel-head"><div><h2>Catalogo</h2><p>Modelli per sport, ricavati da codici e nomi</p></div></div>
              <div className="sport-overview">
                {sports.map(sport => (
                  <div key={sport} className="sport-card">
                    <span className="sport-icon">{SPORT_ICON[sport] || '✦'}</span>
                    <b>{sport}</b>
                    <small>{unique(catalog.filter(m => m.sport === sport).map(m => m.garment)).join(' · ')}</small>
                    <em>{catalog.filter(m => m.sport === sport).length} modelli</em>
                  </div>
                ))}
              </div>
            </section>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function Wizard({ catalog, commesse, canFinance, onCancel, onCreate }: {
  catalog: ZenthaModel[]; commesse: Commessa[]; canFinance: boolean; onCancel: () => void
  onCreate: (order: Record<string, unknown>, items: NewZenthaLine[]) => Promise<string | null>
}) {
  const collections = unique(catalog.map(model => model.collection))
  const steps: StepKey[] = ['cliente', ...(collections.length > 1 ? ['collezione' as StepKey] : []), 'sport', 'target', 'kit', 'taglie', 'riepilogo']
  const [step, setStep] = useState(0)
  const [direction, setDirection] = useState(1)
  const [client, setClient] = useState('')
  const [due, setDue] = useState('')
  const [collection, setCollection] = useState(collections[0] || '')
  const [sport, setSport] = useState('')
  const [target, setTarget] = useState<ZenthaModel['target'] | ''>('')
  const [kit, setKit] = useState<Record<string, string | null>>({})
  const [sizes, setSizes] = useState<Record<string, number>>({})
  const [personalization, setPersonalization] = useState('')
  const [notes, setNotes] = useState('')
  const [prices, setPrices] = useState<Record<string, number>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const inCollection = catalog.filter(model => model.collection === collection)
  const sportOptions = unique(inCollection.map(model => model.sport))
  const forSport = inCollection.filter(model => model.sport === sport)
  const targetOptions = unique(forSport.map(model => model.target))
  const forTarget = forSport.filter(model => model.target === target)
  const garments = unique(forTarget.map(model => model.garment))
  const sizeList = target ? SIZES[target] : SIZES.Unisex
  const pieces = Object.values(sizes).reduce((sum, qty) => sum + qty, 0)
  const chosen = Object.entries(kit).filter(([, id]) => id).map(([garment, id]) => ({ garment, model: catalog.find(model => model.id === id)! })).filter(entry => entry.model)
  const clients = unique(commesse.map(c => c.client)).slice(0, 8)
  const lastOrder = commesse.find(c => c.client.toLowerCase() === client.trim().toLowerCase())
  const current = steps[step]

  // Nuovo destinatario o sport: propone il kit completo con la prima variante di ogni capo.
  useEffect(() => {
    if (!target) return
    const next: Record<string, string | null> = {}
    garments.forEach(garment => { next[garment] = kit[garment] && forTarget.some(m => m.id === kit[garment]) ? kit[garment] : forTarget.find(m => m.garment === garment)?.id || null })
    setKit(next)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sport, target, collection])

  const go = (delta: number) => { setDirection(delta); setStep(s => Math.max(0, Math.min(steps.length - 1, s + delta))) }
  const pick = (setter: () => void) => { setter(); setTimeout(() => go(1), 160) }

  const canNext = current === 'cliente' ? client.trim().length >= 2
    : current === 'sport' ? !!sport : current === 'target' ? !!target
    : current === 'kit' ? chosen.length > 0 : current === 'taglie' ? pieces > 0 : true

  const repeatLast = () => {
    if (!lastOrder) return
    const items = (Array.isArray(lastOrder.raw.payload.items) ? lastOrder.raw.payload.items : []) as Record<string, unknown>[]
    const models = items.map(item => catalog.find(model => model.legacyId === Number(item.productId))).filter(Boolean) as ZenthaModel[]
    if (!models.length) return
    setCollection(models[0].collection); setSport(models[0].sport); setTarget(models[0].target)
    const nextKit: Record<string, string | null> = {}
    models.forEach(model => { nextKit[model.garment] = model.id })
    setTimeout(() => setKit(nextKit), 0)
    setSizes(parseSizes(String(items[0]?.sizeBreakdown || '')))
    setDirection(1); setStep(steps.indexOf('taglie'))
  }

  const submit = async () => {
    setSaving(true); setError('')
    const items: NewZenthaLine[] = chosen.map(({ model }) => ({
      productId: model.legacyId, productName: model.name, quantity: pieces,
      unitPrice: canFinance ? (prices[model.id] ?? model.price ?? 0) : 0,
      unitCost: canFinance ? (model.cost ?? 0) : 0,
      skuSnapshot: model.sku, productDescriptionSnapshot: model.description,
      variant: model.variant === 'Standard' ? '' : model.variant, color: '',
      sizeBreakdown: sizeBreakdown(sizes), personalization, productionNotes: ''
    }))
    const message = await onCreate({ clientName: client.trim(), orderDate: todayIso(), dueDate: due, status: 'CONFIRMED', notes }, items)
    setSaving(false)
    if (message) setError(message)
  }

  const total = chosen.reduce((sum, { model }) => sum + pieces * (prices[model.id] ?? model.price ?? 0), 0)

  return (
    <section className="wizard panel">
      <div className="wizard-top">
        <ol className="wizard-steps">
          {steps.map((key, i) => (
            <li key={key} className={i < step ? 'done' : i === step ? 'current' : ''}>
              <button disabled={i > step} onClick={() => { setDirection(i < step ? -1 : 1); setStep(i) }}>
                <span>{i < step ? <Icon name="check" size={12} /> : i + 1}</span>{STEP_LABEL[key]}
              </button>
            </li>
          ))}
        </ol>
        <button className="icon-button" onClick={onCancel} aria-label="Annulla"><Icon name="close" /></button>
      </div>

      <div className="wizard-body">
        <AnimatePresence mode="wait" custom={direction}>
          <motion.div key={current} custom={direction}
            initial={{ opacity: 0, x: direction * 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: direction * -40 }}
            transition={{ type: 'spring', stiffness: 300, damping: 32 }}>

            {current === 'cliente' ? (
              <div className="step">
                <h2>Per quale cliente?</h2>
                <input className="big-input" autoFocus value={client} onChange={event => setClient(event.target.value)} placeholder="Nome della squadra o della società"
                  onKeyDown={event => { if (event.key === 'Enter' && canNext) go(1) }} />
                {clients.length ? <div className="suggestions">{clients.map(name => <button key={name} className="chip" onClick={() => setClient(name)}>{name}</button>)}</div> : null}
                <label className="field">Consegna richiesta<input type="date" value={due} min={todayIso()} onChange={event => setDue(event.target.value)} /></label>
                {lastOrder ? (
                  <motion.button className="repeat-card" onClick={repeatLast} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
                    <Icon name="repeat" /><span><b>Ripeti l’ultimo ordine</b><small>{lastOrder.code} · {lastOrder.title} · {lastOrder.pieces} pz</small></span><Icon name="arrow" size={16} />
                  </motion.button>
                ) : null}
              </div>
            ) : null}

            {current === 'collezione' ? (
              <div className="step">
                <h2>Quale collezione?</h2>
                <div className="tile-grid">
                  {collections.map(name => (
                    <button key={name} className={`tile ${collection === name ? 'on' : ''}`} onClick={() => pick(() => setCollection(name))}>
                      <b>{name}</b><small>{catalog.filter(m => m.collection === name).length} modelli</small>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            {current === 'sport' ? (
              <div className="step">
                <h2>Che sport?</h2>
                <div className="tile-grid">
                  {sportOptions.map(name => (
                    <button key={name} className={`tile ${sport === name ? 'on' : ''}`} onClick={() => pick(() => { setSport(name); setTarget('') })}>
                      <span className="tile-icon">{SPORT_ICON[name] || '✦'}</span><b>{name}</b>
                      <small>{unique(inCollection.filter(m => m.sport === name).map(m => m.target)).join(' · ')}</small>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            {current === 'target' ? (
              <div className="step">
                <h2>Per chi?</h2>
                <div className="tile-grid">
                  {targetOptions.map(name => (
                    <button key={name} className={`tile ${target === name ? 'on' : ''}`} onClick={() => pick(() => { setTarget(name); setSizes({}) })}>
                      <span className="tile-icon">{TARGET_ICON[name]}</span><b>{name}</b>
                      <small>{unique(forSport.filter(m => m.target === name).map(m => m.garment)).join(' + ')}</small>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            {current === 'kit' ? (
              <div className="step">
                <h2>Componi il kit</h2>
                <p className="step-hint">Ogni capo è già incluso con il modello più usato. Tocca un’altra variante per cambiarla o il capo per toglierlo.</p>
                <div className="kit-grid">
                  {garments.map(garment => {
                    const variants = forTarget.filter(m => m.garment === garment)
                    const selected = kit[garment]
                    return (
                      <div key={garment} className={`kit-card ${selected ? 'on' : 'off'}`}>
                        <button className="kit-toggle" onClick={() => setKit(k => ({ ...k, [garment]: k[garment] ? null : variants[0].id }))}>
                          <span className="check">{selected ? <Icon name="check" size={14} /> : null}</span><b>{garment}</b>
                        </button>
                        <div className="variants">
                          {variants.map(model => (
                            <button key={model.id} className={`variant ${selected === model.id ? 'on' : ''}`} onClick={() => setKit(k => ({ ...k, [garment]: model.id }))}>
                              <b>{model.variant}</b><small>{model.sku}</small>
                              {canFinance && model.price !== null ? <em>{formatPrice(model.price)}</em> : null}
                            </button>
                          ))}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            ) : null}

            {current === 'taglie' ? (
              <div className="step">
                <h2>Quante per taglia?</h2>
                <p className="step-hint">Le taglie valgono per tutto il kit: {chosen.map(c => c.garment.toLowerCase()).join(' e ')}.</p>
                <div className="size-grid">
                  {sizeList.map(size => (
                    <div key={size} className={`size ${sizes[size] ? 'on' : ''}`}>
                      <span>{size}</span>
                      <div className="stepper-input">
                        <button aria-label={`Meno ${size}`} onClick={() => setSizes(s => ({ ...s, [size]: Math.max(0, (s[size] || 0) - 1) }))}><Icon name="minus" size={16} /></button>
                        <input inputMode="numeric" value={sizes[size] || ''} placeholder="0" onChange={event => setSizes(s => ({ ...s, [size]: Math.max(0, Number(event.target.value.replace(/\D/g, '')) || 0) }))} />
                        <button aria-label={`Più ${size}`} onClick={() => setSizes(s => ({ ...s, [size]: (s[size] || 0) + 1 }))}><Icon name="plus" size={16} /></button>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="totals"><b>{pieces}</b> kit · <b>{pieces * chosen.length}</b> capi</div>
                <label className="field">Personalizzazioni<textarea value={personalization} onChange={event => setPersonalization(event.target.value)} placeholder="Nomi, numeri, sponsor, loghi…" /></label>
              </div>
            ) : null}

            {current === 'riepilogo' ? (
              <div className="step">
                <h2>Controlla e crea</h2>
                <div className="summary">
                  <div className="summary-head"><b>{client}</b><span>{due ? `consegna ${new Date(`${due}T12:00:00`).toLocaleDateString('it-IT')}` : 'consegna da definire'} · {sport} · {target}</span></div>
                  {chosen.map(({ model }) => (
                    <div key={model.id} className="summary-line">
                      <span><b>{model.garment}{model.variant !== 'Standard' ? ` · ${model.variant}` : ''}</b><small>{model.sku} · {sizeBreakdown(sizes)}</small></span>
                      <span className="qty">{pieces} pz</span>
                      {canFinance ? <label className="price">€<input type="number" min="0" step="0.01" value={prices[model.id] ?? model.price ?? 0} onChange={event => setPrices(p => ({ ...p, [model.id]: Math.max(0, Number(event.target.value) || 0) }))} /></label> : null}
                    </div>
                  ))}
                  {canFinance ? <div className="summary-total"><span>Totale ordine</span><b>{formatPrice(total)}</b></div> : null}
                </div>
                <label className="field">Note per la produzione<textarea value={notes} onChange={event => setNotes(event.target.value)} placeholder="Riferimenti, colori, indicazioni" /></label>
                <p className="step-hint"><Icon name="alert" size={14} /> Metri di carta e di tessuto si inseriscono dopo il nesting, sull’ordine.</p>
                {error ? <p className="form-error">{error}</p> : null}
              </div>
            ) : null}
          </motion.div>
        </AnimatePresence>
      </div>

      <footer className="wizard-foot">
        <button className="ghost" onClick={() => (step === 0 ? onCancel() : go(-1))}><Icon name="back" size={16} />{step === 0 ? 'Annulla' : 'Indietro'}</button>
        {current === 'riepilogo'
          ? <button className="primary-action" disabled={saving} onClick={submit}>{saving ? 'Creazione…' : <><Icon name="check" />Crea ordine</>}</button>
          : <button className="primary-action" disabled={!canNext} onClick={() => go(1)}>Avanti<Icon name="arrow" size={16} /></button>}
      </footer>
    </section>
  )
}
