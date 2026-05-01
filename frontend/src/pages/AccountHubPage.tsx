import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '../api/authApi';
import { fetchMyOrders, myOrdersQueryKey } from '../api/orderApi';
import { fetchAddresses, addressesQueryKey } from '../api/addressApi';
import { useAuthStore } from '../store/authStore';
import { AccountSidebar } from '../components/account/AccountSidebar';

export default function AccountHubPage() {
  const user = useAuthStore(s => s.user);

  // Lazy /auth/me — only if fullName is missing (hydrateFromStorage only had access to JWT claims)
  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    enabled: !user?.fullName,
    staleTime: 5 * 60_000,
  });

  useEffect(() => {
    // meQuery.data provides fullName for display — consumed directly below
    // No authStore write needed; the display uses the query data directly
  }, [meQuery.data]);

  const ordersQuery = useQuery({ queryKey: myOrdersQueryKey, queryFn: fetchMyOrders });
  const addressesQuery = useQuery({ queryKey: addressesQueryKey, queryFn: fetchAddresses });

  const displayName = meQuery.data?.fullName || user?.fullName || user?.email || 'Misafir';

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-6">
        <AccountSidebar />
        <div>
          <h1 className="text-2xl font-bold mb-2">Hoş geldin, {displayName}</h1>
          <p className="text-sm text-gray-700 mb-6">{user?.email}</p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <StatCard
              label="Toplam Sipariş"
              value={ordersQuery.data?.length ?? '—'}
              isLoading={ordersQuery.isLoading}
            />
            <StatCard
              label="Kayıtlı Adres"
              value={addressesQuery.data?.length ?? '—'}
              isLoading={addressesQuery.isLoading}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, isLoading }: { label: string; value: number | string; isLoading: boolean }) {
  return (
    <div className="bg-white border border-[var(--color-border)] rounded p-6">
      <p className="text-xs text-gray-600 uppercase tracking-wide">{label}</p>
      <p className="text-3xl font-bold mt-2">{isLoading ? '...' : value}</p>
    </div>
  );
}
