import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useNavigate } from 'react-router'
import { useSession } from '../auth/useSession'

/** Landing route for Cognito's redirect; AuthProvider exchanges the code and validates state/nonce. */
export function CallbackPage() {
  const auth = useAuth()
  const session = useSession()
  const navigate = useNavigate()

  useEffect(() => {
    if (auth.isAuthenticated) {
      const requested = (auth.user?.state as { returnTo?: string } | undefined)?.returnTo
      navigate(requested ?? (session.isAdmin ? '/admin' : '/catalogo'), { replace: true })
    }
  }, [auth.isAuthenticated, auth.user, session.isAdmin, navigate])

  return (
    <div className="container page" style={{ minHeight: '100vh', justifyContent: 'center', alignItems: 'center' }}>
      {auth.error ? (
        <div className="card empty" style={{ maxWidth: 480 }}>
          <h1 className="d page-title">No se pudo iniciar sesión</h1>
          <p className="muted" style={{ margin: 0 }}>
            La respuesta del proveedor de identidad no fue válida: {auth.error.message}
          </p>
          <Link className="btn" to="/">
            Volver
          </Link>
        </div>
      ) : (
        <p className="muted">Validando sesión...</p>
      )}
    </div>
  )
}
