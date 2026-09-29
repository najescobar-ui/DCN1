import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/api'
import { codigoPedido, type MetodoPago, type Pedido } from '../api/models'
import { useSession } from '../auth/useSession'
import { useCart } from '../cart/useCart'
import { AddressPicker } from '../components/AddressPicker'
import { BackIcon, CardIcon, CashIcon, LockIcon, TransferIcon, TrashIcon } from '../components/icons'
import { DatosTransferencia, DatosWebpay } from '../components/PaymentInfo'
import { clp } from '../utils/format'
import { imageOf } from '../utils/images'
import { notify } from '../utils/notify'
import { formatearTelefono, telefonoValido } from '../utils/telefono'
import { irAWebpay } from '../utils/webpay'

const METODOS: { id: MetodoPago; titulo: string; detalle: string; Icon: typeof CardIcon }[] = [
  { id: 'TARJETA', titulo: 'Tarjeta', detalle: 'Débito o crédito con Webpay', Icon: CardIcon },
  { id: 'TRANSFERENCIA', titulo: 'Transferencia', detalle: 'Envías el comprobante por WhatsApp', Icon: TransferIcon },
  { id: 'EFECTIVO', titulo: 'Efectivo', detalle: 'Pagas al recibir tu pedido', Icon: CashIcon },
]

