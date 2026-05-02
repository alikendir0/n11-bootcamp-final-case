import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { addToCart } from '../../api/cartApi';
import { useAuthStore } from '../../store/authStore';
import { ROUTES } from '../../lib/routes';
import { productUrlSegment } from '../../lib/productUrls';
import { formatTRY } from '../../lib/format';
import { cartQueryKey } from '../../hooks/useCart';
import type { ChatProductCardData } from '../../lib/types';

interface ChatProductCardProps {
  product: ChatProductCardData;
}

export function ChatProductCard({ product }: ChatProductCardProps) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const qc = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => addToCart({ productId: product.id, qty: 1 }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cartQueryKey });
      toast.success('Ürün sepete eklendi.');
    },
    onError: () => {
      toast.error('Ürün sepete eklenemedi.');
    },
  });

  const productUrl = ROUTES.PRODUCT(productUrlSegment({ id: product.id, name: product.name }));

  function handleAddToCart() {
    if (!isAuthenticated) {
      const redirectUrl = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `${ROUTES.LOGIN}?redirectUrl=${redirectUrl}`;
      return;
    }
    mutation.mutate();
  }

  return (
    <div className="bg-white border border-[#E5E7EB] rounded-lg p-3 space-y-2">
      <h4 className="text-sm font-bold line-clamp-2">{product.name}</h4>
      {typeof product.priceGross === 'number' && (
        <p className="text-sm font-bold">{formatTRY(product.priceGross)}</p>
      )}
      {typeof product.stockQty === 'number' && product.stockQty > 0 && (
        <p className="text-xs text-[#34A853]">Stokta</p>
      )}
      {typeof product.stockQty === 'number' && product.stockQty <= 0 && (
        <p className="text-xs text-[#DC2626]">Tükendi</p>
      )}
      <div className="flex gap-2 pt-1">
        <Link
          to={productUrl}
          className="flex-1 text-center text-xs bg-white border border-[#E5E7EB] text-[#1C1C1E] px-3 py-2 rounded font-medium"
        >
          Ürünü Gör
        </Link>
        <button
          type="button"
          onClick={handleAddToCart}
          disabled={mutation.isPending}
          className="flex-1 text-center text-xs bg-[#1C1C1E] text-white px-3 py-2 rounded font-medium disabled:opacity-50"
        >
          {mutation.isPending ? 'Ekleniyor...' : 'Sepete Ekle'}
        </button>
      </div>
    </div>
  );
}
