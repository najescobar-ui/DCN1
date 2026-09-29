import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsCl from '@angular/common/locales/es-CL';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { LogLevel, authInterceptor, provideAuth, withAppInitializerAuthCheck } from 'angular-auth-oidc-client';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { errorInterceptor } from './core/error.interceptor';

registerLocaleData(localeEsCl);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor(), errorInterceptor])),
    provideAuth(
      {
        config: {
          authority: environment.cognito.authority,
          clientId: environment.cognito.clientId,
          scope: environment.cognito.scope,
          responseType: 'code',
          redirectUrl: `${window.location.origin}/callback`,
          postLogoutRedirectUri: window.location.origin,
          postLoginRoute: '/dashboard',
          forbiddenRoute: '/no-autorizado',
          unauthorizedRoute: '/',
          // The token is only attached to calls to our API, never to third parties.
          secureRoutes: [`${environment.apiUrl}/api/`],
          silentRenew: true,
          useRefreshToken: true,
          renewTimeBeforeTokenExpiresInSeconds: 60,
          // Cognito refresh responses omit refresh_token (no rotation) and nonce.
          allowUnsafeReuseRefreshToken: true,
          ignoreNonceAfterRefresh: true,
          disableRefreshTokenOfflineAccessScopeWarning: true,
          logLevel: environment.production ? LogLevel.Warn : LogLevel.Debug,
        },
      },
      withAppInitializerAuthCheck(),
    ),
    { provide: LOCALE_ID, useValue: 'es-CL' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'CLP' },
  ],
};
