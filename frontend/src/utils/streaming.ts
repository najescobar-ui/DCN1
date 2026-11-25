import type { MetricaVentas } from '../api/models'

/** retention.ms legible: "7 días", "24 h", "30 min"; -1 es "sin límite". */
export function duracion(ms: string | number | null | undefined): string {
  if (ms === null || ms === undefined || ms === '') return '—'
  const n = Number(ms)
  if (n < 0) return 'sin límite'
  const min = n / 60_000
  if (min < 60) return `${Math.round(min)} min`
  const h = min / 60
  if (h < 48) return `${Math.round(h)} h`
  return `${Math.round(h / 24)} días`
}

/** retention.bytes legible. */
export function tamano(bytes: string | number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes === '') return '—'
  const n = Number(bytes)
  if (n < 0) return 'sin límite'
  if (n >= 1024 ** 3) return `${Math.round(n / 1024 ** 3)} GiB`
  if (n >= 1024 ** 2) return `${Math.round(n / 1024 ** 2)} MiB`
  return `${Math.round(n / 1024)} KiB`
}

/** Hora local "15:04" del inicio de una ventana. */
export function horaVentana(m: MetricaVentas): string {
  return new Date(m.inicio).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Las últimas `n` ventanas de un minuto hasta `ahora`, rellenando con cero los minutos sin eventos, para que el
 * gráfico muestre el tiempo real y no solo los minutos con actividad.
 */
export function serieDeVentanas(ventanas: MetricaVentas[], n: number, ahora: number): { inicio: number; pedidos: number; monto: number }[] {
  const porInicio = new Map(ventanas.map((v) => [new Date(v.inicio).getTime(), v]))
  const fin = ahora - (ahora % 60_000)
  const serie = []
  for (let i = n - 1; i >= 0; i--) {
    const inicio = fin - i * 60_000
    const v = porInicio.get(inicio)
    serie.push({ inicio, pedidos: v?.pedidosCreados ?? 0, monto: Number(v?.montoPagado ?? 0) })
  }
  return serie
}

/** Altura relativa (0 a 100) de una barra; siempre visible si el valor es mayor que cero. */
export function alturaBarra(valor: number, maximo: number): number {
  if (maximo <= 0 || valor <= 0) return 0
  return Math.max(4, Math.round((valor / maximo) * 100))
}

/** Grupo sano, atrasado o detenido según sus miembros y su lag. */
export function salud(miembros: number, lag: number): 'ok' | 'atrasado' | 'detenido' {
  if (miembros === 0) return 'detenido'
  return lag > 0 ? 'atrasado' : 'ok'
}