export function CarritoPage() {
  const cart = useCart()
  const session = useSession()
  const navigate = useNavigate()
  const [metodo, setMetodo] = useState<MetodoPago>('TARJETA')
  const [enviando, setEnviando] = useState(false)
  const [intento, setIntento] = useState(false)
  // Solo se permite un pedido en curso: si hay uno, el carrito lo avisa y no deja confirmar.
  const [enCurso, setEnCurso] = useState<Pedido | null>(null)

  useEffect(() => {
    api.pedidos().then(
      (lista) => setEnCurso(lista.find((p) => ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO'].includes(p.estado)) ?? null),
      () => undefined,
    )
  }, [])

  // Prefill the phone registered in Cognito (ID token claim) the first time.
  const telefonoCuenta = session.idClaims?.phone_number as string | undefined
  useEffect(() => {
    if (!cart.telefono && telefonoCuenta) cart.setTelefono(formatearTelefono(telefonoCuenta))
  }, [telefonoCuenta, cart])

  const errores = {
    direccion: cart.direccion.trim().length < 5 ? 'Ingresa la dirección de entrega.' : null,
    telefono: !telefonoValido(cart.telefono) ? 'Ingresa un celular chileno, ej: +56 9 1234 5678.' : null,
  }
  const valido = !errores.direccion && !errores.telefono

  const confirmar = async () => {
    setIntento(true)
    if (!valido) return
    setEnviando(true)
    try {
      const pedido = await api.crearPedido({
        items: cart.items.map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
        direccionEntrega: [cart.direccion.trim(), cart.detalle.trim()].filter(Boolean).join(', '),
        telefono: cart.telefono,
        latitud: cart.lat,
        longitud: cart.lon,
        metodoPago: metodo,
      })
      cart.clear()
      if (metodo === 'TARJETA') {
        notify.ok(`Pedido ${codigoPedido(pedido.id)} creado. Te llevamos a Webpay...`)
        const { url, token } = await api.iniciarWebpay(pedido.id)
        irAWebpay(url, token)
        return
      }
      notify.ok(`Pedido ${codigoPedido(pedido.id)} recibido`)
      navigate(`/pedidos?nuevo=${pedido.id}`)
    } catch {
      setEnviando(false)
    }
  }

  return (
    <div className="container page">
      <div className="list-head" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Link to="/catalogo" className="back-link">
          <BackIcon />
          Seguir comprando
        </Link>
        <ol className="steps-bar" aria-label="Progreso">
          <li className="done">
            <span className="step-dot">1</span>
            <span className="label">Carrito</span>
          </li>
          <li className={`step-line ${cart.items.length ? 'done' : ''}`} aria-hidden="true" />
          <li className={cart.items.length ? 'done' : ''}>
            <span className="step-dot">2</span>
            <span className="label">Entrega y pago</span>
          </li>
          <li className="step-line" aria-hidden="true" />
          <li>
            <span className="step-dot">3</span>
            <span className="label">Confirmación</span>
          </li>
        </ol>
      </div>
      <h1 className="d page-title">Tu pedido</h1>

      {cart.items.length === 0 ? (
        <div className="card empty">
          <p className="muted">Tu carrito está vacío.</p>
          <Link className="btn" to="/catalogo">
            Ver catálogo
          </Link>
        </div>
      ) : (
        <div className="cart-layout">
          <div className="cart-main">
            <section className="card">
              <div className="cart-head">
                <strong>Tus productos</strong>
                <span className="muted">{cart.count} unidades</span>
              </div>
              {cart.items.map((i) => (
                <div className="cart-line" key={i.productoId}>
                  <img src={imageOf(i.imagenUrl)} alt="" />
                  <div className="cart-line-info">
                    <span style={{ fontWeight: 600 }}>{i.nombre}</span>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {i.categoria} · {clp(i.precio)} c/u
                    </span>
                  </div>
                  <div className="stepper">
                    <button type="button" aria-label={`Quitar uno de ${i.nombre}`} onClick={() => cart.setCantidad(i.productoId, i.cantidad - 1)}>
                      {i.cantidad === 1 ? <TrashIcon /> : '−'}
                    </button>
                    <span>{i.cantidad}</span>
                    <button type="button" aria-label={`Agregar uno de ${i.nombre}`} onClick={() => cart.setCantidad(i.productoId, i.cantidad + 1)}>
                      +
                    </button>
                  </div>
                  <span className="cart-line-sub">{clp(i.precio * i.cantidad)}</span>
                </div>
              ))}
            </section>

            <section className="card card-pad checkout-section">
              <h2 className="d">1 · Entrega</h2>
              <AddressPicker
                direccion={cart.direccion}
                lat={cart.lat}
                lon={cart.lon}
                onChange={(v) => cart.setEntrega({ direccion: v.direccion, lat: v.lat, lon: v.lon })}
              />
              <label className="field" style={{ maxWidth: 420 }}>
                Depto, casa o referencia <span className="faint" style={{ fontWeight: 400 }}>(opcional)</span>
                <input
                  className="input"
                  value={cart.detalle}
                  maxLength={60}
                  placeholder="Ej: depto 502, torre B, portón verde"
                  onChange={(e) => cart.setDetalle(e.target.value)}
                />
              </label>
              {intento && errores.direccion && <span className="error-text">{errores.direccion}</span>}
            </section>

            <section className="card card-pad checkout-section">
              <h2 className="d">2 · Contacto</h2>
              <label className="field" style={{ maxWidth: 320 }}>
                Celular
                <input
                  className="input"
                  type="tel"
                  inputMode="tel"
                  autoComplete="tel"
                  placeholder="+56 9 1234 5678"
                  value={cart.telefono}
                  onChange={(e) => cart.setTelefono(e.target.value)}
                  onBlur={(e) => cart.setTelefono(formatearTelefono(e.target.value))}
                />
              </label>
              <span className="faint" style={{ fontSize: 12 }}>
                El repartidor te llamará a este número si necesita ubicarte.
              </span>
              {intento && errores.telefono && <span className="error-text">{errores.telefono}</span>}
            </section>

            <section className="card card-pad checkout-section">
              <h2 className="d">3 · Pago</h2>
              <div className="pay-options" role="radiogroup" aria-label="Método de pago">
                {METODOS.map(({ id, titulo, detalle, Icon }) => (
                  <label key={id} className={`pay-option ${metodo === id ? 'active' : ''}`}>
                    <input type="radio" name="metodo" value={id} checked={metodo === id} onChange={() => setMetodo(id)} />
                    <Icon />
                    <span>
                      <strong>{titulo}</strong>
                      <small>{detalle}</small>
                    </span>
                  </label>
                ))}
              </div>
              {metodo === 'TARJETA' && <DatosWebpay />}
              {metodo === 'TRANSFERENCIA' && (
                <>
                  <DatosTransferencia total={cart.subtotal} />
                  <span className="muted" style={{ fontSize: 13 }}>
                    Al confirmar te mostramos el botón para enviar el comprobante por WhatsApp.
                  </span>
                </>
              )}
              {metodo === 'EFECTIVO' && (
                <div className="pay-details">
                  <p style={{ margin: 0 }}>
                    Pagas <strong>{clp(cart.subtotal)}</strong> en efectivo al recibir tu pedido. Si puedes, ten el monto justo.
                  </p>
                </div>
              )}
            </section>
          </div>

          <aside className="card summary">
            <h2 className="d" style={{ fontSize: 26, marginBottom: 6 }}>
              Resumen
            </h2>
            <div className="summary-row">
              <span>Subtotal</span>
              <span>{clp(cart.subtotal)}</span>
            </div>
            <div className="summary-row">
              <span>Despacho</span>
              <span style={{ color: 'var(--primary-hover)', fontWeight: 600 }}>Gratis</span>
            </div>
            <div className="summary-row">
              <span>Pago</span>
              <span>{METODOS.find((m) => m.id === metodo)?.titulo}</span>
            </div>
            <div className="summary-total">
              <span>Total</span>
              <span>{clp(cart.subtotal)}</span>
            </div>
            {enCurso && (
              <div className="address-note">
                Ya tienes un pedido en curso ({codigoPedido(enCurso.id)}). Podrás hacer otro cuando se entregue.{' '}
                <Link to="/pedidos">Ver mi pedido</Link>
              </div>
            )}
            <button type="button" className="btn btn-lg" onClick={confirmar} disabled={enviando || !!enCurso}>
              {enviando ? 'Procesando...' : metodo === 'TARJETA' ? 'Confirmar y pagar' : 'Confirmar pedido'}
            </button>
            {intento && !valido && <span className="error-text" style={{ fontSize: 13 }}>Revisa la dirección y el celular.</span>}
            <span className="faint" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
              <LockIcon />
              Pedido enviado con tu sesión autenticada. El precio final lo calcula el servidor.
            </span>
          </aside>
        </div>
      )}
    </div>
  )
}
