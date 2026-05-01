import { apiFetch } from '../lib/apiClient';
import type { Cart } from '../lib/types';

export function fetchCart(): Promise<Cart> {
  return apiFetch<Cart>('/cart');
}
