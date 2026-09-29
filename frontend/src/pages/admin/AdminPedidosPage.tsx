import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../../api/api'
import { ESTADOS, ESTADO_LABEL, SIGUIENTES, codigoPedido, type EstadoPago, type EstadoPedido, type Pedido, type Resumen } from '../../api/models'
import { SearchIcon } from '../../components/icons'
import { Paginacion, ThOrdenable } from '../../components/tabla'
import { useTabla } from '../../components/useTabla'
import { clp, fecha } from '../../utils/format'
import { PagoBadge } from '../PedidosPage'
import { formatearTelefono } from '../../utils/telefono'
import { notify } from '../../utils/notify'

type Tab = 'TODOS' | EstadoPedido

export function AdminPedidosPage() {
  const [pedidos, setPedidos] = useState<Pedido[] | null>(null)
  const [resumen, setResumen] = useState<Resumen | null>(null)
  const [tab, setTab] = useState<Tab>('TODOS')
  const [query, setQuery] = useState('')

  const cargar = useCallback(() => {
    api.pedidos().then(setPedidos, () => setPedidos([]))
    // Aggregated numbers come from the BFF (it calls both microservices).
    api.resumen().then(setResumen, () => undefined)
  }, [])

  useEffect(cargar, [cargar])

  const filas = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (pedidos ?? []).filter(
      (p) =>
        (tab === 'TODOS' || p.estado === tab) &&
        (!q || codigoPedido(p.id).toLowerCase().includes(q) || (p.clienteUsername ?? '').toLowerCase().includes(q)),
    )
  }, [pedidos, tab, query])

  const tabla = useTabla(
    filas,
    {
      pedido: (p) => p.id,
      cliente: (p) => p.clienteUsername ?? p.clienteId,
      fecha: (p) => p.creadoEn,
      total: (p) => p.total,
      pago: (p) => `${p.metodoPago ?? ''} ${p.estadoPago ?? ''}`,
      estado: (p) => ESTADOS.indexOf(p.estado),
    },
    { columna: 'fecha', direccion: 'desc' },
  )

  const cambiarEstado = async (pedido: Pedido, estado: EstadoPedido) => {
    try {
      await api.cambiarEstado(pedido.id, estado)
      notify.ok(`${codigoPedido(pedido.id)} → ${ESTADO_LABEL[estado]}`)
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const cambiarPago = async (pedido: Pedido, estadoPago: EstadoPago) => {
    try {
      await api.cambiarEstadoPago(pedido.id, estadoPago)
      notify.ok(`${codigoPedido(pedido.id)}: pago marcado como ${estadoPago === 'PAGADO' ? 'pagado' : estadoPago.toLowerCase()}`)
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const enCurso = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO'].reduce((n, e) => n + (resumen?.pedidosPorEstado[e] ?? 0), 0)

  return (
    <>
      <div className="admin-head">
        <h1 className="d">Gestión de pedidos</h1>
        <label className="search">
          <SearchIcon size={16} className="muted" />
          <span className="sr-only">Buscar pedido</span>
          <input placeholder="Buscar por ID o cliente" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
      </div>

      <section className="kpis">
        <div className="card kpi">
          <span className="muted">Pedidos totales</span>
          <strong>{resumen?.totalPedidos ?? '—'}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">En curso</span>
          <strong className="accent">{resumen ? enCurso : '—'}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Ventas (sin cancelados)</span>
          <strong>{resumen ? clp(resumen.montoTotal) : '—'}</strong>
        </div>
        <div className="card kpi">
          <span className="muted">Productos activos</span>
          <strong>{resumen?.productosDisponibles ?? '—'}</strong>
        </div>
      </section>

      <div className="tabs" role="tablist">
        {(['TODOS', ...ESTADOS] as Tab[]).map((t) => (
          <button
            key={t}
            role="tab"
            type="button"
            aria-selected={t === tab}
            className={`tab ${t === tab ? 'active' : ''}`}
            onClick={() => setTab(t)}
          >
            {t === 'TODOS' ? 'Todos' : ESTADO_LABEL[t]}
          </button>
        ))}
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <ThOrdenable columna="pedido" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Pedido
              </ThOrdenable>
              <ThOrdenable columna="cliente" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Cliente
              </ThOrdenable>
              <th>Detalle</th>
              <ThOrdenable columna="fecha" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Fecha
              </ThOrdenable>
              <ThOrdenable columna="total" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Total
              </ThOrdenable>
              <ThOrdenable columna="pago" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Pago
              </ThOrdenable>
              <ThOrdenable columna="estado" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Estado
              </ThOrdenable>
            </tr>
          </thead>
          <tbody>
            {tabla.filas.map((p) => (
              <tr key={p.id}>
                <td className="m" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
                  {codigoPedido(p.id)}
                </td>
                <td>
                  {p.clienteUsername ?? p.clienteId}
                  {p.telefonoContacto && (
                    <div>
                      <a href={`tel:${p.telefonoContacto}`} className="m" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                        {formatearTelefono(p.telefonoContacto)}
                      </a>
                    </div>
                  )}
                </td>
                <td className="muted" style={{ fontSize: 14, minWidth: 220 }}>
                  {p.items.map((i) => `${i.cantidad}× ${i.nombreProducto}`).join(', ')}
                  {p.direccionEntrega && <div className="faint">{p.direccionEntrega}</div>}
                </td>
                <td className="muted" style={{ whiteSpace: 'nowrap', fontSize: 14 }}>
                  {fecha(p.creadoEn).split(', ')[0]}
                  <div className="faint">{fecha(p.creadoEn).split(', ')[1]}</div>
                </td>
                <td style={{ fontWeight: 700 }}>{clp(p.total)}</td>
                <td>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-start' }}>
                    <PagoBadge pedido={p} />
                    {p.metodoPago && p.metodoPago !== 'TARJETA' && p.estadoPago !== 'PAGADO' && p.estado !== 'CANCELADO' && (
                      <button type="button" className="btn btn-sm btn-ghost" onClick={() => cambiarPago(p, 'PAGADO')}>
                        Marcar pagado
                      </button>
                    )}
                  </div>
                </td>
                <td>
                  <label>
                    <span className="sr-only">Estado del pedido {codigoPedido(p.id)}</span>
                    <select
                      className="select"
                      style={{ minWidth: 150 }}
                      value={p.estado}
                      disabled={SIGUIENTES[p.estado].length === 0}
                      onChange={(e) => cambiarEstado(p, e.target.value as EstadoPedido)}
                    >
                      {/* Solo el estado actual y los siguientes válidos: el pedido no retrocede ni se salta pasos. */}
                      {[p.estado, ...SIGUIENTES[p.estado]].map((e) => (
                        <option key={e} value={e}>
                          {ESTADO_LABEL[e]}
                        </option>
                      ))}
                    </select>
                  </label>
                </td>
              </tr>
            ))}
            {pedidos !== null && filas.length === 0 && (
              <tr>
                <td colSpan={7} className="muted">
                  No hay pedidos en esta vista.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Paginacion {...tabla} nombre="pedidos" />
    </>
  )
}
