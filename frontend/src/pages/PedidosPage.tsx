import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/api'
import { ESTADOS, type EstadoPedido, type Pedido } from '../api/models'
import { useSession } from '../auth/useSession'
import { clp, fecha } from '../utils/format'
import { notify } from '../utils/notify'

export function PedidosPage() {
  const session = useSession()
  const [pedidos, setPedidos] = useState<Pedido[]>([])

  const cargar = useCallback(() => {
    api.pedidos().then(setPedidos, () => undefined)
  }, [])

  useEffect(cargar, [cargar])

  const cambiarEstado = async (pedido: Pedido, estado: EstadoPedido) => {
    try {
      await api.cambiarEstado(pedido.id, estado)
      notify.ok(`Pedido #${pedido.id} ahora esta ${estado}`)
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const cancelar = async (pedido: Pedido) => {
    try {
      await api.cancelarPedido(pedido.id)
      notify.ok(`Pedido #${pedido.id} cancelado`)
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  return (
    <>
      <div className="actions between">
        <h1>{session.isAdmin ? 'Todos los pedidos' : 'Mis pedidos'}</h1>
        <Link className="btn" to="/pedidos/nuevo">
          Nuevo pedido
        </Link>
      </div>

      {pedidos.map((p) => (
        <div className="card" key={p.id}>
          <div className="actions between">
            <div className="actions">
              <strong>Pedido #{p.id}</strong>
              <span className={`badge ${p.estado}`}>{p.estado}</span>
              <span className="muted">{fecha(p.creadoEn)}</span>
              {session.isAdmin && <span className="muted">· {p.clienteUsername || p.clienteId}</span>}
            </div>
            <div className="actions">
              {session.isAdmin ? (
                <select
                  aria-label="Estado"
                  value={p.estado}
                  onChange={(e) => cambiarEstado(p, e.target.value as EstadoPedido)}
                >
                  {ESTADOS.map((e) => (
                    <option key={e} value={e}>
                      {e}
                    </option>
                  ))}
                </select>
              ) : (
                p.estado === 'PENDIENTE' && (
                  <button className="btn small danger" type="button" onClick={() => cancelar(p)}>
                    Cancelar
                  </button>
                )
              )}
            </div>
          </div>
          <table>
            <tbody>
              {p.items.map((i) => (
                <tr key={i.productoId}>
                  <td>{i.nombreProducto}</td>
                  <td>
                    {i.cantidad} x {clp(i.precioUnitario)}
                  </td>
                  <td>{clp(i.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions between" style={{ marginTop: 8 }}>
            <span className="muted">{p.direccionEntrega}</span>
            <strong>Total {clp(p.total)}</strong>
          </div>
        </div>
      ))}
      {pedidos.length === 0 && <p className="muted">No hay pedidos todavia.</p>}
    </>
  )
}
