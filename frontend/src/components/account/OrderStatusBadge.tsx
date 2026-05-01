import type { OrderStatus } from '../../lib/types';
import { getStatusBadge } from '../../lib/orderStatus';

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { label, classes } = getStatusBadge(status);
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded text-xs font-bold ${classes}`}>
      {label}
    </span>
  );
}
