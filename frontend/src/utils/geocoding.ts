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
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: Record<string, string | undefined>
}

const BASE = 'https://photon.komoot.io'
const CHILE_BBOX = '-75.7,-56,-66.4,-17.5'

/** "Street number, comuna"; a place name is shown only when the result is not a plain address. */
function etiqueta(p: PhotonFeature['properties']): string {
  const calle = p.street ? `${p.street}${p.housenumber ? ' ' + p.housenumber : ''}` : undefined
  const nombre = p.name && p.name !== p.street && !p.housenumber ? p.name : undefined
  const comuna = p.city ?? p.district ?? p.county
  return [nombre, calle, comuna].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(', ')
}

function toLugar(f: PhotonFeature): Lugar {
  const [lon, lat] = f.geometry.coordinates
  const p = f.properties
  return { etiqueta: etiqueta(p), lat, lon, numero: p.housenumber, comuna: p.city ?? p.district ?? p.county }
}

/** Street number typed by the user ("Av. Providencia 1234, Providencia" -> "1234"). */
export function numeroEscrito(texto: string): string | undefined {
  return texto.split(',')[0].match(/\d{1,6}[a-zA-Z]?\b/)?.[0]
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
  const despuesDelNumero = calle.slice(calle.indexOf(numero) + numero.length).toLowerCase()
  const comuna = lugar.comuna && !despuesDelNumero.includes(lugar.comuna.toLowerCase()) ? lugar.comuna : undefined
  return { direccion: comuna ? `${calle}, ${comuna}` : calle, exacta: false }
}

/** Results with the typed house number first. */
export function ordenarPorNumero(lugares: Lugar[], escrito: string): Lugar[] {
  const numero = numeroEscrito(escrito)?.toLowerCase()
  if (!numero) return lugares
  return [...lugares].sort((a, b) => Number(b.numero?.toLowerCase() === numero) - Number(a.numero?.toLowerCase() === numero))
}

export async function buscarDirecciones(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  // lang=default returns local (Spanish) names instead of English ones.
  const params = new URLSearchParams({ q: texto, limit: '5', lat: '-33.45', lon: '-70.66', bbox: CHILE_BBOX, lang: 'default' })
  const res = await fetch(`${BASE}/api/?${params}`, { signal })
  if (!res.ok) throw new Error(`Photon ${res.status}`)
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features.filter((f) => f.properties.country === 'Chile' || !f.properties.country).map(toLugar)
}

export async function direccionEn(lat: number, lon: number): Promise<Lugar | null> {
  const res = await fetch(`${BASE}/reverse?lat=${lat}&lon=${lon}&limit=1&lang=default`)
  if (!res.ok) return null
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features[0] ? { ...toLugar(data.features[0]), lat, lon } : null
}
