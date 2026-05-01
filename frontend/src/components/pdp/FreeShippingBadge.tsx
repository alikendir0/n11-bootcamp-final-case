import { qualifiesForFreeShipping } from '../../lib/freeShipping';

export function FreeShippingBadge({ priceGross }: { priceGross: number }) {
  if (!qualifiesForFreeShipping(priceGross)) return null;
  return (
    <span className="inline-flex items-center bg-[var(--color-badge-free-ship-bg)] text-white text-xs font-bold px-3 py-1 rounded">
      Kargo Bedava
    </span>
  );
}
