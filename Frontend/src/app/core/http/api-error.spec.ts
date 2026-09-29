import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error';

describe('toApiError', () => {
  it('uses the ProblemDetail detail and field errors', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        title: 'Conflict',
        detail: 'Username is already taken.',
        errors: { username: 'Taken' },
      },
    });

    expect(toApiError(error)).toEqual({
      message: 'Username is already taken.',
      fieldErrors: { username: 'Taken' },
    });
  });

  it('explains a network failure (status 0)', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).message).toContain(
      'Cannot reach the server',
    );
  });

  it('falls back to a generic message for unexpected bodies and non-HTTP errors', () => {
    expect(
      toApiError(new HttpErrorResponse({ status: 502, error: '<html>Bad gateway</html>' })).message,
    ).toBe('Something went wrong. Please try again.');
    expect(toApiError(new Error('boom')).fieldErrors).toEqual({});
  });
});
