// Mirrors backend DTOs from Phase 3/4/5/6 contracts

export interface User {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface Address {
  id: string;
  title: string;
  recipientName: string;
  phone: string;
  il: string;
  ilce: string;
  mahalle: string;
  streetLine: string;
  postalCode: string;
  isDefault: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string;
  priceGross: number;            // KDV-inclusive
  kdvRate: number;
  imageUrl: string;
  categoryId: string;
  categoryLabel: string;
  stockQty: number;
  createdAt: string;             // ISO-8601
}

export interface ProductPage {
  content: Product[];
  totalElements: number;
  totalPages: number;
  number: number;                // 0-based Spring Data page index
  size: number;
}

export interface CartLineItem {
  productId: string;
  nameSnapshot: string;
  imageUrlSnapshot: string;
  unitPriceSnapshot: number;
  qty: number;
}

export interface Cart {
  userId: string;
  items: CartLineItem[];
  updatedAt: string;
}

export interface OrderItem {
  productId: string;
  nameSnapshot: string;
  qty: number;
  unitPrice: number;
}

export interface ShippingAddress {
  recipientName: string;
  phone: string;
  il: string;
  ilce: string;
  mahalle: string;
  streetLine: string;
  postalCode: string;
  title: string;
}

export interface Order {
  id: string;
  userId: string;
  status:
    | 'PENDING'
    | 'STOCK_RESERVED'
    | 'PAID'
    | 'CONFIRMED'
    | 'STOCK_FAILED'
    | 'PAYMENT_FAILED'
    | 'CANCELLED';
  totalAmount: number;
  currency: 'TRY';
  cancelReason?: string;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  paymentMethod: string;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentStatus {
  orderId: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
  paymentPageUrl: string | null;
}
