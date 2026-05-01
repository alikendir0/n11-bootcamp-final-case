// @vitest-environment node
// Run in Node environment to avoid jsdom cross-realm Uint8Array issue with jose SignJWT
// localStorage is mocked below for the storage tests

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { getToken, setToken, clearToken, isExpired } from './tokenStore';
import { SignJWT } from 'jose';

const SECRET = new TextEncoder().encode('test-secret-only-for-unit-test');

async function makeJwt(expSecondsFromNow: number): Promise<string> {
  return await new SignJWT({})
    .setProtectedHeader({ alg: 'HS256' })
    .setExpirationTime(Math.floor(Date.now() / 1000) + expSecondsFromNow)
    .sign(SECRET);
}

// Mock localStorage for Node environment (not available in pure Node)
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
  };
})();

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  writable: true,
});

describe('tokenStore', () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  it('returns null when no token stored', () => {
    expect(getToken()).toBeNull();
  });

  it('round-trips token through localStorage', () => {
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
  });

  it('clearToken removes the stored value', () => {
    setToken('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });

  it('isExpired returns true for tokens that expire within 5s', async () => {
    const token = await makeJwt(2);  // expires in 2s — inside safety margin
    expect(isExpired(token)).toBe(true);
  });

  it('isExpired returns false for tokens valid beyond safety margin', async () => {
    const token = await makeJwt(120);  // 2 minutes
    expect(isExpired(token)).toBe(false);
  });

  it('isExpired returns true for malformed tokens', () => {
    expect(isExpired('not.a.jwt')).toBe(true);
    expect(isExpired('')).toBe(true);
  });
});
