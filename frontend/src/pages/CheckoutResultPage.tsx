import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchOrder, orderQueryKey } from '../api/orderApi';
import { fetchPaymentForOrder, paymentForOrderQueryKey } from '../api/paymentApi';
import { cartQueryKey } from '../hooks/useCart';
import { useCheckoutStore } from '../store/checkoutStore';
import { ROUTES } from '../lib/routes';
import type { Cart, Order } from '../lib/types';
import { CheckoutStepper } from '../components/checkout/CheckoutStepper';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_MS = 30_000;

const TERMINAL_SUCCESS = ['CONFIRMED'] as const;
const TERMINAL_FAILURE = ['PAYMENT_FAILED', 'STOCK_FAILED', 'CANCELLED'] as const;

function isUuid(s: string | null): s is string {
  return (
    typeof s === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s)
  );
}

export default function CheckoutResultPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const resetCheckout = useCheckoutStore(s => s.reset);
  const orderId = params.get('orderId');
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    if (!isUuid(orderId)) {
      navigate(ROUTES.HOME, { replace: true });
    }
  }, [orderId, navigate]);

  useEffect(() => {
    const t = setTimeout(() => setTimedOut(true), MAX_POLL_MS);
    return () => clearTimeout(t);
  }, []);

  const { data: order, isLoading } = useQuery<Order>({
    queryKey: isUuid(orderId) ? orderQueryKey(orderId) : ['order', 'invalid'],
    queryFn: () => fetchOrder(orderId!),
    enabled: isUuid(orderId),
    refetchInterval: (q) => {
      const data = q.state.data as Order | undefined;
      if (!data) return POLL_INTERVAL_MS;
      if ((TERMINAL_SUCCESS as readonly string[]).includes(data.status)) return false;
      if ((TERMINAL_FAILURE as readonly string[]).includes(data.status)) return false;
      return POLL_INTERVAL_MS;
    },
    refetchIntervalInBackground: false,
  });

  const { data: payment } = useQuery({
    queryKey: isUuid(orderId) ? paymentForOrderQueryKey(orderId) : ['payment', 'invalid'],
    queryFn: () => fetchPaymentForOrder(orderId!),
    enabled: isUuid(orderId) && !timedOut,
    refetchInterval: (q) => {
      const data = q.state.data;
      if (data?.status === 'FAILED' || data?.status === 'TIMED_OUT' || data?.status === 'COMPLETED') return false;
      if (data?.status === 'PENDING' && data.paymentPageUrl) return false;
      return 1000;
    },
    refetchIntervalInBackground: false,
  });

  useEffect(() => {
    if (payment?.status === 'PENDING' && payment.paymentPageUrl) {
      window.location.assign(payment.paymentPageUrl);
    }
  }, [payment?.paymentPageUrl, payment?.status]);

  // On CONFIRMED: invalidate cart cache + reset checkout state
  useEffect(() => {
    if (order?.status === 'CONFIRMED') {
      qc.setQueryData<Cart>(cartQueryKey, { userId: '', items: [], updatedAt: new Date().toISOString() });
      qc.invalidateQueries({ queryKey: cartQueryKey });
      resetCheckout();
    }
  }, [order?.status, qc, resetCheckout]);

  if (!isUuid(orderId)) return null;

  const isSuccess = order && (TERMINAL_SUCCESS as readonly string[]).includes(order.status);
  const isFailure = order && (TERMINAL_FAILURE as readonly string[]).includes(order.status);
  const isStillProcessing = !isSuccess && !isFailure;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <CheckoutStepper active="onay" />
      <div className="bg-white border border-[var(--color-border)] rounded p-8 text-center">
        {isLoading || (isStillProcessing && !timedOut) ? (
          <ProcessingCard />
        ) : isSuccess ? (
          <SuccessCard orderId={orderId} />
        ) : isFailure ? (
          <FailureCard />
        ) : (
          /* timed out, still non-terminal */
          <TimeoutCard orderId={orderId} />
        )}
      </div>
    </div>
  );
}

function ProcessingCard() {
  return (
    <div>
      <div
        className="mx-auto w-12 h-12 rounded-full border-4 border-gray-200 border-t-[#1C1C1E] animate-spin"
        aria-hidden
      />
      <h1 className="text-xl font-bold mt-6 mb-2">Ödemeniz işleniyor</h1>
      <p className="text-gray-700">Ödemeniz işleniyor, lütfen bekleyiniz...</p>
    </div>
  );
}

function SuccessCard({ orderId }: { orderId: string }) {
  return (
    <div>
      <div
        className="mx-auto w-12 h-12 rounded-full bg-[#34A853] flex items-center justify-center text-white text-2xl font-bold"
        aria-hidden
      >
        ✓
      </div>
      <h1 className="text-xl font-bold mt-6 mb-2">Siparişiniz Alındı</h1>
      <p className="text-gray-700 mb-2">
        Sipariş numaranız: <span className="font-bold">{orderId}</span>
      </p>
      <p className="text-gray-700 mb-6">Teşekkür ederiz!</p>
      <Link
        to={ROUTES.ORDER_DETAIL(orderId)}
        className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
      >
        Siparişimi Gör
      </Link>
    </div>
  );
}

function FailureCard() {
  return (
    <div>
      <div
        className="mx-auto w-12 h-12 rounded-full bg-[#DC2626] flex items-center justify-center text-white text-2xl font-bold"
        aria-hidden
      >
        !
      </div>
      <h1 className="text-xl font-bold mt-6 mb-2">Ödemeniz Alınamadı</h1>
      <p className="text-gray-700 mb-6">
        Lütfen tekrar deneyiniz veya farklı bir kart kullanınız.
      </p>
      <Link to={ROUTES.CART} className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold">
        Tekrar Dene
      </Link>
    </div>
  );
}

function TimeoutCard({ orderId }: { orderId: string }) {
  return (
    <div>
      <h1 className="text-xl font-bold mt-2 mb-2">İşleminiz Hâlâ Kontrol Ediliyor</h1>
      <p className="text-gray-700 mb-6">
        Siparişinizin durumunu aşağıdan takip edebilirsiniz.
      </p>
      <Link
        to={ROUTES.ORDER_DETAIL(orderId)}
        className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
      >
        Siparişimi Görüntüle
      </Link>
    </div>
  );
}
