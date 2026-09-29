import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/api'
import type { Producto } from '../api/models'
import { useCart } from '../cart/useCart'
import { BackIcon } from '../components/icons'
import { clp } from '../utils/format'
import { descuento, imageOf } from '../utils/images'
import { notify } from '../utils/notify'

export function ProductoPage() {
  const { id } = useParams()
  const cart = useCart()
  const navigate = useNavigate()
  const [producto, setProducto] = useState<Producto | null | undefined>(undefined)
  const [cantidad, setCantidad] = useState(1)

  useEffect(() => {
    api.productos().then(
      (lista) => setProducto(lista.find((p) => String(p.id) === id) ?? null),
      () => setProducto(null),
    )
  }, [id])

  if (producto === undefined) {
    return <p className="container page muted">Cargando...</p>
  }
  if (producto === null) {
    return (
      <div className="container page">
        <div className="card empty">
          <p className="muted">Este producto no existe o ya no está disponible.</p>
          <Link className="btn" to="/catalogo">
            Volver al catálogo
          </Link>
        </div>
      </div>
    )
  }

  const off = descuento(producto.precio, producto.precioAnterior)
  const max = Math.max(1, Math.min(99, producto.stock))

  const agregar = () => {
    cart.add(
      {
        productoId: producto.id,
        nombre: producto.nombre,
        precio: producto.precio,
        imagenUrl: producto.imagenUrl,
        categoria: producto.categoria,
      },
      cantidad,
    )
    notify.ok(`${cantidad} × ${producto.nombre} agregado al carrito`)
    navigate('/carrito')
  }

  return (
    <div className="container page">
      <nav className="breadcrumb" aria-label="Ruta">
        <Link to="/catalogo" className="back-link">
          <BackIcon />
          Catálogo
        </Link>
        <span>/</span>
        {producto.categoria && (
          <>
            <Link to={`/catalogo?cat=${encodeURIComponent(producto.categoria)}`} className="muted">
              {producto.categoria}
            </Link>
            <span>/</span>
          </>
        )}
        <span style={{ color: 'var(--text)' }}>{producto.nombre}</span>
      </nav>

      <div className="product-detail">
        <div className="product-detail-media">
          <img src={imageOf(producto.imagenUrl)} alt={producto.nombre} />
          {off && <span className="off-tag">{off}</span>}
        </div>
        <div className="product-detail-info">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <span className="muted" style={{ fontSize: 14 }}>
              {producto.categoria} · <span style={{ color: 'var(--primary-hover)', fontWeight: 600 }}>Despacho gratis</span>
            </span>
            <h1 className="d">{producto.nombre}</h1>
            <p className="muted" style={{ margin: 0, fontSize: 16 }}>
              {producto.descripcion}
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
            <span style={{ fontSize: 36, fontWeight: 700 }}>{clp(producto.precio)}</span>
            {off && (
              <span className="old-price" style={{ fontSize: 18 }}>
                {clp(producto.precioAnterior)}
              </span>
            )}
          </div>
          <span className="muted" style={{ fontSize: 14 }}>
            {producto.stock > 0 ? `${producto.stock} disponibles` : 'Sin stock por ahora'}
          </span>
          <div className="add-row">
            <div className="stepper">
              <button type="button" aria-label="Quitar uno" onClick={() => setCantidad((c) => Math.max(1, c - 1))}>
                −
              </button>
              <span>{cantidad}</span>
              <button type="button" aria-label="Agregar uno" onClick={() => setCantidad((c) => Math.min(max, c + 1))}>
                +
              </button>
            </div>
            <button type="button" className="btn btn-lg" onClick={agregar} disabled={producto.stock === 0}>
              <span>Agregar al carrito</span>
              <span>{clp(producto.precio * cantidad)}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
