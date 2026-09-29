import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'
import { config } from '../config'

const { authority, domain, clientId, scope } = config.cognito

/**
 * Authorization Code + PKCE: oidc-client-ts generates code_verifier/code_challenge (S256),
 * state and nonce, and validates state, nonce and the ID token on the callback.
 */
const settings: UserManagerSettings = {
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope,
  // Renews the access token with the refresh token before it expires.
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
}

export const userManager = new UserManager(settings)

/**
 * Same client and PKCE flow, but the authorize request is sent to the hosted UI sign-up page.
 * The callback is processed by the main userManager (shared state store).
 */
export const signupManager = new UserManager({
  ...settings,
  metadata: {
    issuer: authority,
    authorization_endpoint: `${domain}/signup`,
    token_endpoint: `${domain}/oauth2/token`,
    userinfo_endpoint: `${domain}/oauth2/userInfo`,
    jwks_uri: `${authority}/.well-known/jwks.json`,
  },
})

/** Cognito does not publish end_session_endpoint, so its /logout endpoint is called explicitly. */
export function cognitoLogoutUrl(): string {
  const params = new URLSearchParams({ client_id: clientId, logout_uri: window.location.origin })
  return `${domain}/logout?${params}`
}
