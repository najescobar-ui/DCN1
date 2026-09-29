/**
 * Address search for the checkout. Uses Mapbox when a public token is configured (good house
 * numbers in Chile) and Photon / OpenStreetMap otherwise or if Mapbox fails. Both providers share
 * the ranking and the "typed address wins" rules below.
 */
import { config } from '../../config'
import * as mapbox from './mapbox'
import * as photon from './photon'

export const normalizar = (s: string) =>
  s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()

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

export const proveedor = config.mapboxToken ? 'mapbox' : 'photon'

const unicos = (lugares: Lugar[]) =>
  lugares.filter((l, i, all) => all.findIndex((o) => o.etiqueta === l.etiqueta || (o.lat === l.lat && o.lon === l.lon)) === i)

/** Photon: the full text finds exact houses, the text without number finds the street when OSM lacks the house. */
async function buscarEnPhoton(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  const consultas = [texto]
  if (numeroEscrito(texto)) consultas.push(sinNumero(texto))
  return (await Promise.all(consultas.map((q) => photon.buscar(q, signal)))).flat()
}

export async function buscarDirecciones(texto: string, signal?: AbortSignal): Promise<Lugar[]> {
  let resultados: Lugar[] = []
  if (config.mapboxToken) {
    resultados = await mapbox.buscar(texto, config.mapboxToken, signal).catch((error: unknown) => {
      if ((error as Error).name === 'AbortError') throw error
      return [] // quota, network or token problem: fall back to Photon
    })
  }
  if (resultados.length === 0) {
    resultados = await buscarEnPhoton(texto, signal)
  }
  return ordenar(unicos(resultados), texto).slice(0, 6)
}

export async function direccionEn(lat: number, lon: number): Promise<Lugar | null> {
  if (config.mapboxToken) {
    const lugar = await mapbox.reverso(lat, lon, config.mapboxToken).catch(() => null)
    if (lugar) return lugar
  }
  return photon.reverso(lat, lon)
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
