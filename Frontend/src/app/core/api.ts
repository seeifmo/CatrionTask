import { InjectionToken } from '@angular/core';

/**
 * Base URL of the backend API. Relative on purpose: in development the Angular dev server proxies
 * `/api` to Spring Boot (see proxy.conf.json), and in production a reverse proxy does the same, so the
 * browser always talks to one origin. That keeps the auth cookie SameSite=Strict and lets Angular's
 * XSRF protection work (it only sends the header to same-origin URLs).
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api',
});
