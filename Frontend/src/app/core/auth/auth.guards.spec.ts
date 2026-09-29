import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard, guestGuard, safeReturnUrl } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  const authenticated = signal(false);
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/profile' } as RouterStateSnapshot;

  beforeEach(() => {
    authenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: authenticated } },
      ],
    });
  });

  const run = (guard: typeof authGuard) => TestBed.runInInjectionContext(() => guard(route, state));
  const serialize = (tree: unknown) => TestBed.inject(Router).serializeUrl(tree as UrlTree);

  it('authGuard lets a logged-in user through', () => {
    authenticated.set(true);
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard sends a logged-out user to login, remembering where they were going', () => {
    expect(serialize(run(authGuard))).toBe('/login?returnUrl=%2Fprofile');
  });

  it('guestGuard lets a logged-out user through', () => {
    expect(run(guestGuard)).toBe(true);
  });

  it('guestGuard sends a logged-in user to their profile', () => {
    authenticated.set(true);
    expect(serialize(run(guestGuard))).toBe('/profile');
  });
});

describe('safeReturnUrl', () => {
  it.each([
    ['/profile', '/profile'],
    ['/profile?tab=1', '/profile?tab=1'],
    [undefined, '/profile'],
    ['', '/profile'],
    ['https://evil.example', '/profile'],
    ['//evil.example', '/profile'],
    ['/\\evil.example', '/profile'],
    ['javascript:alert(1)', '/profile'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });
});
