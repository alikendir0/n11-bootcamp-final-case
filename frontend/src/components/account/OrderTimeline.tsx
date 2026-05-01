import type { OrderStatus } from '../../lib/types';
import { TIMELINE_STEPS, computeTimelineStep } from '../../lib/orderStatus';

export function OrderTimeline({ status, cancelReason }: { status: OrderStatus; cancelReason?: string }) {
  const { activeStepIndex, isCancelled } = computeTimelineStep(status);

  if (isCancelled) {
    return (
      <div className="bg-[#DC2626] text-white rounded p-4 text-center">
        <p className="font-bold">İptal Edildi</p>
        {cancelReason && <p className="text-sm mt-1 opacity-90">Sebep: {cancelReason}</p>}
      </div>
    );
  }

  return (
    <ol className="flex items-center justify-between gap-2" aria-label="Sipariş durumu">
      {TIMELINE_STEPS.map((step, i) => {
        const isActive = i === activeStepIndex;
        const isPast = i < activeStepIndex;
        const isFuture = i > activeStepIndex;
        return (
          <li key={i} className="flex-1 flex flex-col items-center text-center">
            <span
              className={`inline-flex items-center justify-center w-10 h-10 rounded-full text-sm font-bold ${
                isActive
                  ? 'bg-[#1C1C1E] text-white'
                  : isPast
                    ? 'bg-[#34A853] text-white'
                    : 'bg-gray-200 text-gray-500'
              }`}
              aria-current={isActive ? 'step' : undefined}
            >
              {i + 1}
            </span>
            <span className={`mt-2 text-xs ${isFuture ? 'text-gray-500' : 'font-bold'}`}>
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
