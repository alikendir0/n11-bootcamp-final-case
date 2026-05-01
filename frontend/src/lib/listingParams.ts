import type { CategorySlug } from './categories';

/** Visible URL params (Turkish keys) per D-10. */
export interface UiListingParams {
  sayfa?: number;          // 1-indexed (UI). Defaults to 1.
  siralama?: SiralamaKey;  // user-facing sort
  kategori?: CategorySlug; // category slug
  q?: string;              // search query (used on /arama)
}

export type SiralamaKey = 'tarih-yeni' | 'fiyat-artan' | 'fiyat-azalan';

/** Backend params consumed by Spring Data product-service. Page is 0-indexed. */
export interface BackendListingParams {
  page: number;            // 0-indexed
  size: number;            // fixed 20 (D-12)
  sort: BackendSort;
  q?: string;
  categoryFilter?: string; // slug — translated to UUID by productApi using categories map
}

export type BackendSort = 'created_at,desc' | 'price_gross,asc' | 'price_gross,desc';

export const PAGE_SIZE = 20;                          // D-12
const DEFAULT_SORT: BackendSort = 'created_at,desc'; // D-11

const SORT_UI_TO_BACKEND: Record<SiralamaKey, BackendSort> = {
  'tarih-yeni': 'created_at,desc',
  'fiyat-artan': 'price_gross,asc',
  'fiyat-azalan': 'price_gross,desc',
};

const SORT_BACKEND_TO_UI: Record<BackendSort, SiralamaKey> = {
  'created_at,desc': 'tarih-yeni',
  'price_gross,asc': 'fiyat-artan',
  'price_gross,desc': 'fiyat-azalan',
};

export function uiToBackend(ui: UiListingParams): BackendListingParams {
  const visiblePage = Math.max(1, Math.floor(Number(ui.sayfa) || 1));
  const page = Math.max(0, visiblePage - 1); // 1-indexed UI → 0-indexed backend
  const sort =
    ui.siralama && SORT_UI_TO_BACKEND[ui.siralama]
      ? SORT_UI_TO_BACKEND[ui.siralama]
      : DEFAULT_SORT;
  const out: BackendListingParams = { page, size: PAGE_SIZE, sort };
  if (ui.q) out.q = ui.q;
  if (ui.kategori) out.categoryFilter = ui.kategori;
  return out;
}

export function backendSortToUi(sort: BackendSort): SiralamaKey {
  return SORT_BACKEND_TO_UI[sort];
}

/**
 * Compute the visible page-number window for the pagination component (D-09).
 * Returns an array of page numbers (1-indexed) and 'ellipsis' markers.
 *
 * Examples:
 *   pageWindow(1, 1)      → [1]
 *   pageWindow(1, 3)      → [1, 2, 3]
 *   pageWindow(5, 47, 2)  → [1, 'ellipsis', 3, 4, 5, 6, 7, 'ellipsis', 47]
 *   pageWindow(46, 47, 2) → [1, 'ellipsis', 44, 45, 46, 47]
 */
export function pageWindow(
  current: number,
  total: number,
  radius = 2,
): Array<number | 'ellipsis'> {
  if (total <= 1) return [1];
  const out: Array<number | 'ellipsis'> = [];
  const left = Math.max(2, current - radius);
  const right = Math.min(total - 1, current + radius);

  out.push(1);
  if (left > 2) out.push('ellipsis');
  for (let i = left; i <= right; i++) out.push(i);
  if (right < total - 1) out.push('ellipsis');
  if (total > 1) out.push(total);
  return out;
}
