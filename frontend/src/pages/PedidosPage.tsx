import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/api'
import { ESTADO_LABEL, codigoPedido, type EstadoPedido, type Pedido, type Producto } from '../api/models'
import { useSession } from '../auth/useSession'
import { useCart } from '../cart/useCart'
import { BackIcon } from '../components/icons'
import { clp, fecha } from '../utils/format'
import { notify } from '../utils/notify'

const FLUJO: EstadoPedido[] = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO', 'ENTREGADO']
const EN_CURSO: EstadoPedido[] = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO']
const hora = (iso: string) => new Date(iso).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false })

export function PedidosPage() {
  const session = useSession()
  const cart = useCart()
  const navigate = useNavigate()
  const [pedidos, setPedidos] = useState<Pedido[] | null>(null)
  const [productos, setProductos] = useState<Producto[]>([])

  const cargar = useCallback(() => {
    api.pedidos().then(setPedidos, () => setPedidos([]))
  }, [])

  useEffect(() => {
    cargar()
    api.productos().then(setProductos, () => undefined)
  }, [cargar])

  const activo = useMemo(() => pedidos?.find((p) => EN_CURSO.includes(p.estado)), [pedidos])
  const historial = useMemo(() => (pedidos ?? []).filter((p) => p !== activo), [pedidos, activo])

  const cancelar = async (pedido: Pedido) => {
    try {
      await api.cancelarPedido(pedido.id)
      notify.ok(`Pedido ${codigoPedido(pedido.id)} cancelado`)
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const repetir = (pedido: Pedido) => {
    let agregados = 0
    for (const item of pedido.items) {
      const producto = productos.find((p) => p.id === item.productoId)
      if (producto) {
        cart.add(
          {
            productoId: producto.id,
            nombre: producto.nombre,
            precio: producto.precio,
            imagenUrl: producto.imagenUrl,
            categoria: producto.categoria,
          },
          item.cantidad,
        )
        agregados++
      }
    }
    if (agregados) {
      navigate('/carrito')
    } else {
      notify.error('Los productos de ese pedido ya no están disponibles')
    }
  }

  return (
    <div className="container page">
      <div className="list-head" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Link to="/catalogo" className="back-link">
          <BackIcon />
          Volver al catálogo
        </Link>
        <span className="m muted" style={{ fontSize: 12 }}>
          SESIÓN: {session.username.toUpperCase()} · GRUPO: {session.roles.join(', ')}
        </span>
      </div>
      <h1 className="d page-title">{session.isAdmin ? 'Pedidos' : 'Mis pedidos'}</h1>

      {pedidos === null ? (
        <p className="muted">Cargando pedidos...</p>
      ) : activo ? (
        <section className="card tracker">
          <div className="tracker-main">
            <div className="tracker-top">
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <span className="m accent" style={{ fontSize: 13 }}>
                  {codigoPedido(activo.id)}
                </span>
                <h2 className="d">{ESTADO_LABEL[activo.estado]}</h2>
                <span className="muted" style={{ fontSize: 15 }}>
                  {activo.items.reduce((n, i) => n + i.cantidad, 0)} productos · {clp(activo.total)}
                </span>
                {activo.direccionEntrega && (
                  <span className="muted" style={{ fontSize: 14 }}>
                    Entrega en {activo.direccionEntrega}
                  </span>
                )}
              </div>
              <div className="tracker-when">
                <span className="muted" style={{ fontSize: 13 }}>
                  Última actualización
                </span>
                <span className="d accent" style={{ fontSize: 40 }}>
                  {hora(activo.actualizadoEn)}
                </span>
                {activo.estado === 'PENDIENTE' && !session.isAdmin && (
                  <button type="button" className="btn btn-sm btn-ghost" onClick={() => cancelar(activo)}>
                    Cancelar pedido
                  </button>
                )}
              </div>
            </div>
            <ol className="tracker-steps">
              {FLUJO.map((estado, i) => {
                const actual = FLUJO.indexOf(activo.estado)
                const clase = i === actual ? 'done now' : i < actual ? 'done' : ''
                const tiempo = i === 0 ? hora(activo.creadoEn) : i === actual ? hora(activo.actualizadoEn) : '—'
                return (
                  <li key={estado} className={clase}>
                    <div className="bar" />
                    <span>{ESTADO_LABEL[estado]}</span>
                    <span className="m faint" style={{ fontSize: 12 }}>
                      {i <= actual ? tiempo : '—'}
                    </span>
                  </li>
                )
              })}
            </ol>
          </div>
          <div className="tracker-art">
            <img src="/img/pudu/pudu-sushi.jpg" alt="El pudú de Pedidos360 esperando su pedido" />
            <span>{activo.estado === 'DESPACHADO' ? 'Ya casi llega...' : 'Preparando tu pedido...'}</span>
          </div>
        </section>
      ) : (
        <section className="card tracker">
          <div className="tracker-main" style={{ justifyContent: 'center' }}>
            <h2 className="d" style={{ fontSize: 30 }}>
              No tienes pedidos en curso
            </h2>
            <p className="muted" style={{ margin: 0 }}>
              Elige algo rico del catálogo y síguelo aquí en tiempo real.
            </p>
            <Link className="btn" to="/catalogo" style={{ alignSelf: 'flex-start' }}>
              Ir al catálogo
            </Link>
          </div>
          <div className="tracker-art">
            <img src="/img/pudu/pudu-sushi.jpg" alt="El pudú de Pedidos360 esperando su sushi" />
          </div>
        </section>
      )}

      {historial.length > 0 && (
        <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 className="d" style={{ fontSize: 26 }}>
            Historial
          </h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Productos</th>
                  <th>Fecha</th>
                  <th>Total</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {historial.map((p) => (
                  <tr key={p.id}>
                    <td className="m" style={{ fontSize: 13 }}>
                      {codigoPedido(p.id)}
                    </td>
                    <td>{p.items.map((i) => `${i.cantidad}× ${i.nombreProducto}`).join(', ')}</td>
                    <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                      {fecha(p.creadoEn)}
                    </td>
                    <td style={{ fontWeight: 700 }}>{clp(p.total)}</td>
                    <td>
                      <span className={`badge badge-${p.estado}`}>{ESTADO_LABEL[p.estado]}</span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button type="button" className="btn btn-sm btn-ghost" onClick={() => repetir(p)}>
                        Repetir
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}
