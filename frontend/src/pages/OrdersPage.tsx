import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchMyOrders, myOrdersQueryKey } from '../api/orderApi';
import { ROUTES } from '../lib/routes';
import { formatTRY, formatTRDate } from '../lib/format';
import { AccountSidebar } from '../components/account/AccountSidebar';
import { OrderStatusBadge } from '../components/account/OrderStatusBadge';
import type { Order } from '../lib/types';

export default function OrdersPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: myOrdersQueryKey,
    queryFn: fetchMyOrders,
  });

  // Phase 5 ORD-03: backend returns orders sorted by date desc; frontend re-sorts defensively
  const orders: Order[] = (data ?? []).slice().sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-6">
        <AccountSidebar />
        <div>
          <h1 className="text-2xl font-bold mb-6">Siparişlerim</h1>
          {isLoading ? (
            <OrdersSkeleton />
          ) : isError ? (
            <div className="bg-white border border-[#DC2626] rounded p-6 text-center">
              <p className="mb-4">Siparişler yüklenemedi.</p>
              <button onClick={() => refetch()} className="bg-[#1C1C1E] text-white px-6 py-2 rounded font-bold">
                Tekrar Dene
              </button>
            </div>
          ) : orders.length === 0 ? (
            <div className="bg-white border border-[var(--color-border)] rounded p-12 text-center">
              <h2 className="text-lg font-bold mb-3">Henüz siparişiniz yok.</h2>
              <Link to={ROUTES.HOME} className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold">
                Alışverişe Başla
              </Link>
            </div>
          ) : (
            <ul className="space-y-3">
              {orders.map(order => (
                <li key={order.id}>
                  <article className="bg-white border border-[var(--color-border)] rounded p-4 flex items-center justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <p className="text-xs text-gray-600">Sipariş No: <span className="font-mono">{order.id.slice(0, 8)}…</span></p>
                      <p className="text-sm mt-1">{formatTRDate(order.createdAt)}</p>
                      <p className="text-base font-bold mt-1">{formatTRY(order.totalAmount)}</p>
                    </div>
                    <OrderStatusBadge status={order.status} />
                    <Link
                      to={ROUTES.ORDER_DETAIL(order.id)}
                      className="text-sm font-bold text-[#1C1C1E] hover:underline whitespace-nowrap"
                    >
                      Detay →
                    </Link>
                  </article>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

function OrdersSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="bg-white border border-[var(--color-border)] rounded p-4 flex items-center justify-between gap-4 animate-pulse">
          <div className="flex-1 space-y-2">
            <div className="h-3 bg-gray-200 rounded w-32" />
            <div className="h-3 bg-gray-200 rounded w-48" />
            <div className="h-5 bg-gray-200 rounded w-24" />
          </div>
          <div className="h-6 bg-gray-200 rounded w-20" />
          <div className="h-4 bg-gray-200 rounded w-16" />
        </div>
      ))}
    </div>
  );
}
