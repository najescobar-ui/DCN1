import { describe, expect, it } from 'vitest'
import type { MetricaVentas } from '../api/models'
import { alturaBarra, duracion, salud, serieDeVentanas, tamano } from './streaming'

describe('streaming', () => {
  it('muestra la retención en la unidad que se lee mejor', () => {
    expect(duracion('604800000')).toBe('7 días')
    expect(duracion(86_400_000)).toBe('24 h')
    expect(duracion('1800000')).toBe('30 min')
    expect(duracion('-1')).toBe('sin límite')
    expect(duracion(null)).toBe('—')
  })

  it('muestra el tamaño máximo por partición', () => {
    expect(tamano('104857600')).toBe('100 MiB')
    expect(tamano(1024 ** 3)).toBe('1 GiB')
    expect(tamano('-1')).toBe('sin límite')
  })

  it('rellena con cero los minutos sin eventos', () => {
    const ahora = Date.parse('2026-11-25T15:04:30Z')
    const v = { inicio: '2026-11-25T15:03:00Z', pedidosCreados: 2, montoPagado: 5000 } as MetricaVentas
    const serie = serieDeVentanas([v], 3, ahora)
    expect(serie.map((s) => s.pedidos)).toEqual([0, 2, 0])
    expect(serie[1].monto).toBe(5000)
  })

  it('las barras con valor siempre se ven', () => {
    expect(alturaBarra(1, 1000)).toBe(4)
    expect(alturaBarra(500, 1000)).toBe(50)
    expect(alturaBarra(0, 1000)).toBe(0)
  })

  it('clasifica la salud de un grupo', () => {
    expect(salud(3, 0)).toBe('ok')
    expect(salud(3, 12)).toBe('atrasado')
    expect(salud(0, 12)).toBe('detenido')
  })
})
