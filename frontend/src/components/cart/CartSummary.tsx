import { useNavigate } from 'react-router-dom';
import type { CartLineItem } from '../../lib/types';
import { formatTRY } from '../../lib/format';
import { qualifiesForFreeShipping, FREE_SHIPPING_THRESHOLD } from '../../lib/freeShipping';
import { useAuthStore } from '../../store/authStore';
import { ROUTES } from '../../lib/routes';

const SHIPPING_FEE = 29.90;  // planner-discretion stub; only shown when subtotal < FREE_SHIPPING_THRESHOLD

export function CartSummary({ items }: { items: CartLineItem[] }) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const navigate = useNavigate();

  const subtotal = items.reduce((sum, i) => sum + i.unitPriceSnapshot * i.qty, 0);
  const isFreeShipping = qualifiesForFreeShipping(subtotal);
  const shipping = isFreeShipping ? 0 : SHIPPING_FEE;
  const total = subtotal + shipping;

  function handleCheckout() {
    if (!isAuthenticated) {
      const redirectUrl = encodeURIComponent(ROUTES.CHECKOUT_ADDRESS);
      navigate(`${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`);
      return;
    }
    navigate(ROUTES.CHECKOUT_ADDRESS);
  }

  return (
    <aside className="bg-white border border-[var(--color-border)] rounded p-6 sticky top-20" aria-labelledby="summary-heading">
      <h2 id="summary-heading" className="text-base font-bold mb-4">Sipariş Özeti</h2>
      <dl className="space-y-3 text-sm">
        <div className="flex justify-between">
          <dt>Ara Toplam</dt>
          <dd className="font-bold">{formatTRY(subtotal)}</dd>
        </div>
        <p className="text-xs text-gray-600">KDV Dahil</p>
        <div className="flex justify-between">
          <dt>Kargo</dt>
          <dd className={isFreeShipping ? 'font-bold text-[#34A853]' : 'font-bold'}>
            {isFreeShipping ? 'Kargo Bedava' : formatTRY(shipping)}
          </dd>
        </div>
        {!isFreeShipping && (
          <p className="text-xs text-gray-600">
            {formatTRY(FREE_SHIPPING_THRESHOLD - subtotal)} daha eklerseniz kargo bedava!
          </p>
        )}
        <div className="border-t border-[var(--color-border)] pt-3 flex justify-between text-base">
          <dt className="font-bold">Toplam</dt>
          <dd className="font-bold">{formatTRY(total)}</dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={handleCheckout}
        disabled={items.length === 0}
        className="mt-6 w-full bg-[#1C1C1E] text-white py-3 rounded font-bold disabled:opacity-50"
      >
        Siparişi Tamamla
      </button>
    </aside>
  );
}
