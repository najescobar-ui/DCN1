import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map, of, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AccessTokenClaims, rolesFrom, scopesFrom } from './token-claims';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);

  readonly isAuthenticated = toSignal(this.oidc.isAuthenticated$.pipe(map((r) => r.isAuthenticated)), {
    initialValue: false,
  });

  /** Claims of the access token, the one sent to API Gateway and the microservices. */
  readonly claims = toSignal(
    this.oidc.isAuthenticated$.pipe(
      switchMap((r) =>
        r.isAuthenticated ? this.oidc.getPayloadFromAccessToken() : of(null),
      ),
      map((claims) => claims as AccessTokenClaims | null),
    ),
    { initialValue: null },
  );

  readonly roles = computed(() => rolesFrom(this.claims()));
  readonly scopes = computed(() => scopesFrom(this.claims()));
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));
  readonly username = computed(() => this.claims()?.username ?? '');

  /** Authorization Code + PKCE: the library creates code_verifier, code_challenge, state and nonce. */
  login(): void {
    this.oidc.authorize();
  }

  /** Same authorize request, sent to the managed login sign-up page instead. */
  register(): void {
    this.oidc.authorize(undefined, {
      urlHandler: (url) => window.location.assign(url.replace('/oauth2/authorize', '/signup')),
    });
  }

  /** Cognito does not publish end_session_endpoint, so its /logout endpoint is called explicitly. */
  logout(): void {
    this.oidc.logoffLocal();
    const params = new URLSearchParams({
      client_id: environment.cognito.clientId,
      logout_uri: window.location.origin,
    });
    window.location.assign(`${environment.cognito.domain}/logout?${params}`);
  }

  accessTokenPayload() {
    return this.oidc.getPayloadFromAccessToken();
  }

  idTokenPayload() {
    return this.oidc.getPayloadFromIdToken();
  }

  accessToken() {
    return this.oidc.getAccessToken();
  }
}
