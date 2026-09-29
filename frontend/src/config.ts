const env = import.meta.env

export const config = {
  /** API Gateway base URL; empty in local development (Vite proxy). */
  apiUrl: env.VITE_API_URL ?? '',
  /** Optional Mapbox public token (pk.*) for address search and map tiles; OpenStreetMap is used without it. */
  mapboxToken: env.VITE_MAPBOX_TOKEN ?? '',
  cognito: {
    /** User pool issuer; OIDC discovery lives at {authority}/.well-known/openid-configuration. */
    authority: env.VITE_COGNITO_AUTHORITY,
    /** Hosted UI domain, used for sign-up and logout. */
    domain: env.VITE_COGNITO_DOMAIN,
    clientId: env.VITE_COGNITO_CLIENT_ID,
    scope: env.VITE_COGNITO_SCOPE,
  },
}
