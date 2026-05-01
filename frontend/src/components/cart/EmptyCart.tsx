import { Link } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';
import { useAuthStore } from '../../store/authStore';

export function EmptyCart() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  return (
    <div className="bg-white border border-[var(--color-border)] rounded p-12 text-center max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-3">Sepetin Boş Görünüyor</h1>
      <p className="text-gray-700 mb-6">Alışverişe başlamak için ürün ekleyin.</p>
      <Link
        to={ROUTES.HOME}
        className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold mr-3"
      >
        Alışverişe Başla
      </Link>
      {!isAuthenticated && (
        <Link
          to={ROUTES.LOGIN}
          className="inline-block border border-[#1C1C1E] text-[#1C1C1E] px-6 py-3 rounded font-bold"
        >
          Hemen Giriş Yap
        </Link>
      )}
    </div>
  );
}
