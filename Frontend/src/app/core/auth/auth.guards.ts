import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Only for logged-in users. The session is restored before the first navigation (see app.config.ts),
 * so the signal is already accurate here.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Only for logged-out users (login, register): a logged-in user goes straight to the profile. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/profile']) : true;

/**
 * Only allows in-app paths after login, so `?returnUrl=https://evil.example` can't redirect the user
 * off-site (open-redirect protection).
 */
export function safeReturnUrl(url: string | null | undefined, fallback = '/profile'): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')
    ? url
    : fallback;
}
