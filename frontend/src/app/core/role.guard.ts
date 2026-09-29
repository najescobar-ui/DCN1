import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map, take } from 'rxjs';
import { rolesFrom } from './token-claims';

/** Allows the route only if the access token has one of the roles listed in route data "roles". */
export const roleGuard: CanActivateFn = (route) => {
  const required = (route.data['roles'] as string[] | undefined) ?? [];
  const router = inject(Router);
  return inject(OidcSecurityService)
    .getPayloadFromAccessToken()
    .pipe(
      take(1),
      map((claims) =>
        required.length === 0 || rolesFrom(claims).some((r) => required.includes(r))
          ? true
          : router.createUrlTree(['/no-autorizado']),
      ),
    );
};
