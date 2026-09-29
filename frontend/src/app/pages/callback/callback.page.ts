import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { take } from 'rxjs';

/** Landing route for Cognito's redirect; the code exchange already ran in the app initializer. */
@Component({
  selector: 'app-callback',
  imports: [RouterLink],
  template: `
    @if (failed()) {
      <div class="card">
        <h1>No se pudo iniciar sesion</h1>
        <p class="muted">La respuesta del proveedor de identidad no fue valida (state, nonce o codigo).</p>
        <a class="btn" routerLink="/">Volver</a>
      </div>
    } @else {
      <p class="muted">Validando sesion...</p>
    }
  `,
})
export class CallbackPage implements OnInit {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);
  protected readonly failed = signal(false);

  ngOnInit(): void {
    this.oidc.isAuthenticated$.pipe(take(1)).subscribe(({ isAuthenticated }) => {
      if (!isAuthenticated) {
        this.failed.set(true);
      } else if (this.router.url.startsWith('/callback')) {
        this.router.navigateByUrl('/dashboard');
      }
    });
  }
}
