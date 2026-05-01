import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { createOrder } from '../api/orderApi';
import { fetchPaymentForOrder } from '../api/paymentApi';
import { useCheckoutStore } from '../store/checkoutStore';
import { ROUTES } from '../lib/routes';
import { ApiError } from '../lib/apiClient';
import { CheckoutStepper } from '../components/checkout/CheckoutStepper';

const PAYMENT_LINK_RETRY_LIMIT = 5;
const PAYMENT_LINK_RETRY_DELAY_MS = 1000;

/** Polls GET /payments/{orderId} up to 5 times with 1s delay until paymentPageUrl is ready.
 *  Handles Phase 6 D-08 saga lag: payment-service initializes asynchronously after stock.reserved. */
async function pollForPaymentPageUrl(orderId: string): Promise<string | null> {
  for (let i = 0; i < PAYMENT_LINK_RETRY_LIMIT; i++) {
    const status = await fetchPaymentForOrder(orderId);
    if (status.paymentPageUrl) return status.paymentPageUrl;
    if (status.status === 'FAILED' || status.status === 'TIMED_OUT') return null;
    if (status.status === 'COMPLETED') return null;
    await new Promise(r => setTimeout(r, PAYMENT_LINK_RETRY_DELAY_MS));
  }
  return null;
}

export default function CheckoutPaymentPage() {
  const navigate = useNavigate();
  const addressId = useCheckoutStore(s => s.addressId);
  const ensureIdempotencyKey = useCheckoutStore(s => s.ensureIdempotencyKey);
  const setPaymentMethod = useCheckoutStore(s => s.setPaymentMethod);
  const [submitting, setSubmitting] = useState(false);

  // D-13 stepper guard: if no address selected, redirect back to step 1
  useEffect(() => {
    if (!addressId) navigate(ROUTES.CHECKOUT_ADDRESS, { replace: true });
  }, [addressId, navigate]);

  async function handlePlaceOrder() {
    if (!addressId || submitting) return;
    setSubmitting(true);
    try {
      setPaymentMethod('CREDIT_CARD');
      const idempotencyKey = ensureIdempotencyKey();
      const order = await createOrder({ addressId, paymentMethod: 'CREDIT_CARD' }, idempotencyKey);
      const paymentPageUrl = await pollForPaymentPageUrl(order.orderId);
      if (paymentPageUrl) {
        window.location.assign(paymentPageUrl);
        return; // browser navigates away; component unmounts
      }
      // No paymentPageUrl after retries — let the result page keep polling
      navigate(`${ROUTES.CHECKOUT_RESULT}?orderId=${encodeURIComponent(order.orderId)}`);
    } catch (err) {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Siparişiniz oluşturulamadı. Lütfen tekrar deneyiniz.');
      setSubmitting(false);
    }
  }

  if (!addressId) return null; // navigating away

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <CheckoutStepper active="odeme" />
      <h1 className="text-xl font-bold mb-6">Ödeme Yöntemi</h1>

      <fieldset className="bg-white border border-[var(--color-border)] rounded p-6 space-y-4">
        <legend className="sr-only">Ödeme yöntemi seçimi</legend>
        <label className="flex items-start gap-3 cursor-pointer">
          <input type="radio" name="payment" value="CREDIT_CARD" defaultChecked className="mt-1" />
          <div>
            <p className="font-bold text-sm">Kredi Kartı</p>
            <p className="text-xs text-gray-600 mt-1">
              Iyzico güvenli ödeme sayfasına yönlendirileceksiniz. 3D Secure desteklenmektedir.
            </p>
          </div>
        </label>
        <label className="flex items-start gap-3 opacity-60 cursor-not-allowed">
          <input type="radio" name="payment" disabled className="mt-1" />
          <div>
            <p className="font-bold text-sm">
              Kapıda Ödeme{' '}
              <span className="ml-2 text-xs text-gray-500">
                Yakında
              </span>
            </p>
            <p className="text-xs text-gray-600 mt-1">Bu ödeme yöntemi henüz aktif değil.</p>
          </div>
        </label>
      </fieldset>

      <button
        type="button"
        onClick={handlePlaceOrder}
        disabled={submitting}
        className="mt-6 w-full bg-[#1C1C1E] text-white py-3 rounded font-bold disabled:opacity-50"
      >
        {submitting ? 'Sipariş oluşturuluyor...' : 'Sipariş Ver'}
      </button>
    </div>
  );
}
