import { useMemo } from 'react'
import { useAuth } from 'react-oidc-context'
import { signOut, signinArgs, signupManager } from './oidc'
import { decodeJwt, rolesFrom, scopesFrom } from './tokenClaims'

/** Session data derived from the access token (the one sent to API Gateway). */
export function useSession() {
  const auth = useAuth()
  const accessToken = auth.user?.access_token
  const claims = useMemo(() => decodeJwt(accessToken), [accessToken])
  const roles = useMemo(() => rolesFrom(claims), [claims])

  return {
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    accessToken,
    claims,
    idClaims: auth.user?.profile,
    roles,
    scopes: scopesFrom(claims),
    isAdmin: roles.includes('ADMIN'),
    /** Nombre del rol para mostrar en la interfaz. */
    rolVisible: roles.includes('ADMIN') ? 'Administrador' : 'Cliente',
    username: claims?.username ?? '',
    login: (returnTo?: string) => auth.signinRedirect(signinArgs(returnTo)),
    register: () => signupManager.signinRedirect(signinArgs()),
    // Clears the local session and ends the Cognito session (its cookie would log the user back in).
    logout: signOut,
  }
}
