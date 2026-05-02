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

export const CATEGORY_LABELS: Record<CategorySlug, string> = {
  'elektronik': 'Elektronik',
  'moda': 'Moda',
  'ev-yasam': 'Ev & Yaşam',
  'anne-bebek': 'Anne & Bebek',
  'kozmetik': 'Kozmetik',
  'spor-outdoor': 'Spor & Outdoor',
  'supermarket': 'Süpermarket',
  'kitap-muzik-film': 'Kitap, Müzik, Film, Oyun',
};

export const LEGACY_CATEGORY_SLUGS: Record<string, CategorySlug> = {
  'kitap-muzik-film-oyun': 'kitap-muzik-film',
};

export function isCategorySlug(slug: string): slug is CategorySlug {
  return (CATEGORY_SLUGS as readonly string[]).includes(slug);
}
