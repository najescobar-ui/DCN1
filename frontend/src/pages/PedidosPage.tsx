import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/api'
import { ESTADO_LABEL, ESTADO_PAGO_LABEL, METODO_LABEL, codigoPedido, type EstadoPedido, type Pedido, type Producto } from '../api/models'
import { useSession } from '../auth/useSession'
import { useCart } from '../cart/useCart'
import { BackIcon } from '../components/icons'
import { DatosTransferencia } from '../components/PaymentInfo'
import { formatearTelefono } from '../utils/telefono'
import { TEXTO_SEGUIMIENTO, encuadre, imagenSeguimiento, unidades } from '../utils/seguimiento'
import { irAWebpay } from '../utils/webpay'
import { clp, fecha } from '../utils/format'
import { notify } from '../utils/notify'

const FLUJO: EstadoPedido[] = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO', 'ENTREGADO']
const EN_CURSO: EstadoPedido[] = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO']
/** Cada cuánto se consulta el estado mientras hay un pedido en curso. */
const ACTUALIZAR_CADA_MS = 15000
/** Durante cuánto tiempo se sigue destacando un pedido después de entregado. */
const RECIEN_ENTREGADO_MS = 3 * 60 * 60 * 1000
const RESULTADO_PAGO: Record<string, [ok: boolean, texto: string]> = {
  aprobado: [true, 'Pago aprobado por Webpay. ¡Gracias!'],
  rechazado: [false, 'Webpay rechazó el pago. Puedes intentarlo de nuevo.'],
  anulado: [false, 'Anulaste el pago en Webpay. Puedes intentarlo de nuevo.'],
  error: [false, 'No pudimos verificar el pago.'],
}

export function PagoBadge({ pedido }: { pedido: Pedido }) {
  if (!pedido.metodoPago) return null
  const estado = pedido.estadoPago ?? 'PENDIENTE'
  return (
    <span className={`badge badge-pago-${estado}`} title={METODO_LABEL[pedido.metodoPago]}>
      {METODO_LABEL[pedido.metodoPago]} · {ESTADO_PAGO_LABEL[estado]}
    </span>
  )
}

const hora = (iso: string) => new Date(iso).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false })

