import { useMemo } from 'react'
import { useAuth } from 'react-oidc-context'
import { cognitoLogoutUrl, signupManager } from './oidc'
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
    username: claims?.username ?? '',
    login: (returnTo = '/dashboard') => auth.signinRedirect({ state: { returnTo } }),
    register: () => signupManager.signinRedirect({ state: { returnTo: '/dashboard' } }),
    logout: async () => {
      await auth.removeUser()
      window.location.assign(cognitoLogoutUrl())
    },
  }
}
