import { Link } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';
import { formatTRY } from '../../lib/format';
import type { Product } from '../../lib/types';

/** Build the slug-id segment per FE-07: kebab-cased name + product-id. */
function productSlug(p: Product, displayName: string): string {
  const slug = displayName
    .toLocaleLowerCase('tr-TR')
    .replace(/[^a-z0-9ğüşıöçĞÜŞİÖÇ]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return `${slug}-${p.id}`;
}

export function ProductCard({ product }: { product: Product }) {
  const outOfStock = product.stockQty <= 0;
  const displayName = product.name?.trim() || 'Ürün';
  return (
    <Link
      to={ROUTES.PRODUCT(productSlug(product, displayName))}
      aria-label={displayName}
      className="block bg-white border border-[var(--color-border)] rounded overflow-hidden hover:shadow-md transition-shadow"
    >
      <div className="relative aspect-[3/4] bg-gray-100">
        <img
          src={product.imageUrl}
          alt={displayName}
          loading="lazy"
          className="w-full h-full object-cover"
        />
        {outOfStock && (
          <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
            <span className="bg-white text-[#DC2626] font-bold text-sm px-3 py-1 rounded">
              Tükendi
            </span>
          </div>
        )}
      </div>
      <div className="p-3">
        <h3 className="text-sm line-clamp-2 min-h-[2.5em] mb-2">{displayName}</h3>
        <p className="text-xl font-bold">{formatTRY(product.priceGross)}</p>
      </div>
    </Link>
  );
}
