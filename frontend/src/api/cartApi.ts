import { apiFetch } from '../lib/apiClient';
import type { Cart } from '../lib/types';

export function fetchCart(): Promise<Cart> {
  return apiFetch<Cart>('/cart');
}

export interface AddToCartRequest {
  productId: string;
  qty: number;
}

export function addToCart(req: AddToCartRequest): Promise<void> {
  return apiFetch<void>('/cart/items', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}
