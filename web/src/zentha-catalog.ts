export function productSports(product: Record<string, unknown>): string[] {
  const sku = String(product.sku ?? '').toUpperCase().replace(/\s+/g, '')
  const terms = `${product.name ?? ''} ${product.category ?? ''} ${product.description ?? ''}`.toLowerCase()
  const sports = []
  if (/^(02BGM|02BASKE)/.test(sku) || /basket|pallacanestro/.test(terms)) sports.push('Basket')
  if (/^(01VGMD|01PCDO|03VBM|03VPC)/.test(sku) || /calcio|volley|pallavolo/.test(terms)) sports.push('Calcio / Volley')
  if (/danza|dance/.test(terms)) sports.push('Danza')
  return sports.length ? sports : ['Multisport']
}

export function productAudience(product: Record<string, unknown>): string {
  const terms = `${product.sku ?? ''} ${product.name ?? ''} ${product.category ?? ''} ${product.description ?? ''}`.toLowerCase()
  if (/\b(baby|bambin[oaie]?|junior|kids)\b/.test(terms)) return 'Baby'
  if (/\b(adult[oaie]?|uomo|donna)\b/.test(terms)) return 'Adulto'
  return 'Da classificare'
}
