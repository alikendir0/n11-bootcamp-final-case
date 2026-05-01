import type { OrderStatus } from './types';

export interface TimelineStep {
  label: string;
  description?: string;
}

/** UI-SPEC §Order Detail: four-step display timeline; v1 stops at "Kargoya Verildi" (no real shipping). */
export const TIMELINE_STEPS: readonly TimelineStep[] = [
  { label: 'Sipariş Alındı' },
  { label: 'Hazırlanıyor' },
  { label: 'Kargoya Verildi' },
  { label: 'Teslim Edildi' },  // static greyed placeholder per CONTEXT.md (out of v1 scope)
] as const;

export interface ComputedTimeline {
  activeStepIndex: number;   // -1 when cancelled
  isCancelled: boolean;
}

/** Maps saga state → display step index per CONTEXT.md status-timeline rules. */
export function computeTimelineStep(status: OrderStatus): ComputedTimeline {
  switch (status) {
    case 'PENDING':
    case 'STOCK_RESERVED':
      return { activeStepIndex: 0, isCancelled: false };
    case 'PAID':
      return { activeStepIndex: 1, isCancelled: false };
    case 'CONFIRMED':
      return { activeStepIndex: 2, isCancelled: false };
    case 'STOCK_FAILED':
    case 'PAYMENT_FAILED':
    case 'CANCELLED':
      return { activeStepIndex: -1, isCancelled: true };
    default:
      return { activeStepIndex: 0, isCancelled: false };
  }
}

export interface StatusBadgeStyle {
  label: string;
  classes: string;  // Tailwind classes
}

/** Per UI-SPEC §Orders List status-badge color: PENDING/STOCK_RESERVED → gray, PAID → blue, CONFIRMED → green, FAILED/CANCELLED → red. */
export function getStatusBadge(status: OrderStatus): StatusBadgeStyle {
  switch (status) {
    case 'PENDING':
    case 'STOCK_RESERVED':
      return { label: 'Onay Bekliyor', classes: 'bg-gray-200 text-gray-700' };
    case 'PAID':
      return { label: 'Hazırlanıyor', classes: 'bg-blue-100 text-blue-800' };
    case 'CONFIRMED':
      return { label: 'Onaylandı', classes: 'bg-[#34A853] text-white' };
    case 'STOCK_FAILED':
      return { label: 'Stok Yetersiz', classes: 'bg-[#DC2626] text-white' };
    case 'PAYMENT_FAILED':
      return { label: 'Ödeme Başarısız', classes: 'bg-[#DC2626] text-white' };
    case 'CANCELLED':
      return { label: 'İptal Edildi', classes: 'bg-[#DC2626] text-white' };
    default:
      return { label: 'Bilinmiyor', classes: 'bg-gray-200 text-gray-700' };
  }
}

/** Whether the user can cancel from the UI — only PENDING per Phase 5 CD-09 + UI-SPEC. */
export function isCancellable(status: OrderStatus): boolean {
  return status === 'PENDING';
}
