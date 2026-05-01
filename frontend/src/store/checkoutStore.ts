import { create } from 'zustand';

interface CheckoutState {
  addressId: string | null;
  paymentMethod: 'CREDIT_CARD' | null;
  idempotencyKey: string | null;
  setAddress: (id: string) => void;
  setPaymentMethod: (m: 'CREDIT_CARD') => void;
  ensureIdempotencyKey: () => string;
  reset: () => void;
}

export const useCheckoutStore = create<CheckoutState>((set, get) => ({
  addressId: null,
  paymentMethod: null,
  idempotencyKey: null,
  setAddress: (id) => set({ addressId: id }),
  setPaymentMethod: (m) => set({ paymentMethod: m }),
  ensureIdempotencyKey: () => {
    const existing = get().idempotencyKey;
    if (existing) return existing;
    const fresh = crypto.randomUUID();
    set({ idempotencyKey: fresh });
    return fresh;
  },
  reset: () => set({ addressId: null, paymentMethod: null, idempotencyKey: null }),
}));
