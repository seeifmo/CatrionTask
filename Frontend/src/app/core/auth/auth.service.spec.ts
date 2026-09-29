import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { User } from '../user/user.model';
import { AuthService } from './auth.service';

const demo: User = {
  id: 1,
  username: 'demo',
  email: 'demo@example.com',
  fullName: 'Demo User',
  role: 'USER',
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in, then loads the current user into the session', () => {
    let result: User | undefined;
    service.login({ username: 'demo', password: 'Demo12345' }).subscribe((u) => (result = u));

    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ username: 'demo', password: 'Demo12345' });
    login.flush(null, { status: 204, statusText: 'No Content' });

    http.expectOne('/api/users/me').flush(demo);

    expect(result).toEqual(demo);
    expect(service.currentUser()).toEqual(demo);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('does not start a session when login fails', () => {
    let failed = false;
    service
      .login({ username: 'demo', password: 'bad' })
      .subscribe({ error: () => (failed = true) });

    http
      .expectOne('/api/auth/login')
      .flush(
        { detail: 'Invalid username or password.' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(failed).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('registers, then logs in with the same credentials', () => {
    service
      .register({
        username: 'alice',
        email: 'a@example.com',
        fullName: 'Alice',
        password: 'Passw0rd',
      })
      .subscribe();

    http
      .expectOne('/api/auth/register')
      .flush({ ...demo, username: 'alice' }, { status: 201, statusText: 'Created' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ username: 'alice', password: 'Passw0rd' });
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/users/me').flush({ ...demo, username: 'alice' });

    expect(service.currentUser()?.username).toBe('alice');
  });

  it('restoreSession resolves to null instead of failing when there is no session', () => {
    let result: User | null | undefined;
    service.restoreSession().subscribe((u) => (result = u));

    http.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout clears the session even when the server call fails', () => {
    service.restoreSession().subscribe();
    http.expectOne('/api/users/me').flush(demo);
    expect(service.isAuthenticated()).toBe(true);

    let completed = false;
    service.logout().subscribe({ complete: () => (completed = true) });
    http.expectOne('/api/auth/logout').flush({}, { status: 500, statusText: 'Server Error' });

    expect(completed).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
  });
});
