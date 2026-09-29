import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { API_BASE_URL } from '../api';
import { User } from '../user/user.model';
import { UserService } from '../user/user.service';
import { LoginRequest, RegisterRequest } from './auth.models';

/**
 * Owns the session state. The JWT lives in an HttpOnly cookie that JavaScript cannot read, so
 * "logged in" means "the server returned the current user", and that user is kept in a signal.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);
  private readonly authUrl = `${inject(API_BASE_URL)}/auth`;

  private readonly user = signal<User | null>(null);

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  /** Logs in (the server sets the cookie), then loads the profile. */
  login(credentials: LoginRequest): Observable<User> {
    return this.http
      .post<void>(`${this.authUrl}/login`, credentials)
      .pipe(switchMap(() => this.loadCurrentUser()));
  }

  /** Creates the account, then logs straight in with the same credentials. */
  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<User>(`${this.authUrl}/register`, request)
      .pipe(
        switchMap(() => this.login({ username: request.username, password: request.password })),
      );
  }

  /** Clears the session locally even if the server call fails, so the user is never stuck. */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/logout`, null).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      finalize(() => this.clearSession()),
    );
  }

  loadCurrentUser(): Observable<User> {
    return this.userService.getMe().pipe(tap((user) => this.user.set(user)));
  }

  /** Runs once at startup so a page reload keeps a still-valid session. Never fails. */
  restoreSession(): Observable<User | null> {
    return this.loadCurrentUser().pipe(catchError(() => of(null)));
  }

  clearSession(): void {
    this.user.set(null);
  }
}
