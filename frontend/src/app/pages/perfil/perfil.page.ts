import { DatePipe, JsonPipe } from '@angular/common';
import { HttpBackend, HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

interface Prueba {
  titulo: string;
  status: number;
  body: unknown;
}

/** Shows the tokens and lets you compare calls with, without and with a tampered token. */
@Component({
  selector: 'app-perfil',
  imports: [DatePipe, JsonPipe],
  templateUrl: './perfil.page.html',
})
export class PerfilPage {
  private readonly auth = inject(AuthService);
  // HttpBackend skips the interceptors, so these requests carry exactly the headers we set.
  private readonly rawHttp = new HttpClient(inject(HttpBackend));
  private readonly meUrl = `${environment.apiUrl}/api/bff/me`;

  protected readonly me = toSignal(inject(ApiService).me().pipe(catchError(() => of(null))));
  protected readonly accessClaims = toSignal(this.auth.accessTokenPayload());
  protected readonly idClaims = toSignal(this.auth.idTokenPayload());
  protected readonly accessToken = toSignal(this.auth.accessToken());
  protected readonly prueba = signal<Prueba | null>(null);

  probarSinToken(): void {
    this.llamar('Sin Authorization header', {});
  }

  probarTokenAlterado(): void {
    const token = this.accessToken() ?? '';
    // Flip the last signature character: same claims, invalid signature.
    const alterado = token.slice(0, -1) + (token.endsWith('A') ? 'B' : 'A');
    this.llamar('Token con firma alterada', { Authorization: `Bearer ${alterado}` });
  }

  probarTokenValido(): void {
    this.llamar('Token valido', { Authorization: `Bearer ${this.accessToken()}` });
  }

  private llamar(titulo: string, headers: Record<string, string>): void {
    this.rawHttp.get(this.meUrl, { headers, observe: 'response' }).subscribe({
      next: (res) => this.prueba.set({ titulo, status: res.status, body: res.body }),
      error: (err: HttpErrorResponse) => this.prueba.set({ titulo, status: err.status, body: err.error }),
    });
  }

  protected fecha(epochSeconds: unknown): Date | null {
    return typeof epochSeconds === 'number' ? new Date(epochSeconds * 1000) : null;
  }
}
