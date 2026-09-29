import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <section class="card hero">
      <h1>Pedidos360</h1>
      <p class="muted">
        Haz tus pedidos en linea y sigue su estado en tiempo real. Inicia sesion con tu cuenta o registrate.
      </p>
      <div class="actions">
        @if (auth.isAuthenticated()) {
          <a class="btn" routerLink="/dashboard">Ir a mi panel</a>
        } @else {
          <button class="btn" type="button" (click)="auth.login()">Iniciar sesion</button>
          <button class="btn secondary" type="button" (click)="auth.register()">Crear cuenta</button>
        }
      </div>
    </section>
    <section class="grid">
      <div class="card">
        <h2>Autenticacion</h2>
        <p class="muted">Amazon Cognito con OpenID Connect, flujo Authorization Code + PKCE.</p>
      </div>
      <div class="card">
        <h2>API protegida</h2>
        <p class="muted">AWS API Gateway valida el JWT antes de llegar a los microservicios.</p>
      </div>
      <div class="card">
        <h2>Microservicios</h2>
        <p class="muted">Spring Boot en EC2: productos, pedidos y un BFF, sobre PostgreSQL en RDS.</p>
      </div>
    </section>
  `,
  styles: `
    .hero {
      padding: 40px 32px;
    }
  `,
})
export class HomePage {
  protected readonly auth = inject(AuthService);
}
