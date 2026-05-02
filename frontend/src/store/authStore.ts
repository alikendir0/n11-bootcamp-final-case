import { create } from 'zustand';
import { decodeJwt } from 'jose';
import { getToken, setToken, clearToken, isExpired } from '../lib/tokenStore';
import type { User } from '../lib/types';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  hasHydrated: boolean;
  setSession: (token: string, user: User) => void;
  hydrateFromStorage: () => void;     // call once on App mount; D-04 boot validation
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  hasHydrated: false,
  setSession: (token, user) => {
    setToken(token);
    set({ user, isAuthenticated: true, hasHydrated: true });
  },
  hydrateFromStorage: () => {
    const token = getToken();
    if (!token || isExpired(token)) {
      clearToken();
      set({ user: null, isAuthenticated: false, hasHydrated: true });
      return;
    }
    // Token present + not expired — extract minimal user info from sub claim;
    // full /auth/me fetch is lazy (when account UI renders).
    try {
      const claims = decodeJwt(token) as { sub?: string; email?: string; roles?: string[] };
      if (!claims.sub) {
        clearToken();
        set({ user: null, isAuthenticated: false, hasHydrated: true });
        return;
      }
      set({
        user: {
          id: claims.sub,
          email: claims.email ?? '',
          fullName: '',  // populated lazily by GET /auth/me when account UI renders
          roles: claims.roles ?? [],
        },
        isAuthenticated: true,
        hasHydrated: true,
      });
    } catch {
      clearToken();
      set({ user: null, isAuthenticated: false, hasHydrated: true });
    }
  },
  logout: () => {
    clearToken();
    set({ user: null, isAuthenticated: false, hasHydrated: true });
  },
}));
