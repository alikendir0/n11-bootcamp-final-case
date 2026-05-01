import { apiFetch } from '../lib/apiClient';
import type { Product, ProductPage } from '../lib/types';
import type { BackendListingParams } from '../lib/listingParams';

/**
 * Backend product-service accepts ?categoryId=<UUID> (not slug).
 * The ListingGrid resolves slug → UUID via the categories query and passes
 * resolvedCategoryId here.
 *
 * fetchProducts: slug-aware variant used by ListingGrid (passes categoryId UUID)
 * fetchProductsRaw: accepts explicit categoryId UUID (already resolved)
 */
export function fetchProducts(
  params: BackendListingParams,
  slugToUuid?: Record<string, string>,
): Promise<ProductPage> {
  const qs = new URLSearchParams();
  qs.set('page', String(params.page));
  qs.set('size', String(params.size));
  qs.set('sort', params.sort);
  if (params.q) qs.set('q', params.q);
  if (params.categoryFilter) {
    // Resolve slug to UUID when map is provided; otherwise pass slug verbatim
    // (backend will return empty results for unknown UUIDs — fail-safe).
    const resolvedId = slugToUuid
      ? (slugToUuid[params.categoryFilter] ?? params.categoryFilter)
      : params.categoryFilter;
    qs.set('categoryId', resolvedId);
  }
  return apiFetch<ProductPage>(`/products?${qs.toString()}`);
}

export function fetchProductById(id: string): Promise<Product> {
  return apiFetch<Product>(`/products/${encodeURIComponent(id)}`);
}

export const productsQueryKey = (params: BackendListingParams, categoryId?: string) =>
  ['products', params, categoryId] as const;

export const productByIdQueryKey = (id: string) => ['product', id] as const;
