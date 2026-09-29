/**
 * Address search with Photon (https://photon.komoot.io), a free geocoder built on OpenStreetMap
 * data that is meant for search-as-you-type. Results are biased to Chile.
 */
export interface Lugar {
  etiqueta: string
  lat: number
  lon: number
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: Record<string, string | undefined>
}

const BASE = 'https://photon.komoot.io'
const CHILE_BBOX = '-75.7,-56,-66.4,-17.5'

function etiqueta(p: PhotonFeature['properties']): string {
  const calle = p.street ? `${p.street}${p.housenumber ? ' ' + p.housenumber : ''}` : p.name
  const lugar = p.name && p.street && p.name !== p.street ? p.name : undefined
  const comuna = p.city ?? p.district ?? p.county
  return [lugar, calle, comuna, p.state].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(', ')
}

function toLugar(f: PhotonFeature): Lugar {
  const [lon, lat] = f.geometry.coordinates
  return { etiqueta: etiqueta(f.properties), lat, lon }
}

export async function buscarDirecciones(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  const params = new URLSearchParams({ q: texto, limit: '5', lat: '-33.45', lon: '-70.66', bbox: CHILE_BBOX })
  const res = await fetch(`${BASE}/api/?${params}`, { signal })
  if (!res.ok) throw new Error(`Photon ${res.status}`)
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features.filter((f) => f.properties.country === 'Chile' || !f.properties.country).map(toLugar)
}

export async function direccionEn(lat: number, lon: number): Promise<Lugar | null> {
  const res = await fetch(`${BASE}/reverse?lat=${lat}&lon=${lon}&limit=1`)
  if (!res.ok) return null
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features[0] ? { ...toLugar(data.features[0]), lat, lon } : null
}
