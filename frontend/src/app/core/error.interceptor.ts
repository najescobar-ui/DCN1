import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from './notification.service';

const MESSAGES: Record<number, string> = {
  0: 'No se pudo contactar la API (red o CORS)',
  401: 'Sesion expirada o token invalido (401)',
  403: 'No tienes permisos para esta accion (403)',
  404: 'Recurso no encontrado (404)',
  502: 'Un microservicio no respondio (502)',
};

/** Shows backend errors (problem+json "detail" when available) as a notification. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const detail = typeof error.error?.detail === 'string' ? error.error.detail : null;
      notifications.error(detail ?? MESSAGES[error.status] ?? `Error ${error.status}`);
      return throwError(() => error);
    }),
  );
};
