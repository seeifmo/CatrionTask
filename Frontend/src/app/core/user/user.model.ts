export type Role = 'USER' | 'ADMIN';

/** Mirrors the backend's UserResponse. The API never returns a password. */
export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: Role;
}
