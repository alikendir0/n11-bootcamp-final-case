import { useQuery } from '@tanstack/react-query';
import { fetchCart } from '../api/cartApi';
import { useAuthStore } from '../store/authStore';
import type { Cart } from '../lib/types';

export const cartQueryKey = ['cart'] as const;

export function useCart() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  return useQuery<Cart>({
    queryKey: cartQueryKey,
    queryFn: fetchCart,
    enabled: isAuthenticated,  // anonymous users have no cart per Phase 5 D-11; show empty badge
    placeholderData: { userId: '', items: [], updatedAt: '' } as Cart,
  });
}

export function useCartItemCount(): number {
  const { data } = useCart();
  return data?.items.reduce((sum, item) => sum + item.qty, 0) ?? 0;
}
