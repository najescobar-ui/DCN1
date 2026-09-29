/** Photon (https://photon.komoot.io): free geocoder on OpenStreetMap data, no key needed. */
import type { Lugar } from './index'

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: Record<string, string | undefined>
}

const BASE = 'https://photon.komoot.io'
const CHILE_BBOX = '-75.7,-56,-66.4,-17.5'

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

export function toLugar(f: PhotonFeature): Lugar {
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


export async function buscar(texto: string, signal?: AbortSignal): Promise<Lugar[]> {

  // lang=default returns local (Spanish) names instead of English ones.
  const params = new URLSearchParams({ q: texto, limit: '6', lat: '-33.45', lon: '-70.66', bbox: CHILE_BBOX, lang: 'default' })
  const res = await fetch(`${BASE}/api/?${params}`, { signal })
  if (!res.ok) throw new Error(`Photon ${res.status}`)
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features.filter((f) => f.properties.country === 'Chile' || !f.properties.country).map(toLugar)
}


export async function reverso(lat: number, lon: number): Promise<Lugar | null> {
  const res = await fetch(`${BASE}/reverse?lat=${lat}&lon=${lon}&limit=1&lang=default`)
  if (!res.ok) return null
  const data = (await res.json()) as { features: PhotonFeature[] }
  return data.features[0] ? { ...toLugar(data.features[0]), lat, lon } : null
}
