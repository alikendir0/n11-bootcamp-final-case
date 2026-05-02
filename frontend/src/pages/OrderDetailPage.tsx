import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { fetchOrder, cancelOrder, orderQueryKey, myOrdersQueryKey } from '../api/orderApi';
import { fetchPaymentForOrder, paymentForOrderQueryKey } from '../api/paymentApi';
import { isCancellable } from '../lib/orderStatus';
import { formatTRY, formatTRDate } from '../lib/format';
import { ApiError } from '../lib/apiClient';
import { AccountSidebar } from '../components/account/AccountSidebar';
import { OrderTimeline } from '../components/account/OrderTimeline';
import { CancelOrderDialog } from '../components/account/CancelOrderDialog';
import NotFoundPage from './NotFoundPage';
import type { PaymentStatus } from '../lib/types';

function paymentMethodLabel(method?: string | null): string {
  if (!method || method === 'CARD' || method === 'CREDIT_CARD') return 'Kredi Kartı';
  return method;
}

function paymentStatusLabel(status?: PaymentStatus['status']): string {
  switch (status) {
    case 'COMPLETED': return 'Ödeme tamamlandı';
    case 'FAILED': return 'Ödeme başarısız';
    case 'TIMED_OUT': return 'Ödeme zaman aşımına uğradı';
    case 'PENDING': return 'Ödeme bekleniyor';
    case 'PENDING_INITIALIZATION': return 'Ödeme hazırlanıyor';
    default: return 'Ödeme bilgisi bekleniyor';
  }
}

export default function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const qc = useQueryClient();
  const [showCancelDialog, setShowCancelDialog] = useState(false);

  const { data: order, isLoading, isError, error } = useQuery({
    queryKey: orderId ? orderQueryKey(orderId) : ['order', 'invalid'],
    queryFn: () => fetchOrder(orderId!),
    enabled: !!orderId,
  });

  const { data: payment, isLoading: isPaymentLoading } = useQuery({
    queryKey: orderId ? paymentForOrderQueryKey(orderId) : ['payment', 'invalid'],
    queryFn: () => fetchPaymentForOrder(orderId!),
    enabled: !!orderId && !!order,
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: orderQueryKey(orderId!) });
      qc.invalidateQueries({ queryKey: myOrdersQueryKey });
      toast.success('Sipariş iptal edildi.');
      setShowCancelDialog(false);
    },
    onError: (err) => {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Sipariş iptal edilemedi.');
    },
  });

  if (isError && error instanceof ApiError && error.status === 404) return <NotFoundPage />;

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-6">
        <AccountSidebar />
        <div>
          <h1 className="text-2xl font-bold mb-2">Sipariş Detayı</h1>
          {isLoading || !order ? (
            <DetailSkeleton />
          ) : (
            <>
              <p className="text-sm text-gray-700 mb-6">
                Sipariş No: <span className="font-mono">{order.id}</span> · {formatTRDate(order.createdAt)}
              </p>

              <section className="bg-white border border-[var(--color-border)] rounded p-6 mb-6">
                <h2 className="font-bold mb-4">Sipariş Durumu</h2>
                <OrderTimeline
                  status={order.status}
                  {...(order.cancelReason ? { cancelReason: order.cancelReason } : {})}
                />
              </section>

              <section className="bg-white border border-[var(--color-border)] rounded p-6 mb-6">
                <h2 className="font-bold mb-4">Ürünler</h2>
                <ul className="divide-y divide-[var(--color-border)]">
                  {order.items.map(item => (
                    <li key={item.productId} className="py-3 flex justify-between gap-4">
                      <div>
                        <p className="text-sm font-bold">{item.nameSnapshot}</p>
                        <p className="text-xs text-gray-600">
                          {item.qty} × {formatTRY(item.unitPrice)}
                        </p>
                      </div>
                      <p className="text-sm font-bold whitespace-nowrap">
                        {formatTRY(item.qty * item.unitPrice)}
                      </p>
                    </li>
                  ))}
                </ul>
                <div className="mt-4 pt-3 border-t border-[var(--color-border)] flex justify-between">
                  <span className="font-bold">Toplam</span>
                  <span className="font-bold">{formatTRY(order.totalAmount)}</span>
                </div>
              </section>

              <section className="bg-white border border-[var(--color-border)] rounded p-6 mb-6">
                <h2 className="font-bold mb-4">Teslimat Adresi</h2>
                <p className="text-sm">{order.shippingAddress.recipientName}</p>
                <p className="text-sm">{order.shippingAddress.phone}</p>
                <p className="text-sm text-gray-700 mt-1">
                  {order.shippingAddress.mahalle}, {order.shippingAddress.streetLine}
                </p>
                <p className="text-sm text-gray-700">
                  {order.shippingAddress.ilce} / {order.shippingAddress.il} {order.shippingAddress.postalCode}
                </p>
              </section>

              <section className="bg-white border border-[var(--color-border)] rounded p-6 mb-6">
                <h2 className="font-bold mb-4">Ödeme Bilgileri</h2>
                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt className="text-gray-600">Ödeme Yöntemi</dt>
                    <dd className="font-bold mt-1">{paymentMethodLabel(order.paymentMethod)}</dd>
                  </div>
                  <div>
                    <dt className="text-gray-600">Ödeme Durumu</dt>
                    <dd className="font-bold mt-1">{isPaymentLoading ? 'Yükleniyor...' : paymentStatusLabel(payment?.status)}</dd>
                  </div>
                  {payment?.updatedAt && (
                    <div>
                      <dt className="text-gray-600">Son Güncelleme</dt>
                      <dd className="font-bold mt-1">{formatTRDate(payment.updatedAt)}</dd>
                    </div>
                  )}
                  {payment?.failureReason && (
                    <div>
                      <dt className="text-gray-600">Hata Nedeni</dt>
                      <dd className="font-bold mt-1">{payment.failureReason}</dd>
                    </div>
                  )}
                </dl>
              </section>

              {isCancellable(order.status) && (
                <button
                  type="button"
                  onClick={() => setShowCancelDialog(true)}
                  className="w-full border border-[#DC2626] text-[#DC2626] py-3 rounded font-bold hover:bg-red-50"
                >
                  Siparişi İptal Et
                </button>
              )}
            </>
          )}
        </div>
      </div>
      <CancelOrderDialog
        open={showCancelDialog}
        pending={cancelMutation.isPending}
        onConfirm={() => cancelMutation.mutate()}
        onCancel={() => setShowCancelDialog(false)}
      />
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-4 animate-pulse">
      <div className="h-4 bg-gray-200 rounded w-1/3" />
      <div className="h-32 bg-gray-200 rounded" />
      <div className="h-48 bg-gray-200 rounded" />
    </div>
  );
}
