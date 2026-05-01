import { apiFetch } from '../lib/apiClient';
import type { LoginResponse, User } from '../lib/types';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export function loginRequest(body: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/identity/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function registerRequest(body: RegisterRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/identity/auth/register', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function fetchMe(): Promise<User> {
  return apiFetch<User>('/identity/auth/me');
}
