import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/api'
import type { Producto } from '../api/models'
import { useCart } from '../cart/useCart'
import { PlusIcon } from '../components/icons'
import { clp } from '../utils/format'
import { descuento, imageOf } from '../utils/images'
import { notify } from '../utils/notify'

const TODO = 'Todo'
const ORDEN_CATEGORIAS = ['Hamburguesas', 'Pizzas', 'Sushi', 'Bebidas', 'Postres']
const rank = (c?: string) => {
  const i = ORDEN_CATEGORIAS.indexOf(c ?? '')
  return i === -1 ? ORDEN_CATEGORIAS.length : i
}
const normalize = (s: string) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

export function CatalogoPage() {
  const cart = useCart()
  const [params, setParams] = useSearchParams()
  const [productos, setProductos] = useState<Producto[] | null>(null)
  const categoria = params.get('cat') ?? TODO
  const query = params.get('q') ?? ''

  useEffect(() => {
    // Menu order: by category, then as loaded in the catalog.
    api.productos().then((lista) => setProductos([...lista].sort((a, b) => rank(a.categoria) - rank(b.categoria) || a.id - b.id)), () => setProductos([]))
  }, [])

  const categorias = useMemo(
    () => [TODO, ...Array.from(new Set((productos ?? []).map((p) => p.categoria).filter(Boolean) as string[]))],
    [productos],
  )

  const visibles = useMemo(() => {
    const q = normalize(query)
    return (productos ?? []).filter(
      (p) =>
        (categoria === TODO || p.categoria === categoria) &&
        (!q || normalize(`${p.nombre} ${p.categoria ?? ''} ${p.descripcion ?? ''}`).includes(q)),
    )
  }, [productos, categoria, query])

  const elegir = (cat: string) => {
    const next = new URLSearchParams(params)
    if (cat === TODO) next.delete('cat')
    else next.set('cat', cat)
    setParams(next)
  }

  const agregar = (p: Producto) => {
    cart.add({ productoId: p.id, nombre: p.nombre, precio: p.precio, imagenUrl: p.imagenUrl, categoria: p.categoria })
    notify.ok(`${p.nombre} agregado al carrito`)
  }

  const titulo = query ? `Resultados para “${query}”` : categoria === TODO ? 'Lo más pedido' : categoria

  return (
    <div className="container page">
      <section className="promo">
        <div className="promo-text">
          <span className="promo-kicker m">PROMO DE LA SEMANA</span>
          <h1 className="d">
            Despacho gratis
            <br />
            en todos tus pedidos
          </h1>
        </div>
        <button className="btn btn-lg" type="button" onClick={() => elegir('Hamburguesas')}>
          Ver hamburguesas
        </button>
        <img src="/img/pudu/pudu-burger.jpg" alt="El pudú de Pedidos360 esperando una hamburguesa" />
        <div className="saw" style={{ bottom: 0, transform: 'rotate(180deg)' }} />
      </section>

      <nav className="categories" aria-label="Categorías">
        {categorias.map((c) => (
          <button key={c} type="button" className={`cat ${c === categoria ? 'active' : ''}`} onClick={() => elegir(c)}>
            {c}
          </button>
        ))}
      </nav>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="list-head">
          <h2 className="d section-title">{titulo}</h2>
          <span className="muted" style={{ fontSize: 14 }}>
            {visibles.length} productos
          </span>
        </div>

        {productos === null ? (
          <p className="muted">Cargando catálogo...</p>
        ) : visibles.length === 0 ? (
          <div className="card empty">
            <p className="muted">No encontramos productos con ese filtro.</p>
            <Link className="btn btn-ghost" to="/catalogo">
              Ver todo el catálogo
            </Link>
          </div>
        ) : (
          <div className="product-grid">
            {visibles.map((p) => {
              const off = descuento(p.precio, p.precioAnterior)
              return (
                <article key={p.id} className="card product-card">
                  <Link to={`/producto/${p.id}`} className="product-media">
                    <img src={imageOf(p.imagenUrl)} alt={p.nombre} loading="lazy" />
                    {off && <span className="off-tag">{off}</span>}
                  </Link>
                  <div className="product-body">
                    <Link to={`/producto/${p.id}`} className="product-name">
                      {p.nombre}
                    </Link>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {p.categoria}
                    </span>
                    <div className="product-foot">
                      <div style={{ display: 'flex', flexDirection: 'column' }}>
                        {off && <span className="old-price">{clp(p.precioAnterior)}</span>}
                        <span className="price">{clp(p.precio)}</span>
                      </div>
                      <button
                        type="button"
                        className="btn icon-btn"
                        aria-label={`Agregar ${p.nombre} al carrito`}
                        onClick={() => agregar(p)}
                        disabled={p.stock === 0}
                      >
                        <PlusIcon />
                      </button>
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </div>

      {cart.count > 0 && (
        <Link to="/carrito" className="cart-fab">
          <span>Ver carrito</span>
          <span style={{ fontSize: 14, fontWeight: 600 }}>{cart.count} productos</span>
          <span className="total">{clp(cart.subtotal)}</span>
        </Link>
      )}
    </div>
  )
}
