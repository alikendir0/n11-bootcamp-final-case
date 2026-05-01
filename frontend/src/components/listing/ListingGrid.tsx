import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchProducts, productsQueryKey } from '../../api/productApi';
import { fetchCategories, categoriesQueryKey } from '../../api/categoryApi';
import {
  uiToBackend,
  type SiralamaKey,
  type UiListingParams,
} from '../../lib/listingParams';
import type { CategorySlug } from '../../lib/categories';
import { ProductCard } from './ProductCard';
import { Pagination } from './Pagination';
import { SortControl } from './SortControl';
import { SkeletonCard } from '../feedback/SkeletonCard';

interface Props {
  categorySlug?: CategorySlug;
  query?: string;
}

const VALID_SORTS: SiralamaKey[] = ['tarih-yeni', 'fiyat-artan', 'fiyat-azalan'];

export function ListingGrid({ categorySlug, query }: Props) {
  const [params, setParams] = useSearchParams();

  const sayfaRaw = params.get('sayfa');
  const sayfa = sayfaRaw ? Math.max(1, Number(sayfaRaw)) : 1;
  const siralamaRaw = params.get('siralama') as SiralamaKey | null;
  const siralama: SiralamaKey =
    siralamaRaw && (VALID_SORTS as readonly string[]).includes(siralamaRaw)
      ? siralamaRaw
      : 'tarih-yeni';

  const ui: UiListingParams = { sayfa, siralama };
  if (categorySlug) ui.kategori = categorySlug;
  if (query) ui.q = query;

  const backend = uiToBackend(ui);

  // Fetch categories to build slug → UUID map (backend requires UUID categoryId)
  const { data: categories } = useQuery({
    queryKey: categoriesQueryKey,
    queryFn: fetchCategories,
    staleTime: Infinity,
  });

  const slugToUuid: Record<string, string> = {};
  if (categories) {
    for (const cat of categories) {
      slugToUuid[cat.slug] = cat.id;
    }
  }

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: productsQueryKey(backend, categorySlug ? (slugToUuid[categorySlug] ?? categorySlug) : undefined),
    queryFn: () => fetchProducts(backend, slugToUuid),
    enabled: !categorySlug || !!categories, // wait for categories before fetching by slug
  });

  function setUi(next: UiListingParams) {
    const merged = new URLSearchParams(params);
    if (next.sayfa !== undefined) merged.set('sayfa', String(next.sayfa));
    if (next.siralama) merged.set('siralama', next.siralama);
    setParams(merged, { replace: false });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  if (isError) {
    return (
      <div className="border border-[#DC2626] rounded p-6 text-center bg-white">
        <p className="mb-4">Ürünler yüklenirken bir hata oluştu.</p>
        <button
          type="button"
          onClick={() => refetch()}
          className="bg-[#1C1C1E] text-white px-6 py-2 rounded font-bold"
        >
          Tekrar Dene
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-4">
        <p className="text-sm text-gray-600">
          {data ? `${data.totalElements} ürün bulundu` : 'Yükleniyor...'}
        </p>
        <SortControl value={siralama} onChange={s => setUi({ siralama: s, sayfa: 1 })} />
      </div>
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => <SkeletonCard key={i} />)}
        </div>
      ) : data && data.content.length > 0 ? (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {data.content.map(p => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
          <Pagination
            currentPage={data.number + 1} // 0-indexed → 1-indexed for display (D-10)
            totalPages={data.totalPages}
            onPageChange={p => setUi({ sayfa: p })}
          />
        </>
      ) : (
        <div className="bg-white rounded border border-[var(--color-border)] p-12 text-center">
          <h2 className="text-xl font-bold mb-2">
            {query
              ? 'Aramanız için sonuç bulunamadı.'
              : 'Bu kategoride ürün bulunamadı.'}
          </h2>
          <a
            href="/elektronik"
            className="inline-block mt-4 bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
          >
            Diğer kategorilere göz at
          </a>
        </div>
      )}
    </div>
  );
}
