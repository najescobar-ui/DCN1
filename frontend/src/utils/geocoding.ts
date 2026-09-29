/**
 * Address search with Photon (https://photon.komoot.io), a free geocoder built on OpenStreetMap
 * data that is meant for search-as-you-type. Results are biased to Chile.
 */
export interface Lugar {
  etiqueta: string
  lat: number
  lon: number
  /** House number known by the map for this result, if any. */
  numero?: string
  comuna?: string
  calle?: string
  /** house = exact address, street = a street without number, place = a business or landmark. */
  tipo?: 'house' | 'street' | 'place'
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: Record<string, string | undefined>
}

const BASE = 'https://photon.komoot.io'
const CHILE_BBOX = '-75.7,-56,-66.4,-17.5'

export const normalizar = (s: string) =>
  s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()

/** In Greater Santiago OSM puts "Santiago" as city and the comuna in district. */
function comunaDe(p: PhotonFeature['properties']): string | undefined {
  return p.city === 'Santiago' && p.district ? p.district : (p.city ?? p.district ?? p.county)
}

function tipoDe(p: PhotonFeature['properties']): Lugar['tipo'] {
  if (p.housenumber && (p.osm_value === 'house_number' || p.osm_key === 'building' || p.type === 'house')) return 'house'
  if (p.osm_key === 'highway' || p.type === 'street') return 'street'
  return 'place'
}

/** "Street number, comuna"; a place name is shown only when the result is not a plain address. */
function etiqueta(p: PhotonFeature['properties'], tipo: Lugar['tipo']): string {
  const calle = p.street ? `${p.street}${p.housenumber ? ' ' + p.housenumber : ''}` : undefined
  const nombre = tipo === 'place' || !calle ? p.name : undefined
  return [nombre, calle, comunaDe(p)].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(', ')
}

function toLugar(f: PhotonFeature): Lugar {
  const [lon, lat] = f.geometry.coordinates
  const p = f.properties
  const tipo = tipoDe(p)
  return {
    etiqueta: etiqueta(p, tipo),
    lat,
    lon,
    numero: p.housenumber,
    comuna: comunaDe(p),
    calle: p.street ?? (tipo === 'street' ? p.name : undefined),
    tipo,
  }
}

/** Street number typed by the user ("Av. Providencia 1234, Providencia" -> "1234"). */
export function numeroEscrito(texto: string): string | undefined {
  return texto.split(',')[0].match(/\d{1,6}[a-zA-Z]?\b/)?.[0]
}

/** Typed text without the street number: "San Jorge 60, Ñuñoa" -> "San Jorge, Ñuñoa". */
export function sinNumero(texto: string): string {
  const numero = numeroEscrito(texto)
  return numero ? texto.replace(numero, '').replace(/\s+,/g, ',').replace(/\s{2,}/g, ' ').trim() : texto
}

/**
 * Ranks results against what the user typed: exact house number, then the typed comuna,
 * then the typed street name; streets and houses before businesses. Results in a comuna
 * other than the typed one go down.
 */
export function ordenar(lugares: Lugar[], escrito: string): Lugar[] {
  const numero = numeroEscrito(escrito)?.toLowerCase()
  const texto = normalizar(escrito)
  // The typed comuna is whatever comes after the number, or after the first comma.
  const despues = numero ? texto.slice(texto.indexOf(numero) + numero.length) : texto.split(',').slice(1).join(',')
  const comunas = new Set(lugares.map((l) => l.comuna).filter(Boolean).map((c) => normalizar(c!)))
  const comunaEscrita = [...comunas].find((c) => despues.includes(c))
  const calleEscrita = normalizar(sinNumero(texto.split(',')[0]).replace(comunaEscrita ?? '\u0000', ''))
  const puntaje = (l: Lugar) => {
    const mismaCalle = !!l.calle && !!calleEscrita && normalizar(l.calle).includes(calleEscrita)
    let p = 0
    // A matching number only counts on the typed street ("Jorge Monckeberg 60" is not "San Jorge 60").
    if (numero && mismaCalle && l.numero?.toLowerCase() === numero) p += 4
    if (comunaEscrita && l.comuna) p += normalizar(l.comuna) === comunaEscrita ? 3 : -3
    if (mismaCalle) p += 2
    if (l.tipo !== 'place') p += 1
    return p
  }
  return lugares
    .map((l, i) => ({ l, i, p: puntaje(l) }))
    .sort((a, b) => b.p - a.p || a.i - b.i)
    .map((x) => x.l)
}

async function photon(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  // lang=default returns local (Spanish) names instead of English ones.
  const params = new URLSearchParams({ q: texto, limit: '6', lat: '-33.45', lon: '-70.66', bbox: CHILE_BBOX, lang: 'default' })
  const res = await fetch(`${BASE}/api/?${params}`, { signal })
  if (!res.ok) throw new Error(`Photon ${res.status}`)
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features.filter((f) => f.properties.country === 'Chile' || !f.properties.country).map(toLugar)
}

/**
 * Searches the full text (finds exact houses when OSM has them) and the text without the number
 * (finds the street when the house is missing), then merges and ranks both.
 */
export async function buscarDirecciones(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  const consultas = [texto]
  if (numeroEscrito(texto)) consultas.push(sinNumero(texto))
  const resultados = (await Promise.all(consultas.map((q) => photon(q, signal)))).flat()
  const unicos = resultados.filter(
    (l, i, all) => all.findIndex((o) => o.etiqueta === l.etiqueta || (o.lat === l.lat && o.lon === l.lon)) === i,
  )
  return ordenar(unicos, texto).slice(0, 6)
}

export async function direccionEn(lat: number, lon: number): Promise<Lugar | null> {
  const res = await fetch(`${BASE}/reverse?lat=${lat}&lon=${lon}&limit=1&lang=default`)
  if (!res.ok) return null
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features[0] ? { ...toLugar(data.features[0]), lat, lon } : null
}

/**
 * What the user typed wins: OpenStreetMap has few house numbers in Chile, so when the chosen
 * result does not have the typed number we keep the typed street and number, add the comuna
 * from the result, and only use its coordinates to place the pin.
 */
export function combinarDireccion(escrito: string, lugar: Lugar): { direccion: string; exacta: boolean } {
  const numero = numeroEscrito(escrito)
  if (!numero || lugar.numero?.toLowerCase() === numero.toLowerCase()) {
    return { direccion: lugar.etiqueta, exacta: true }
  }
  const calle = escrito.split(',')[0].trim()
  // Only text after the number counts as a typed comuna ("Providencia 1234" is a street in Providencia).
  const despuesDelNumero = normalizar(calle.slice(calle.indexOf(numero) + numero.length))
  const comuna = lugar.comuna && !despuesDelNumero.includes(normalizar(lugar.comuna)) ? lugar.comuna : undefined
  return { direccion: comuna ? `${calle}, ${comuna}` : calle, exacta: false }
}
