import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useCart, cartQueryKey } from '../hooks/useCart';
import { addToCart, updateCartItem, removeCartItem } from '../api/cartApi';
import { CartLineItemRow } from '../components/cart/CartLineItemRow';
import { CartSummary } from '../components/cart/CartSummary';
import { EmptyCart } from '../components/cart/EmptyCart';
import { ApiError } from '../lib/apiClient';
import type { Cart, CartLineItem } from '../lib/types';

export default function CartPage() {
  const { data: cart, isLoading, isError, refetch } = useCart();
  const qc = useQueryClient();

  // Optimistic update for qty change
  const qtyMutation = useMutation({
    mutationFn: ({ productId, qty }: { productId: string; qty: number }) =>
      updateCartItem(productId, qty),
    onMutate: async ({ productId, qty }) => {
      await qc.cancelQueries({ queryKey: cartQueryKey });
      const previous = qc.getQueryData<Cart>(cartQueryKey);
      if (previous) {
        qc.setQueryData<Cart>(cartQueryKey, {
          ...previous,
          items: previous.items.map(i => i.productId === productId ? { ...i, qty } : i),
        });
      }
      return { previous };
    },
    onError: (err, _, context) => {
      if (context?.previous) qc.setQueryData(cartQueryKey, context.previous);
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: cartQueryKey });
    },
  });

  // Optimistic remove with undo
  const removeMutation = useMutation({
    mutationFn: (productId: string) => removeCartItem(productId),
    onMutate: async (productId) => {
      await qc.cancelQueries({ queryKey: cartQueryKey });
      const previous = qc.getQueryData<Cart>(cartQueryKey);
      const removed = previous?.items.find(i => i.productId === productId);
      if (previous) {
        qc.setQueryData<Cart>(cartQueryKey, {
          ...previous,
          items: previous.items.filter(i => i.productId !== productId),
        });
      }
      return { previous, removed };
    },
    onSuccess: (_, _productId, context) => {
      const removed = context?.removed;
      if (!removed) {
        toast.success('Ürün sepetten çıkarıldı.');
        return;
      }
      toast('Ürün sepetten çıkarıldı.', {
        action: {
          label: 'Geri al',
          onClick: () => undoRemove(removed),
        },
        duration: 5000,
      });
    },
    onError: (err, _, context) => {
      if (context?.previous) qc.setQueryData(cartQueryKey, context.previous);
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: cartQueryKey });
    },
  });

  async function undoRemove(item: CartLineItem) {
    try {
      await addToCart({ productId: item.productId, qty: item.qty });
      qc.invalidateQueries({ queryKey: cartQueryKey });
      toast.success('Ürün geri eklendi.');
    } catch {
      toast.error('Ürün geri eklenemedi.');
    }
  }

  function handleQtyChange(productId: string, qty: number) {
    if (qty < 1) {
      removeMutation.mutate(productId);
      return;
    }
    qtyMutation.mutate({ productId, qty });
  }

  if (isLoading) return <CartSkeleton />;
  if (isError) {
    return (
      <div className="mx-auto max-w-7xl my-12 px-4">
        <div className="bg-white border border-[#DC2626] rounded p-6 text-center">
          <p className="mb-4">Sepet yüklenirken bir hata oluştu.</p>
          <button
            onClick={() => refetch()}
            type="button"
            className="bg-[#1C1C1E] text-white px-6 py-2 rounded font-bold"
          >
            Tekrar Dene
          </button>
        </div>
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-12">
        <EmptyCart />
      </div>
    );
  }

  const isMutating = qtyMutation.isPending || removeMutation.isPending;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">Sepetim ({cart.items.length} ürün)</h1>
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-8">
        <section className="bg-white border border-[var(--color-border)] rounded p-4">
          {cart.items.map(item => (
            <CartLineItemRow
              key={item.productId}
              item={item}
              mutating={isMutating}
              onQtyChange={handleQtyChange}
              onRemove={(pid) => removeMutation.mutate(pid)}
            />
          ))}
        </section>
        <CartSummary items={cart.items} />
      </div>
    </div>
  );
}

function CartSkeleton() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="h-8 w-40 bg-gray-200 rounded animate-pulse mb-6" />
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-8">
        <div className="bg-white border border-[var(--color-border)] rounded p-4 space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex gap-4 items-start py-4">
              <div className="w-16 h-16 bg-gray-200 animate-pulse rounded" />
              <div className="flex-1 space-y-2">
                <div className="h-4 bg-gray-200 animate-pulse rounded w-3/4" />
                <div className="h-3 bg-gray-200 animate-pulse rounded w-1/3" />
                <div className="h-9 bg-gray-200 animate-pulse rounded w-32" />
              </div>
              <div className="h-5 w-20 bg-gray-200 animate-pulse rounded" />
            </div>
          ))}
        </div>
        <div className="bg-white border border-[var(--color-border)] rounded p-6">
          <div className="h-5 bg-gray-200 animate-pulse rounded w-32 mb-4" />
          <div className="space-y-3">
            <div className="h-4 bg-gray-200 animate-pulse rounded" />
            <div className="h-4 bg-gray-200 animate-pulse rounded" />
            <div className="h-4 bg-gray-200 animate-pulse rounded" />
          </div>
          <div className="h-12 bg-gray-200 animate-pulse rounded mt-6" />
        </div>
      </div>
    </div>
  );
}
