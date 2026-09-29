import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api';
import { AuthService } from './auth.service';

/**
 * For API calls: sends the auth cookie (needed if the API is ever served cross-origin), and when a
 * session that was active gets a 401 (token expired or revoked) clears it and sends the user to login.
 *
 * No Authorization header is added: the token is an HttpOnly cookie the browser attaches by itself.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const apiBaseUrl = inject(API_BASE_URL);
  if (!req.url.startsWith(apiBaseUrl)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req.clone({ withCredentials: true })).pipe(
    catchError((error: unknown) => {
      const sessionExpired =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.startsWith(`${apiBaseUrl}/auth/`) &&
        auth.isAuthenticated();
      if (sessionExpired) {
        auth.clearSession();
        void router.navigate(['/login'], { queryParams: { reason: 'expired' } });
      }
      return throwError(() => error);
    }),
  );
};
