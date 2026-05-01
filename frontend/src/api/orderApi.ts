import { apiFetch } from '../lib/apiClient';
import type { Order } from '../lib/types';

export interface CreateOrderRequest {
  addressId: string;
  paymentMethod: 'CREDIT_CARD';
}

/** Minimal response from POST /orders (OrderResponse DTO). */
export interface OrderCreatedResponse {
  orderId: string;
  status: Order['status'];
}

/** POSTs /orders with Idempotency-Key header per Phase 5 D-05. */
export function createOrder(req: CreateOrderRequest, idempotencyKey: string): Promise<OrderCreatedResponse> {
  return apiFetch<OrderCreatedResponse>('/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(req),
  });
}

export function fetchOrder(orderId: string): Promise<Order> {
  return apiFetch<Order>(`/orders/${encodeURIComponent(orderId)}`);
}

export function fetchMyOrders(): Promise<Order[]> {
  return apiFetch<Order[]>('/orders');
}

export function cancelOrder(orderId: string): Promise<void> {
  return apiFetch<void>(`/orders/${encodeURIComponent(orderId)}/cancel`, {
    method: 'POST',
  });
}

export const orderQueryKey = (orderId: string) => ['order', orderId] as const;
export const myOrdersQueryKey = ['orders', 'mine'] as const;
