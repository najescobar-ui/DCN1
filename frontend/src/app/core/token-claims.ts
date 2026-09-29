/** Role assigned by the backend to users without a Cognito group; kept in sync with default-role. */
export const DEFAULT_ROLE = 'CLIENTE';

export interface AccessTokenClaims {
  sub?: string;
  username?: string;
  scope?: string;
  exp?: number;
  'cognito:groups'?: string[];
  [claim: string]: unknown;
}

export function rolesFrom(claims: AccessTokenClaims | null | undefined): string[] {
  if (!claims) {
    return [];
  }
  const groups = claims['cognito:groups'] ?? [];
  return groups.length ? groups.map((g) => g.toUpperCase()) : [DEFAULT_ROLE];
}

export function scopesFrom(claims: AccessTokenClaims | null | undefined): string[] {
  return claims?.scope ? claims.scope.split(' ').filter(Boolean) : [];
}
