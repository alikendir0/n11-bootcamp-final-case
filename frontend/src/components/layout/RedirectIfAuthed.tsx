import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { ROUTES } from '../../lib/routes';

export function RedirectIfAuthed() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  if (isAuthenticated) return <Navigate to={ROUTES.HOME} replace />;
  return <Outlet />;
}
