import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const url = new URL(req.url, window.location.origin);
  const esApi = (url.origin === 'http://localhost:8080' || url.origin === window.location.origin)
    && url.pathname.startsWith('/api/');
  if (!esApi) return next(req);

  const token = authService.obtenerToken();
  const peticion = token && url.pathname !== '/api/auth/login'
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;
  return next(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403) {
        authService.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
