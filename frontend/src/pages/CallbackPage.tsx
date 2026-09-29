import { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { Link, useNavigate } from 'react-router'

/** Landing route for Cognito's redirect; AuthProvider exchanges the code and validates state/nonce. */
export function CallbackPage() {
  const auth = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (auth.isAuthenticated) {
      const returnTo = (auth.user?.state as { returnTo?: string } | undefined)?.returnTo ?? '/dashboard'
      navigate(returnTo, { replace: true })
    }
  }, [auth.isAuthenticated, auth.user, navigate])

  if (auth.error) {
    return (
      <div className="card">
        <h1>No se pudo iniciar sesion</h1>
        <p className="muted">La respuesta del proveedor de identidad no fue valida: {auth.error.message}</p>
        <Link className="btn" to="/">
          Volver
        </Link>
      </div>
    )
  }
  return <p className="muted">Validando sesion...</p>
}
