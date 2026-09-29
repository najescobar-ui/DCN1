import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-no-autorizado',
  imports: [RouterLink],
  template: `
    <div class="card">
      <h1>Acceso denegado</h1>
      <p class="muted">Tu rol no tiene permiso para ver esta seccion.</p>
      <a class="btn" routerLink="/dashboard">Volver al inicio</a>
    </div>
  `,
})
export class NoAutorizadoPage {}
