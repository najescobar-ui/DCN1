import { useEffect, type ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { Navigate, useLocation } from 'react-router'
import { isSigningOut } from './oidc'
import { useSession } from './useSession'

interface Props {
  /** Roles allowed to enter; any authenticated user when omitted. */
  roles?: string[]
  children: ReactNode
}

/** Route guard: starts the login when there is no session and checks roles from the token. */
export function ProtectedRoute({ roles, children }: Props) {
  const auth = useAuth()
  const session = useSession()
  const location = useLocation()
  const mustLogin =
    !auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error && !isSigningOut()

  useEffect(() => {
    if (mustLogin) {
      void auth.signinRedirect({ state: { returnTo: location.pathname } })
    }
  }, [mustLogin, auth, location.pathname])

  if (auth.error) {
    return <p className="error">Error de autenticación: {auth.error.message}</p>
  }
  if (!auth.isAuthenticated) {
    return <p className="container page muted">{isSigningOut() ? 'Cerrando sesión...' : 'Redirigiendo al inicio de sesión...'}</p>
  }
  if (roles && !roles.some((role) => session.roles.includes(role))) {
    return <Navigate to="/no-autorizado" replace />
  }
  return children
}
