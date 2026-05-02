import type { Product } from './types';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SEEDED_ID_RE = /^00000000-0000-4000-8000-(\d{12})$/i;

export function productNameSlug(name: string): string {
  return name
    .toLocaleLowerCase('tr-TR')
    .replace(/[^a-z0-9ğüşıöçĞÜŞİÖÇ]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60) || 'urun';
}

export function productUrlSegment(product: Pick<Product, 'id' | 'name'>): string {
  const slug = productNameSlug(product.name?.trim() || 'Ürün');
  const seeded = product.id.match(SEEDED_ID_RE);
  if (!seeded) return `${slug}-${product.id}`;

  const shortId = String(Number(seeded[1]));
  return `${slug}-${shortId}`;
}

export function extractProductIdFromUrlSegment(segment: string | undefined): string | null {
  if (!segment) return null;

  const fullUuid = segment.slice(-36);
  if (UUID_RE.test(fullUuid)) return fullUuid;

  const numericSuffix = segment.match(/-(\d+)$/)?.[1];
  if (!numericSuffix) return null;

  return `00000000-0000-4000-8000-${numericSuffix.padStart(12, '0')}`;
}
