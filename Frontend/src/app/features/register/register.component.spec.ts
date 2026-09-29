import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let register: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    register = vi.fn();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { register } }],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(RegisterComponent);
    await fixture.whenStable();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  async function fill(values: string[]) {
    // Order in the template: full name, username, email, password, confirm password.
    Array.from(el().querySelectorAll('input')).forEach((input, i) => {
      input.value = values[i];
      input.dispatchEvent(new Event('input'));
    });
    el().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('blocks submission when the passwords do not match', async () => {
    await fill(['Jane', 'jane', 'jane@example.com', 'Secret123', 'Secret124']);

    expect(register).not.toHaveBeenCalled();
    expect(el().textContent).toContain("Passwords don't match.");
  });

  it('enforces the password rule (letter and digit) before calling the API', async () => {
    await fill(['Jane', 'jane', 'jane@example.com', 'onlyletters', 'onlyletters']);

    expect(register).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Include at least one letter and one digit.');
  });

  it('sends the request without confirmPassword and goes to the profile', async () => {
    register.mockReturnValue(of({ id: 2 }));

    await fill(['Jane', 'jane', 'jane@example.com', 'Secret123', 'Secret123']);

    expect(register).toHaveBeenCalledWith({
      fullName: 'Jane',
      username: 'jane',
      email: 'jane@example.com',
      password: 'Secret123',
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/profile');
  });

  it('shows a 409 field error under the matching input, not in the banner', async () => {
    register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              detail: 'Username is already taken.',
              errors: { username: 'Username is already taken.' },
            },
          }),
      ),
    );

    await fill(['Jane', 'demo', 'jane@example.com', 'Secret123', 'Secret123']);

    expect(el().querySelector('mat-error')?.textContent).toContain('Username is already taken.');
    expect(el().querySelector('[role=alert]')?.textContent?.trim()).toBe('');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows errors without a matching field in the banner', async () => {
    register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    await fill(['Jane', 'jane', 'jane@example.com', 'Secret123', 'Secret123']);

    expect(el().querySelector('[role=alert]')?.textContent).toContain('Cannot reach the server');
  });
});
