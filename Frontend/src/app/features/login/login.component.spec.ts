import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let login: ReturnType<typeof vi.fn>;
  let snackOpen: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function setup(inputs: Record<string, string> = {}) {
    login = vi.fn();
    snackOpen = vi.fn();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        { provide: MatSnackBar, useValue: { open: snackOpen } },
      ],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    fixture = TestBed.createComponent(LoginComponent);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  async function fillAndSubmit(username: string, password: string) {
    const [user, pass] = Array.from(el().querySelectorAll('input'));
    user.value = username;
    user.dispatchEvent(new Event('input'));
    pass.value = password;
    pass.dispatchEvent(new Event('input'));
    el().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('shows required errors and does not call the API when the form is empty', async () => {
    await setup();

    await fillAndSubmit('', '');

    expect(login).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Username is required.');
    expect(el().textContent).toContain('Password is required.');
  });

  it('navigates to the profile after a successful login', async () => {
    await setup();
    login.mockReturnValue(of({ id: 1 }));

    await fillAndSubmit('demo', 'Demo12345');

    expect(login).toHaveBeenCalledWith({ username: 'demo', password: 'Demo12345' });
    expect(navigateByUrl).toHaveBeenCalledWith('/profile');
  });

  it('honours a safe returnUrl', async () => {
    await setup({ returnUrl: '/profile?tab=security' });
    login.mockReturnValue(of({ id: 1 }));

    await fillAndSubmit('demo', 'Demo12345');

    expect(navigateByUrl).toHaveBeenCalledWith('/profile?tab=security');
  });

  it('shows the server message and clears the password on failure', async () => {
    await setup();
    login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { detail: 'Invalid username or password.' },
          }),
      ),
    );

    await fillAndSubmit('demo', 'wrong');

    expect(el().querySelector('[role=alert]')?.textContent).toContain(
      'Invalid username or password.',
    );
    expect(el().querySelectorAll('input')[1].value).toBe('');
    expect(el().textContent).not.toContain('Password is required.');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('tells the user their session expired', async () => {
    await setup({ reason: 'expired' });

    expect(snackOpen).toHaveBeenCalledWith(
      'Your session has expired. Please sign in again.',
      'Dismiss',
      expect.anything(),
    );
  });
});
