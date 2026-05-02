import { apiFetch } from '../lib/apiClient';
import type {
  BackendProductDetailDto,
  BackendProductPage,
  BackendProductSummaryDto,
  Product,
  ProductPage,
  StockState,
} from '../lib/types';
import type { BackendListingParams } from '../lib/listingParams';

async function fetchStockQty(productId: string): Promise<number> {
  const stock = await apiFetch<StockState>(`/inventory/${encodeURIComponent(productId)}`);
  return stock.availableQty;
}

function normalizeProductSummary(dto: BackendProductSummaryDto): Product {
  return {
    id: dto.id,
    name: dto.nameTr,
    description: '',
    priceGross: dto.priceGross,
    kdvRate: 0,
    imageUrl: dto.firstImageUrl ?? '',
    categoryId: dto.categoryId ?? '',
    categoryLabel: dto.categoryName,
    stockQty: dto.stockQty ?? 0,
    createdAt: dto.createdAt ?? '',
  };
}

function normalizeProductDetail(dto: BackendProductDetailDto, stockQty: number): Product {
  return {
    id: dto.id,
    name: dto.nameTr,
    description: dto.descriptionTr,
    priceGross: dto.priceGross,
    kdvRate: dto.kdvRate,
    imageUrl: dto.imageUrls[0] ?? '',
    categoryId: dto.categoryId,
    categoryLabel: dto.categoryName,
    stockQty,
    createdAt: '',
  };
}

export function normalizeProductPage(page: BackendProductPage): ProductPage {
  return {
    content: page.content.map(normalizeProductSummary),
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    number: page.number,
    size: page.size,
  };
}

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
  return apiFetch<BackendProductPage>(`/products?${qs.toString()}`)
    .then(async page => {
      const normalized = normalizeProductPage(page);
      const content = await Promise.all(normalized.content.map(async (product, index) => {
        const dtoStockQty = page.content[index]?.stockQty;
        if (typeof dtoStockQty === 'number') return product;
        return { ...product, stockQty: await fetchStockQty(product.id) };
      }));
      return { ...normalized, content };
    });
}

export function fetchProductById(id: string): Promise<Product> {
  return apiFetch<BackendProductDetailDto>(`/products/${encodeURIComponent(id)}`)
    .then(async product => normalizeProductDetail(
      product,
      typeof product.stockQty === 'number' ? product.stockQty : await fetchStockQty(product.id),
    ));
}

export const productsQueryKey = (params: BackendListingParams, categoryId?: string) =>
  ['products', params, categoryId] as const;

export const productByIdQueryKey = (id: string) => ['product', id] as const;
