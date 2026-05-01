const RAW = import.meta.env.VITE_FREE_SHIPPING_THRESHOLD;
const PARSED = RAW !== undefined ? Number(RAW) : NaN;
export const FREE_SHIPPING_THRESHOLD = Number.isFinite(PARSED) ? PARSED : 500;

export function qualifiesForFreeShipping(priceGross: number): boolean {
  return priceGross >= FREE_SHIPPING_THRESHOLD;
}
