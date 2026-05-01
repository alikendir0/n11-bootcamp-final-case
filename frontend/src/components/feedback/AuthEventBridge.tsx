import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuthStore } from '../../store/authStore';
import { ROUTES } from '../../lib/routes';

interface UnauthorizedDetail {
  redirectUrl: string;
}

export function AuthEventBridge() {
  const navigate = useNavigate();

  useEffect(() => {
    function handler(event: Event) {
      const e = event as CustomEvent<UnauthorizedDetail>;
      const redirectUrl = e.detail?.redirectUrl ?? '/';
      useAuthStore.getState().logout();

      // Avoid showing the toast if user is already on the auth pages
      const path = window.location.pathname;
      if (path !== ROUTES.LOGIN && path !== ROUTES.REGISTER) {
        toast.error('Oturum süreniz doldu, lütfen tekrar giriş yapın.');
      }

      const target = `${ROUTES.LOGIN}?redirectUrl=${encodeURIComponent(redirectUrl)}`;
      navigate(target, { replace: true });
    }

    window.addEventListener('auth:unauthorized', handler as EventListener);
    return () => window.removeEventListener('auth:unauthorized', handler as EventListener);
  }, [navigate]);

  return null;
}
