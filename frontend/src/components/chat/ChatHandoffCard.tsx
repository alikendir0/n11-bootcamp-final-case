import { Link } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';
import { formatTRY } from '../../lib/format';
import type { ChatCartSummaryData, ChatOrderHandoffData } from '../../lib/types';

interface ChatHandoffCardProps {
  type: 'cart' | 'order' | 'payment';
  cart?: ChatCartSummaryData | undefined;
  order?: ChatOrderHandoffData | undefined;
  paymentPageUrl?: string | undefined;
}

export function ChatHandoffCard({ type, cart, order, paymentPageUrl }: ChatHandoffCardProps) {
  if (type === 'cart' && cart) {
    return (
      <div className="bg-white border border-[#E5E7EB] rounded-lg p-4 space-y-3">
        <p className="text-sm font-bold">Sepetiniz ({cart.itemCount} ürün)</p>
        {typeof cart.totalAmount === 'number' && (
          <p className="text-sm">Toplam: {formatTRY(cart.totalAmount)}</p>
        )}
        <Link
          to={ROUTES.CART}
          className="inline-block text-sm bg-[#1C1C1E] text-white px-4 py-2 rounded font-medium"
        >
          Sepete Git
        </Link>
      </div>
    );
  }

  if (type === 'order' && order) {
    return (
      <div className="bg-white border border-[#E5E7EB] rounded-lg p-4 space-y-3">
        <p className="text-sm font-bold">Sipariş #{order.orderId}</p>
        {order.status && <p className="text-xs text-gray-600">Durum: {order.status}</p>}
        {order.paymentPageUrl ? (
          <a
            href={order.paymentPageUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-block text-sm bg-[#1C1C1E] text-white px-4 py-2 rounded font-medium"
          >
            Ödemeye Git
          </a>
        ) : (
          <Link
            to={order.orderId ? ROUTES.ORDER_DETAIL(order.orderId) : ROUTES.ORDERS}
            className="inline-block text-sm bg-[#1C1C1E] text-white px-4 py-2 rounded font-medium"
          >
            Sipariş Detayı
          </Link>
        )}
      </div>
    );
  }

  if (type === 'payment' && paymentPageUrl && paymentPageUrl !== 'null') {
    return (
      <div className="bg-white border border-[#E5E7EB] rounded-lg p-4 space-y-3">
        <p className="text-sm font-bold">Ödeme bağlantınız hazır</p>
        <a
          href={paymentPageUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block text-sm bg-[#1C1C1E] text-white px-4 py-2 rounded font-medium"
        >
          Ödemeye Devam Et
        </a>
      </div>
    );
  }

  return null;
}
