import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import type { CartLineItem } from '../../lib/types';
import { ROUTES } from '../../lib/routes';
import { formatTRY } from '../../lib/format';
import { productUrlSegment } from '../../lib/productUrls';
import { QtyStepper } from './QtyStepper';

interface Props {
  item: CartLineItem;
  mutating: boolean;
  onQtyChange: (productId: string, qty: number) => void;
  onRemove: (productId: string) => void;
}

export function CartLineItemRow({ item, mutating, onQtyChange, onRemove }: Props) {
  const lineTotal = item.unitPriceSnapshot * item.qty;
  const productUrl = ROUTES.PRODUCT(productUrlSegment({ id: item.productId, name: item.nameSnapshot }));
  return (
    <article
      className="flex gap-4 items-start py-4 border-b border-[var(--color-border)] last:border-b-0"
      aria-busy={mutating}
    >
      <Link
        to={productUrl}
        aria-label={item.nameSnapshot}
        className="block w-16 h-16 flex-shrink-0 bg-gray-100 border border-[var(--color-border)] rounded overflow-hidden"
      >
        <img src={item.imageUrlSnapshot} alt="" className="w-full h-full object-cover" />
      </Link>
      <div className="flex-1 min-w-0">
        <Link to={productUrl} className="block text-sm font-bold hover:underline truncate">
          {item.nameSnapshot}
        </Link>
        <p className="text-xs text-gray-600 mt-1">{formatTRY(item.unitPriceSnapshot)} (Birim)</p>
        <div className="mt-3">
          <QtyStepper
            qty={item.qty}
            onChange={(next) => onQtyChange(item.productId, next)}
            disabled={mutating}
          />
        </div>
      </div>
      <div className="text-right flex-shrink-0">
        <p className="text-base font-bold">{formatTRY(lineTotal)}</p>
        <button
          type="button"
          aria-label="Ürünü kaldır"
          title="Ürünü kaldır"
          disabled={mutating}
          onClick={() => onRemove(item.productId)}
          className="mt-2 inline-flex items-center justify-center w-8 h-8 text-gray-500 hover:text-[#DC2626] disabled:opacity-50"
        >
          <Trash2 size={16} />
        </button>
      </div>
    </article>
  );
}
