/** Mapbox Geocoding API v6 (free tier: 100k requests/month). Returns suggestions with coordinates. */
import type { Lugar } from './index'

const BASE = 'https://api.mapbox.com/search/geocode/v6'
const SANTIAGO = '-70.66,-33.45'

interface MapboxFeature {
  geometry: { coordinates: [number, number] }
  properties: {
    feature_type: string
    name: string
    context?: {
      address?: { address_number?: string; street_name?: string }
      street?: { name?: string }
      place?: { name?: string }
      locality?: { name?: string }
    }
  }
}

export function toLugar(f: MapboxFeature): Lugar {
  const [lon, lat] = f.geometry.coordinates
  const p = f.properties
  const c = p.context ?? {}
  const comuna = c.place?.name ?? c.locality?.name
  const tipo = p.feature_type === 'address' ? 'house' : p.feature_type === 'street' ? 'street' : 'place'
  return {
    etiqueta: [p.name, comuna].filter(Boolean).join(', '),
    lat,
    lon,
    numero: c.address?.address_number,
    comuna,
    calle: c.address?.street_name ?? c.street?.name ?? (tipo === 'street' ? p.name : undefined),
    tipo,
  }
}

async function pedir(url: string, signal?: AbortSignal): Promise<MapboxFeature[]> {
  const res = await fetch(url, { signal })
  if (!res.ok) throw new Error(`Mapbox ${res.status}`)
  return ((await res.json()) as { features: MapboxFeature[] }).features
}

export async function buscar(texto: string, token: string, signal?: AbortSignal): Promise<Lugar[]> {
  const params = new URLSearchParams({
    q: texto,
    country: 'cl',
    language: 'es',
    autocomplete: 'true',
    types: 'address,street',
    proximity: SANTIAGO,
    limit: '6',
    access_token: token,
  })
  return (await pedir(`${BASE}/forward?${params}`, signal)).map(toLugar)
}

export async function reverso(lat: number, lon: number, token: string): Promise<Lugar | null> {
  const params = new URLSearchParams({ longitude: String(lon), latitude: String(lat), types: 'address,street', language: 'es', limit: '1', access_token: token })
  const [f] = await pedir(`${BASE}/reverse?${params}`)
  return f ? { ...toLugar(f), lat, lon } : null
}
