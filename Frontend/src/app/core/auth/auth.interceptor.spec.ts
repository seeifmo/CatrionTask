import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let clearSession: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authenticated = signal(true);
    clearSession = vi.fn();
    navigate = vi.fn().mockResolvedValue(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: authenticated, clearSession } },
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('sends credentials with API requests', () => {
    http.get('/api/users/me').subscribe();

    const req = controller.expectOne('/api/users/me');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('leaves non-API requests alone', () => {
    http.get('/assets/config.json').subscribe();

    const req = controller.expectOne('/assets/config.json');
    expect(req.request.withCredentials).toBe(false);
    req.flush({});
  });

  it('on 401 for an active session: clears it and redirects to login with reason=expired', () => {
    let failed = false;
    http.get('/api/users/me').subscribe({ error: () => (failed = true) });

    controller.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(clearSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });

  it('does not redirect on a 401 from the login endpoint (wrong password)', () => {
    authenticated.set(false);
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    controller.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not redirect on a 401 when there was no session (startup check)', () => {
    authenticated.set(false);
    http.get('/api/users/me').subscribe({ error: () => undefined });

    controller.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(clearSession).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
