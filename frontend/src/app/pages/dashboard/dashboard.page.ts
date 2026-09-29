import { CurrencyPipe, DatePipe, KeyValuePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Resumen } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe, KeyValuePipe, RouterLink],
  templateUrl: './dashboard.page.html',
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  protected readonly resumen = toSignal(
    inject(ApiService)
      .resumen()
      .pipe(catchError(() => of(null as Resumen | null))),
  );
}
