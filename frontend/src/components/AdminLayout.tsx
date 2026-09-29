import { NavLink, Outlet } from 'react-router'
import { useSession } from '../auth/useSession'
import { Brand } from './Brand'
import { Toast } from './Toast'

export function AdminLayout() {
  const session = useSession()
  return (
    <div className="admin">
      <aside className="admin-side">
        <Brand to="/admin" />
        <nav className="admin-nav" aria-label="Administración">
          <NavLink to="/admin" end>
            Pedidos
          </NavLink>
          <NavLink to="/admin/productos">Productos</NavLink>
          <NavLink to="/catalogo">Ver tienda</NavLink>
          <NavLink to="/admin/token">Mi token</NavLink>
        </nav>
        <div className="admin-user">
          <strong style={{ fontSize: 14 }}>{session.username}</strong>
          <span className="accent" style={{ fontSize: 13 }}>
            {session.rolVisible}
          </span>
          <button type="button" onClick={session.logout}>
            Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="admin-main">
        <Outlet />
      </main>
      <Toast />
    </div>
  )
}
