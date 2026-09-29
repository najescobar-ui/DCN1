/** Role assigned by the backend to users without a Cognito group; kept in sync with default-role. */
export const DEFAULT_ROLE = 'CLIENTE'

export interface AccessTokenClaims {
  sub?: string
  username?: string
  scope?: string
  exp?: number
  iat?: number
  client_id?: string
  token_use?: string
  'cognito:groups'?: string[]
  [claim: string]: unknown
}

/** Reads the JWT payload. Only for display/UI decisions: the signature is verified by the backend. */
export function decodeJwt(token: string | undefined): AccessTokenClaims | null {
  const payload = token?.split('.')[1]
  if (!payload) {
    return null
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as AccessTokenClaims
  } catch {
    return null
  }
}

export function rolesFrom(claims: AccessTokenClaims | null | undefined): string[] {
  if (!claims) {
    return []
  }
  const groups = claims['cognito:groups'] ?? []
  return groups.length ? groups.map((g) => g.toUpperCase()) : [DEFAULT_ROLE]
}

export function scopesFrom(claims: AccessTokenClaims | null | undefined): string[] {
  return claims?.scope ? claims.scope.split(' ').filter(Boolean) : []
}
