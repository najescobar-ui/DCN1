import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/api'
import { codigoPedido } from '../api/models'
import { useCart } from '../cart/useCart'
import { BackIcon, LockIcon, TrashIcon } from '../components/icons'
import { clp } from '../utils/format'
import { imageOf } from '../utils/images'
import { notify } from '../utils/notify'

export function CarritoPage() {
  const cart = useCart()
  const navigate = useNavigate()
  const [enviando, setEnviando] = useState(false)
  const faltaDireccion = cart.direccion.trim().length < 5

  const confirmar = async () => {
    setEnviando(true)
    try {
      const pedido = await api.crearPedido({
        items: cart.items.map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
        direccionEntrega: cart.direccion.trim(),
      })
      cart.clear()
      notify.ok(`Pedido ${codigoPedido(pedido.id)} recibido`)
      navigate('/pedidos')
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
            <span className="label">Entrega</span>
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

            <section className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <h2 style={{ fontSize: 16 }}>Entrega a domicilio</h2>
              <label className="field">
                Dirección de entrega
                <input
                  className="input"
                  value={cart.direccion}
                  maxLength={250}
                  placeholder="Calle, número, comuna"
                  onChange={(e) => cart.setDireccion(e.target.value)}
                  autoComplete="street-address"
                />
              </label>
              {faltaDireccion && <span className="muted" style={{ fontSize: 13 }}>Ingresa una dirección para confirmar.</span>}
            </section>

            <section className="card upsell">
              <img src="/img/pudu/pudu-pizza.jpg" alt="El pudú de Pedidos360 mirando una pizza" />
              <div className="upsell-text">
                <span className="m accent" style={{ fontSize: 12, letterSpacing: '.1em' }}>
                  ¿SE TE ANTOJA ALGO MÁS?
                </span>
                <span className="d" style={{ fontSize: 30 }}>
                  Agrega una pizza familiar
                </span>
                <span className="muted" style={{ fontSize: 14 }}>
                  Llega junto con el resto de tu pedido.
                </span>
              </div>
              <Link to="/catalogo?cat=Pizzas" className="btn btn-outline">
                Ver pizzas
              </Link>
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
            <div className="summary-total">
              <span>Total</span>
              <span>{clp(cart.subtotal)}</span>
            </div>
            <button type="button" className="btn btn-lg" onClick={confirmar} disabled={enviando || faltaDireccion}>
              {enviando ? 'Enviando...' : 'Confirmar pedido'}
            </button>
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
