import { Navigate, useLocation, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { ROUTES } from '../../lib/routes';

export function RequireAuth() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const location = useLocation();
  if (!isAuthenticated) {
    const redirectUrl = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`} replace />;
  }
  return <Outlet />;
}
