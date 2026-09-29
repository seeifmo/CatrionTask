import { HttpErrorResponse } from '@angular/common/http';

/** RFC 9457 body the backend returns for every error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Field name → message, for validation (400) and duplicate (409) errors. */
  errors?: Record<string, string>;
}

export interface ApiError {
  message: string;
  fieldErrors: Record<string, string>;
}

const GENERIC_MESSAGE = 'Something went wrong. Please try again.';
const OFFLINE_MESSAGE = 'Cannot reach the server. Check your connection and try again.';

/** Turns any HTTP failure into a message that is safe to show, plus per-field errors. */
export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: GENERIC_MESSAGE, fieldErrors: {} };
  }
  if (error.status === 0) {
    return { message: OFFLINE_MESSAGE, fieldErrors: {} };
  }
  const body = isProblemDetail(error.error) ? error.error : null;
  return {
    message: body?.detail ?? body?.title ?? GENERIC_MESSAGE,
    fieldErrors: body?.errors ?? {},
  };
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && ('detail' in value || 'title' in value);
}
