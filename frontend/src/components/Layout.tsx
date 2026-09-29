import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { useSession } from '../auth/useSession'
import { notify, type Notification } from '../utils/notify'

export function Layout() {
  const session = useSession()
  const [toast, setToast] = useState<Notification | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)

  useEffect(
    () =>
      notify.subscribe((notification) => {
        clearTimeout(timer.current)
        setToast(notification)
        timer.current = setTimeout(() => setToast(null), 5000)
      }),
    [],
  )

  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          Pedidos<span>360</span>
        </Link>
        {session.isAuthenticated ? (
          <>
            <nav>
              <NavLink to="/dashboard">Inicio</NavLink>
              <NavLink to="/productos">Productos</NavLink>
              <NavLink to="/pedidos" end>
                Pedidos
              </NavLink>
              <NavLink to="/pedidos/nuevo">Nuevo pedido</NavLink>
              <NavLink to="/perfil">Mi token</NavLink>
            </nav>
            <div className="user">
              <span>
                {session.username} · {session.roles.join(', ')}
              </span>
              <button className="btn small secondary" type="button" onClick={session.logout}>
                Cerrar sesion
              </button>
            </div>
          </>
        ) : (
          <div className="user">
            <button className="btn small secondary" type="button" onClick={session.register}>
              Crear cuenta
            </button>
            <button className="btn small" type="button" onClick={() => session.login()}>
              Iniciar sesion
            </button>
          </div>
        )}
      </header>

      {toast && (
        <div className={`toast ${toast.kind === 'error' ? 'error' : ''}`} role="status" onClick={() => setToast(null)}>
          {toast.text}
        </div>
      )}

      <main>
        <Outlet />
      </main>
    </>
  )
}
