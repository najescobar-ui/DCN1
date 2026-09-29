import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useTabla } from './useTabla'

const filas = Array.from({ length: 23 }, (_, i) => ({ id: i + 1, nombre: `P${String(i + 1).padStart(2, '0')}` }))

describe('useTabla', () => {
  it('pagina de 10 en 10', () => {
    const { result } = renderHook(() => useTabla(filas, { id: (f) => f.id, nombre: (f) => f.nombre }, { columna: 'id', direccion: 'asc' }))
    expect(result.current.filas).toHaveLength(10)
    expect(result.current.totalPaginas).toBe(3)
    act(() => result.current.irA(3))
    expect(result.current.filas.map((f) => f.id)).toEqual([21, 22, 23])
    expect([result.current.desde, result.current.hasta]).toEqual([21, 23])
  })

  it('un clic ordena ascendente y el segundo descendente, volviendo a la página 1', () => {
    const { result } = renderHook(() => useTabla(filas, { id: (f) => f.id, nombre: (f) => f.nombre }, { columna: 'id', direccion: 'asc' }))
    act(() => result.current.irA(2))
    act(() => result.current.ordenarPor('nombre'))
    expect(result.current.pagina).toBe(1)
    expect(result.current.filas[0].nombre).toBe('P01')
    act(() => result.current.ordenarPor('nombre'))
    expect(result.current.filas[0].nombre).toBe('P23')
  })
})
