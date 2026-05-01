import { apiFetch } from '../lib/apiClient';
import type { PaymentStatus } from '../lib/types';

/** Phase 6 PaymentController GET /payments/{orderId} returns paymentPageUrl. */
export function fetchPaymentForOrder(orderId: string): Promise<PaymentStatus> {
  return apiFetch<PaymentStatus>(`/payments/${encodeURIComponent(orderId)}`);
}

export const paymentForOrderQueryKey = (orderId: string) =>
  ['payment', 'by-order', orderId] as const;
