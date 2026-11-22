import type { CloudRecord } from './model'

// Il catalogo Zentha strutturato: ogni prodotto riceve collezione, sport,
// destinatario, capo e variante. Se i campi esistono già nel record li usa,
// altrimenti li ricava da codice, nome e descrizione (es. "BASKE" → Basket).

export type ZenthaModel = {
  id: string
  legacyId: number | null
  sku: string
  name: string
  description: string
  collection: string
  sport: string
  target: 'Uomo' | 'Donna' | 'Bambino' | 'Unisex'
  garment: string
  variant: string
  price: number | null
  cost: number | null
}

const SPORTS: [RegExp, string][] = [
  [/CALCIO\s*\/\s*VOLLEY|VOLLEY\s*\/\s*CALCIO/, 'Calcio / Volley'],
  [/BASKE/, 'Basket'],
  [/CALCIO|FOOTBALL|SOCCER/, 'Calcio'],
  [/VOLLEY|PALLAVOLO/, 'Volley'],
  [/DANZA|DANCE/, 'Danza'],
  [/RUGBY/, 'Rugby'],
  [/PADEL/, 'Padel'],
  [/TENNIS/, 'Tennis'],
  [/RUNNING|CORSA|ATLETICA/, 'Running'],
  [/CICLISMO|BIKE/, 'Ciclismo'],
  [/HANDBALL|PALLAMANO/, 'Pallamano']
]

const GARMENTS: [RegExp, string][] = [
  [/PANTALONCIN|SHORT|\bPC[A-Z]*\b/, 'Pantaloncino'],
  [/CANOTTA|CANOTTIERA/, 'Canotta'],
  [/FELPA/, 'Felpa'],
  [/GIACC|GIUBBINO/, 'Giacca'],
  [/CALZETT|CALZE/, 'Calzettoni'],
  [/BODY/, 'Body'],
  [/LEGGINGS|PANTALON/, 'Pantalone'],
  [/MAGLIA|MAGLIETTA|T-SHIRT|POLO/, 'Maglia']
]

const word = (value: unknown) => String(value ?? '').trim()
const num = (value: unknown) => (value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value))

function capitalize(text: string) {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : text
}

export function toModel(row: CloudRecord): ZenthaModel {
  const x = row.payload
  const sku = word(x.sku)
  const name = word(x.name)
  const description = word(x.description)
  const haystack = `${sku} ${name} ${description}`.toUpperCase()
  const sport = word(x.sport) || SPORTS.find(([pattern]) => pattern.test(haystack))?.[1] || 'Multisport'
  const target = (word(x.target) as ZenthaModel['target']) ||
    (/BABY|BAMBIN|KID|JUNIOR/.test(haystack) ? 'Bambino' : /DONNA|WOMAN|LADY/.test(haystack) ? 'Donna' : /UOMO|MAN\b|MEN\b/.test(haystack) ? 'Uomo' : 'Unisex')
  const garment = word(x.garment) || word(x.category) || GARMENTS.find(([pattern]) => pattern.test(haystack))?.[1] || 'Capo'
  const derived = capitalize(name
    .replace(new RegExp(garment, 'i'), '')
    .replace(/\b(uomo|donna|baby|bambino|unisex)\b/gi, '')
    .replace(/\b(calcio|volley|basket|danza)\b\/?/gi, '')
    .replace(/[/·]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim())
  // Varianti leggibili dai codici: PIEGA → a pieghe, INS → con inserti…
  const keywords = [
    [/PIEG/, 'A pieghe'], [/DOUBLE/, 'Double'], [/\bINS(ERTI)?\b/, 'Con inserti'], [/UNITO/, 'Unito'], [/SUBL/, 'Sublimato'], [/MESH/, 'Mesh']
  ] as const
  const fromCodes = keywords.filter(([pattern]) => pattern.test(haystack)).map(([, label]) => label)
  const variant = word(x.variant) || derived || fromCodes.slice(0, 2).join(' · ') || 'Standard'
  return {
    id: row.id,
    legacyId: num(x.id),
    sku, name, description,
    collection: word(x.collection) || 'Collezione principale',
    sport, target, garment, variant,
    price: num(x.standardPrice),
    cost: num(x.standardCost)
  }
}

export function buildCatalog(records: CloudRecord[]): ZenthaModel[] {
  return records
    .filter(row => row.entity_type === 'zentha_product' && row.payload.active !== false)
    .map(toModel)
    .sort((a, b) => a.sport.localeCompare(b.sport, 'it') || a.garment.localeCompare(b.garment, 'it') || a.variant.localeCompare(b.variant, 'it'))
}

export const SIZES: Record<ZenthaModel['target'], string[]> = {
  Uomo: ['XS', 'S', 'M', 'L', 'XL', 'XXL', '3XL'],
  Donna: ['XS', 'S', 'M', 'L', 'XL', 'XXL'],
  Unisex: ['XS', 'S', 'M', 'L', 'XL', 'XXL'],
  Bambino: ['4 anni', '6 anni', '8 anni', '10 anni', '12 anni', '14 anni']
}

export const SPORT_ICON: Record<string, string> = {
  'Calcio / Volley': '⚽', Calcio: '⚽', Volley: '🏐', Basket: '🏀', Danza: '🩰', Rugby: '🏉',
  Padel: '🎾', Tennis: '🎾', Running: '🏃', Ciclismo: '🚴', Pallamano: '🤾', Multisport: '✦'
}

export const TARGET_ICON: Record<ZenthaModel['target'], string> = { Uomo: '👕', Donna: '👚', Bambino: '🧒', Unisex: '◎' }

export function sizeBreakdown(sizes: Record<string, number>) {
  return Object.entries(sizes).filter(([, qty]) => qty > 0).map(([size, qty]) => `${size}×${qty}`).join(', ')
}
