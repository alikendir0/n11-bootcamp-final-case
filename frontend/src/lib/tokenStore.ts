import { decodeJwt } from 'jose';

const STORAGE_KEY = 'n11_auth_token';
const SAFETY_MARGIN_MS = 5_000;  // D-04 — 5-second safety margin

export function getToken(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;  // SSR or privacy-mode browsers
  }
}

export function setToken(token: string): void {
  localStorage.setItem(STORAGE_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export function isExpired(token: string): boolean {
  try {
    const { exp } = decodeJwt(token);
    if (typeof exp !== 'number') return true;
    return exp * 1000 <= Date.now() + SAFETY_MARGIN_MS;
  } catch {
    return true;  // malformed token → treat as expired
  }
}
