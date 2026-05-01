import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { fetchProductById, productByIdQueryKey } from '../api/productApi';
import { addToCart } from '../api/cartApi';
import { cartQueryKey } from '../hooks/useCart';
import { useAuthStore } from '../store/authStore';
import { ROUTES } from '../lib/routes';
import { formatTRY } from '../lib/format';
import { ApiError } from '../lib/apiClient';
import { Breadcrumbs } from '../components/listing/Breadcrumbs';
import { ImageGallery } from '../components/pdp/ImageGallery';
import { StockBadge } from '../components/pdp/StockBadge';
import { FreeShippingBadge } from '../components/pdp/FreeShippingBadge';
import { TaksitTable } from '../components/pdp/TaksitTable';
import { PdpTabs } from '../components/pdp/PdpTabs';
import NotFoundPage from './NotFoundPage';

/** Extract product UUID from /urun/<slug>-<UUID>. UUID v4 is the last 36 chars (with hyphens). */
function extractUuid(slugAndId: string | undefined): string | null {
  if (!slugAndId || slugAndId.length < 36) return null;
  const uuid = slugAndId.slice(-36);
  // Loose validation — UUID v4 has 4 hyphens at fixed positions
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid)) return null;
  return uuid;
}

export default function ProductDetailPage() {
  const { slugAndId } = useParams<{ slugAndId: string }>();
  const productId = extractUuid(slugAndId);
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const navigate = useNavigate();
  const location = useLocation();
  const qc = useQueryClient();

  const { data: product, isLoading, isError, error } = useQuery({
    queryKey: productId ? productByIdQueryKey(productId) : ['product', 'invalid'],
    queryFn: () => fetchProductById(productId!),
    enabled: productId !== null,
  });

  const addMutation = useMutation({
    mutationFn: () => addToCart({ productId: productId!, qty: 1 }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cartQueryKey });
      toast.success('Ürün sepete eklendi.');
    },
    onError: (err) => {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
    },
  });

  if (productId === null) return <NotFoundPage />;
  if (isError && error instanceof ApiError && error.status === 404) return <NotFoundPage />;

  if (isLoading || !product) {
    return <PdpSkeleton />;
  }

  const outOfStock = product.stockQty <= 0;

  function handleAddToCart() {
    if (!isAuthenticated) {
      const redirectUrl = encodeURIComponent(location.pathname);
      navigate(`${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`);
      return;
    }
    addMutation.mutate();
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <Breadcrumbs items={[
        { label: 'Ana Sayfa', to: '/' },
        { label: product.categoryLabel ?? 'Kategori' },
        { label: product.name },
      ]} />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <ImageGallery primaryUrl={product.imageUrl} altText={product.name} />

        <div>
          <h1 className="text-xl font-bold mb-4">{product.name}</h1>
          <p className="text-2xl font-bold mb-2">{formatTRY(product.priceGross)}</p>
          <p className="text-xs text-gray-600 mb-3">KDV Dahil</p>
          <div className="mb-4">
            <FreeShippingBadge priceGross={product.priceGross} />
          </div>
          <TaksitTable priceGross={product.priceGross} />
          <div className="mt-6">
            <StockBadge qty={product.stockQty} />
          </div>
          <button
            type="button"
            disabled={outOfStock || addMutation.isPending}
            onClick={handleAddToCart}
            style={{ fontFamily: 'var(--font-cta)' }}
            className="mt-6 w-full bg-[#1C1C1E] text-white py-4 rounded font-bold disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {outOfStock ? 'Tükendi' : addMutation.isPending ? 'Ekleniyor...' : 'Sepete Ekle'}
          </button>
          <p className="mt-4 text-xs text-gray-600">
            Satıcı: <span className="font-bold">n11 Pazaryeri</span>
          </p>
        </div>
      </div>

      <PdpTabs description={product.description} />
    </div>
  );
}

function PdpSkeleton() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="aspect-square bg-gray-200 rounded animate-pulse" />
        <div>
          <div className="h-6 bg-gray-200 rounded animate-pulse mb-4 w-3/4" />
          <div className="h-6 bg-gray-200 rounded animate-pulse mb-3 w-1/2" />
          <div className="h-4 bg-gray-200 rounded animate-pulse mb-2 w-full" />
          <div className="h-4 bg-gray-200 rounded animate-pulse mb-6 w-5/6" />
          <div className="h-12 bg-gray-200 rounded animate-pulse w-full" />
        </div>
      </div>
    </div>
  );
}
