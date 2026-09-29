import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useSession } from '../auth/useSession'
import { useCart } from '../cart/useCart'
import { Brand } from './Brand'
import { CartIcon, PinIcon, SearchIcon } from './icons'
import { Toast } from './Toast'

const initials = (name: string) => name.slice(0, 2).toUpperCase() || '··'

export function ShopLayout() {
  const session = useSession()
  const cart = useCart()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') ?? '')
  const menu = useRef<HTMLDetailsElement>(null)

  // Close the user menu on navigation.
  useEffect(() => {
    menu.current?.removeAttribute('open')
  }, [location.pathname])

  const buscar = (event: FormEvent) => {
    event.preventDefault()
    navigate(query.trim() ? `/catalogo?q=${encodeURIComponent(query.trim())}` : '/catalogo')
  }

  return (
    <>
      <header className="shop-header">
        <div className="container shop-header-inner">
          <Brand />
          <Link to="/carrito" className="address-btn" title="Cambiar dirección de entrega">
            <PinIcon size={18} className="accent" />
            <span>
              <small>Entregar en</small>
              <strong>{cart.direccion || 'Agrega tu dirección'}</strong>
            </span>
          </Link>
          <form className="search" role="search" onSubmit={buscar}>
            <SearchIcon size={18} className="muted" />
            <label htmlFor="q" className="sr-only">
              Buscar
            </label>
            <input
              id="q"
              type="search"
              placeholder="Busca productos o categorías"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </form>
          <NavLink to="/pedidos" className="header-link">
            Mis pedidos
          </NavLink>
          <details className="user-menu" ref={menu}>
            <summary aria-label="Menú de usuario">
              <span className="avatar">{initials(session.username)}</span>
              <span className="role-tag">{session.roles[0] ?? ''}</span>
            </summary>
            <div className="menu">
              <div className="menu-head">
                <strong>{session.username}</strong>
                <div className="m faint" style={{ fontSize: 11 }}>
                  cognito:groups = {session.roles.join(', ')}
                </div>
              </div>
              <Link to="/pedidos">Mis pedidos</Link>
              {session.isAdmin && <Link to="/admin">Panel de administración</Link>}
              <Link to="/perfil">Mi token</Link>
              <button type="button" onClick={session.logout}>
                Cerrar sesión
              </button>
            </div>
          </details>
          <Link to="/carrito" className="btn icon-btn cart-btn" aria-label={`Carrito, ${cart.count} productos`}>
            <CartIcon />
            {cart.count > 0 && <span className="count-badge">{cart.count}</span>}
          </Link>
        </div>
      </header>
      <Outlet />
      <Toast />
    </>
  )
}
