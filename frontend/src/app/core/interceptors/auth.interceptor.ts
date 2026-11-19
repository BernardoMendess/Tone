import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * M4: Attaches in-memory Bearer token and transparently rotates expired access tokens on 401
 * before clearing session and redirecting to /auth.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isAuthEndpoint = req.url.includes('/api/auth/login')
    || req.url.includes('/api/auth/register')
    || req.url.includes('/api/auth/refresh')
    || req.url.includes('/api/auth/logout');

  const token = auth.token();
  let authReq = req;
  if (token && !isAuthEndpoint) {
    authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token && !isAuthEndpoint) {
        return auth.refreshAccessToken().pipe(
          switchMap(refreshed => {
            if (refreshed?.token) {
              const retried = req.clone({
                setHeaders: { Authorization: `Bearer ${refreshed.token}` }
              });
              return next(retried);
            }
            auth.clearSession();
            router.navigate(['/auth'], { queryParams: { redirect: router.url } });
            return throwError(() => err);
          })
        );
      }
      return throwError(() => err);
    })
  );
};
