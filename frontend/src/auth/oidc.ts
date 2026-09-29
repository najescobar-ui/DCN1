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

/** Random value for the OIDC nonce (32 bytes, hex). */
export function nuevoNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Arguments for every authorize request. oidc-client-ts only sends a nonce when one is given;
 * with it, the library checks that the ID token carries the same nonce (replay protection).
 */
export const signinArgs = (returnTo?: string) => ({ state: { returnTo }, nonce: nuevoNonce() })

/**
 * Set while signing out, so route guards do not start a new login in the moment between
 * clearing the local session and leaving for Cognito's /logout.
 */
let signingOut = false
export const isSigningOut = () => signingOut

export async function signOut(): Promise<void> {
  signingOut = true
  await userManager.removeUser()
  window.location.assign(cognitoLogoutUrl())
}

/** Cognito does not publish end_session_endpoint, so its /logout endpoint is called explicitly. */
export function cognitoLogoutUrl(): string {
  const params = new URLSearchParams({ client_id: clientId, logout_uri: window.location.origin })
  return `${domain}/logout?${params}`
}
