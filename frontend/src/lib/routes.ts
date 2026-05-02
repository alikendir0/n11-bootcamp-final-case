export const ROUTES = {
  HOME: '/',
  CATEGORY: (slug: string) => `/${slug}`,
  SEARCH: '/arama',
  PRODUCT: (slugAndId: string) => `/urun/${slugAndId}`,
  CART: '/sepetim',
  CHECKOUT_ADDRESS: '/odeme/adres',
  CHECKOUT_PAYMENT: '/odeme/odeme',
  CHECKOUT_RESULT: '/odeme/sonuc',
  LOGIN: '/giris-yap',
  REGISTER: '/uye-ol',
  ACCOUNT: '/hesabim',
  ORDERS: '/siparislerim',
  ORDER_DETAIL: (orderId: string) => `/siparislerim/${orderId}`,
  ADDRESSES: '/adreslerim',
} as const;

export const CATEGORY_SLUGS = [
  'elektronik',
  'moda',
  'ev-yasam',
  'anne-bebek',
  'kozmetik',
  'spor-outdoor',
  'supermarket',
  'kitap-muzik-film',
] as const;

export type CategorySlug = typeof CATEGORY_SLUGS[number];
