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

export interface BackendProductSummaryDto {
  id: string;
  nameTr: string;
  priceGross: number;
  firstImageUrl: string | null;
  categoryName: string;
  categoryId?: string;
  stockQty?: number;
  createdAt?: string;
}

export interface BackendProductPage {
  content: BackendProductSummaryDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface BackendProductDetailDto {
  id: string;
  nameTr: string;
  descriptionTr: string;
  priceGross: number;
  kdvRate: number;
  imageUrls: string[];
  categoryId: string;
  categoryName: string;
  stockQty?: number;
}

export interface StockState {
  productId: string;
  availableQty: number;
  stockState: string;
  stockStateLabel: string;
  displayQty: number;
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

export type OrderStatus =
  | 'PENDING'
  | 'STOCK_RESERVED'
  | 'PAID'
  | 'CONFIRMED'
  | 'STOCK_FAILED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';

export interface Order {
  id: string;
  userId: string;
  status: OrderStatus;
  totalAmount: number;
  currency: 'TRY';
  cancelReason?: string;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  paymentMethod?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentStatus {
  orderId: string;
  /** PENDING_INITIALIZATION = no payment row yet (202 from PaymentController). */
  status: 'PENDING_INITIALIZATION' | 'PENDING' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
  paymentPageUrl: string | null;
  failureReason?: string | null;
  updatedAt?: string | null;
}

// ── Chat / Assistant types (Phase 11) ──────────────────────────────────

export type ChatEventType = 'delta' | 'tool_call' | 'tool_result' | 'done' | 'error';

export interface ChatDeltaEvent {
  type: 'delta';
  text: string;
  conversationId: string;
}

export interface ChatToolCallEvent {
  type: 'tool_call';
  name: string;
  callId: string;
  argsJson: string;
}

export interface ChatToolResultEvent {
  type: 'tool_result';
  callId: string;
  toolName?: string;
  ok: boolean;
  summary: string;
  resultType?: 'products' | 'product' | 'cart' | 'order' | 'payment' | 'generic';
  data?: ChatToolResultData;
}

export interface ChatDoneEvent {
  type: 'done';
  conversationId: string;
  finalText: string;
}

export interface ChatErrorEvent {
  type: 'error';
  code: string;
  messageTr: string;
}

export type ChatStreamEvent =
  | ChatDeltaEvent
  | ChatToolCallEvent
  | ChatToolResultEvent
  | ChatDoneEvent
  | ChatErrorEvent;

export interface ChatTranscriptItem {
  id: string;
  role: 'user' | 'assistant' | 'tool';
  text?: string;
  events?: ChatStreamEvent[];
  ctaUrl?: string;
}

export interface ChatProductCardData {
  id: string;
  name: string;
  priceGross?: number;
  stockQty?: number;
  imageUrl?: string;
  categoryLabel?: string;
}

export interface ChatCartSummaryData {
  itemCount: number;
  totalAmount?: number;
}

export interface ChatOrderHandoffData {
  orderId?: string;
  status?: string;
  paymentPageUrl?: string;
}

export type ChatToolResultData =
  | { products: ChatProductCardData[] }
  | { product: ChatProductCardData }
  | { cart: ChatCartSummaryData }
  | { order: ChatOrderHandoffData }
  | { paymentPageUrl: string }
  | Record<string, unknown>;
