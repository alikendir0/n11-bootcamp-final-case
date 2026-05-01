export interface TaksitRow {
  installments: number;
  monthly: number;
}

/** UI-SPEC PDP block: 1, 2, 3, 6, 9, 12 installments. */
export const TAKSIT_TIERS: readonly number[] = [1, 2, 3, 6, 9, 12];

/** LOC-03: monthly = Math.ceil(price / n) — keeps the user from undershooting the principal. */
export function computeTaksit(priceGross: number): TaksitRow[] {
  return TAKSIT_TIERS.map(n => ({
    installments: n,
    monthly: Math.ceil(priceGross / n),
  }));
}
