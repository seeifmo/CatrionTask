import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroupDirective,
  NgForm,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { toApiError } from '../../core/http/api-error';

/** Same rules as the backend's RegisterRequest, so most mistakes are caught before a round trip. */
const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).*$/;

/** Group-level rule; the error is shown under the confirm field via ConfirmPasswordErrorMatcher. */
const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value as string;
  const confirm = group.get('confirmPassword')?.value as string;
  return confirm && password !== confirm ? { passwordMismatch: true } : null;
};

/** Marks the confirm field invalid when it has its own error or the passwords don't match. */
class ConfirmPasswordErrorMatcher implements ErrorStateMatcher {
  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const touched = !!control && (control.touched || !!form?.submitted);
    return touched && (!!control?.invalid || !!form?.hasError('passwordMismatch'));
  }
}

type ServerField = 'username' | 'email' | 'fullName' | 'password';

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      username: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(50),
          Validators.pattern(USERNAME_PATTERN),
        ],
      ],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      fullName: ['', [Validators.required, Validators.maxLength(100)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(PASSWORD_PATTERN),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);
  protected readonly confirmPasswordMatcher = new ConfirmPasswordErrorMatcher();

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, email, fullName, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth
      .register({ username, email, fullName, password })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/profile'),
        error: (error: unknown) => {
          const { message, fieldErrors } = toApiError(error);
          let shownOnField = false;
          for (const [field, fieldMessage] of Object.entries(fieldErrors)) {
            const control = this.form.controls[field as ServerField] as AbstractControl | undefined;
            if (control) {
              control.setErrors({ server: fieldMessage });
              control.markAsTouched();
              shownOnField = true;
            }
          }
          // Field errors appear under their inputs; the banner is for everything else.
          this.errorMessage.set(shownOnField ? null : message);
        },
      });
  }
}