export function PedidosPage() {
  const session = useSession()
  const cart = useCart()
  const navigate = useNavigate()
  const [pedidos, setPedidos] = useState<Pedido[] | null>(null)
  const [productos, setProductos] = useState<Producto[]>([])
  const [params, setParams] = useSearchParams()
  const [pagando, setPagando] = useState(false)

  // Result of the Webpay redirect (?pago=aprobado|rechazado|anulado|error).
  useEffect(() => {
    const resultado = RESULTADO_PAGO[params.get('pago') ?? '']
    if (resultado) {
      const [ok, texto] = resultado
      if (ok) notify.ok(texto)
      else notify.error(texto)
      setParams({}, { replace: true })
    }
  }, [params, setParams])

  // Momento de la última carga: sirve para saber si el último pedido se entregó hace poco.
  const [cargadoEn, setCargadoEn] = useState(0)

  const cargar = useCallback(() => {
    api.pedidos().then(
      (data) => {
        setPedidos(data)
        setCargadoEn(Date.now())
      },
      () => setPedidos([]),
    )
  }, [])

  useEffect(() => {
    cargar()
    api.productos().then(setProductos, () => undefined)
  }, [cargar])

  // Se destaca el último pedido mientras está en curso o recién entregado (para mostrar "¡Que lo
  // disfrutes!"); si no, otro pedido que siga en curso. Los pedidos vienen del más nuevo al más antiguo.
  const activo = useMemo(() => {
    const ultimo = pedidos?.[0]
    const recienEntregado =
      ultimo?.estado === 'ENTREGADO' && cargadoEn - new Date(ultimo.actualizadoEn).getTime() < RECIEN_ENTREGADO_MS
    if (ultimo && (EN_CURSO.includes(ultimo.estado) || recienEntregado)) return ultimo
    return pedidos?.find((p) => EN_CURSO.includes(p.estado))
  }, [pedidos, cargadoEn])
  const enCurso = !!pedidos?.some((p) => EN_CURSO.includes(p.estado))

  // Mientras hay un pedido en curso, se consulta su estado periódicamente (solo con la pestaña visible),
  // así el cliente ve los cambios que hace el admin sin recargar.
  useEffect(() => {
    if (!enCurso) return
    const timer = setInterval(() => {
      if (!document.hidden) cargar()
    }, ACTUALIZAR_CADA_MS)
    return () => clearInterval(timer)
  }, [enCurso, cargar])
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

  const pagarConWebpay = async (pedido: Pedido) => {
    setPagando(true)
    try {
      const { url, token } = await api.iniciarWebpay(pedido.id)
      irAWebpay(url, token)
    } catch {
      setPagando(false)
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
                  {unidades(activo.items.reduce((n, i) => n + i.cantidad, 0))} · {clp(activo.total)}
                </span>
                {activo.direccionEntrega && (
                  <span className="muted" style={{ fontSize: 14 }}>
                    Entrega en {activo.direccionEntrega}
                    {activo.telefonoContacto && ` · ${formatearTelefono(activo.telefonoContacto)}`}
                  </span>
                )}
                <div style={{ marginTop: 6 }}>
                  <PagoBadge pedido={activo} />
                </div>
              </div>
              <div className="tracker-when">
                <span className="muted" style={{ fontSize: 13 }}>
                  Última actualización
                </span>
                <span className="d accent" style={{ fontSize: 40 }}>
                  {hora(activo.actualizadoEn)}
                </span>
                {activo.estado === 'PENDIENTE' && activo.estadoPago !== 'PAGADO' && !session.isAdmin && (
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
                // Solo se guardan la creación y el último cambio: los pasos intermedios ya cumplidos llevan ✓.
                const tiempo = i === 0 ? hora(activo.creadoEn) : i === actual ? hora(activo.actualizadoEn) : '✓'
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
            {activo.metodoPago === 'TARJETA' && activo.estadoPago === 'PAGADO' && activo.pagoTarjeta && (
              <span className="muted" style={{ fontSize: 14 }}>
                Pagado con tarjeta terminada en {activo.pagoTarjeta} · código de autorización {activo.pagoAutorizacion}
              </span>
            )}
            {activo.metodoPago === 'TARJETA' && activo.estadoPago !== 'PAGADO' && !session.isAdmin && (
              <div className="pay-details">
                <span>Tu pedido está reservado, pero falta el pago con tarjeta.</span>
                <button type="button" className="btn" style={{ alignSelf: 'flex-start' }} disabled={pagando} onClick={() => pagarConWebpay(activo)}>
                  {pagando ? 'Abriendo Webpay...' : 'Pagar con Webpay'}
                </button>
              </div>
            )}
            {activo.metodoPago === 'TRANSFERENCIA' && activo.estadoPago !== 'PAGADO' && !session.isAdmin && (
              <DatosTransferencia codigo={codigoPedido(activo.id)} total={activo.total} />
            )}
            {activo.metodoPago === 'EFECTIVO' && activo.estadoPago !== 'PAGADO' && (
              <span className="muted" style={{ fontSize: 14 }}>
                Pagas {clp(activo.total)} en efectivo al recibir.
              </span>
            )}
          </div>
          <div className="tracker-art">
            <img
              src={imagenSeguimiento(activo, productos)}
              style={{ objectPosition: encuadre(imagenSeguimiento(activo, productos)) }}
              alt={`El pudú de Pedidos360: ${TEXTO_SEGUIMIENTO[activo.estado]}`}
            />
            <span>{TEXTO_SEGUIMIENTO[activo.estado]}</span>
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
            <img src="/img/pudu/pudu-home.jpg" alt="El pudú de Pedidos360 en la cocina" />
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
                  <th>Pago</th>
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
                    <td>
                      <PagoBadge pedido={p} />
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
