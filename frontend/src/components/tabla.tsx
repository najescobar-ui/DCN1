import type { ReactNode } from 'react'
import type { Orden } from './useTabla'

interface ThProps<K extends string> {
  columna: K
  orden: Orden<K>
  onOrdenar: (columna: K) => void
  children: ReactNode
}

/** Encabezado que ordena la tabla al hacer clic, con el indicador de dirección. */
export function ThOrdenable<K extends string>({ columna, orden, onOrdenar, children }: ThProps<K>) {
  const activa = orden.columna === columna
  return (
    <th aria-sort={activa ? (orden.direccion === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className={`th-sort ${activa ? 'active' : ''}`} onClick={() => onOrdenar(columna)}>
        {children}
        <span aria-hidden="true">{activa ? (orden.direccion === 'asc' ? '▲' : '▼') : '↕'}</span>
      </button>
    </th>
  )
}

interface PaginacionProps {
  pagina: number
  totalPaginas: number
  desde: number
  hasta: number
  total: number
  irA: (n: number) => void
  nombre?: string
}

export function Paginacion({ pagina, totalPaginas, desde, hasta, total, irA, nombre = 'registros' }: PaginacionProps) {
  if (total === 0) return null
  return (
    <nav className="paginacion" aria-label="Paginación">
      <span className="muted">
        {desde}–{hasta} de {total} {nombre}
      </span>
      <div className="paginacion-botones">
        <button type="button" className="btn btn-sm btn-ghost" onClick={() => irA(pagina - 1)} disabled={pagina === 1}>
          Anterior
        </button>
        {Array.from({ length: totalPaginas }, (_, i) => i + 1).map((n) => (
          <button
            key={n}
            type="button"
            className={`btn btn-sm ${n === pagina ? '' : 'btn-ghost'}`}
            aria-current={n === pagina ? 'page' : undefined}
            onClick={() => irA(n)}
          >
            {n}
          </button>
        ))}
        <button type="button" className="btn btn-sm btn-ghost" onClick={() => irA(pagina + 1)} disabled={pagina === totalPaginas}>
          Siguiente
        </button>
      </div>
    </nav>
  )
}
