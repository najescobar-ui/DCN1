import { useMemo, useState } from 'react'

type Valor = string | number | null | undefined
export type Orden<K extends string> = { columna: K; direccion: 'asc' | 'desc' }

/**
 * Ordena y pagina una lista en el navegador. `valores` indica, por columna, qué valor comparar
 * (por ejemplo, el estado por su posición en el flujo y no alfabéticamente).
 */
export function useTabla<T, K extends string>(
  filas: T[],
  valores: Record<K, (fila: T) => Valor>,
  inicial: Orden<NoInfer<K>>,
  porPagina = 10,
) {
  const [orden, setOrden] = useState<Orden<K>>(inicial)
  const [pagina, setPagina] = useState(1)

  const ordenadas = useMemo(() => {
    const valor = valores[orden.columna]
    const signo = orden.direccion === 'asc' ? 1 : -1
    return [...filas].sort((a, b) => {
      const va = valor(a)
      const vb = valor(b)
      if (va == null) return 1
      if (vb == null) return -1
      const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es')
      return cmp * signo
    })
    // valores es un objeto de funciones estable por render; basta con filas y orden.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filas, orden])

  const totalPaginas = Math.max(1, Math.ceil(ordenadas.length / porPagina))
  const actual = Math.min(pagina, totalPaginas)

  return {
    orden,
    pagina: actual,
    totalPaginas,
    total: ordenadas.length,
    desde: ordenadas.length === 0 ? 0 : (actual - 1) * porPagina + 1,
    hasta: Math.min(actual * porPagina, ordenadas.length),
    filas: ordenadas.slice((actual - 1) * porPagina, actual * porPagina),
    irA: (n: number) => setPagina(Math.min(Math.max(1, n), totalPaginas)),
    // Clic en la misma columna invierte el orden; en otra columna parte ascendente. Vuelve a la página 1.
    ordenarPor: (columna: K) => {
      setOrden((o) => ({ columna, direccion: o.columna === columna && o.direccion === 'asc' ? 'desc' : 'asc' }))
      setPagina(1)
    },
  }
}
