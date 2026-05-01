import { Minus, Plus } from 'lucide-react';

interface Props {
  qty: number;
  onChange: (next: number) => void;
  disabled?: boolean;
}

const MAX_QTY = 99;  // Phase 5 CD-10 soft-cap
const MIN_QTY = 1;

export function QtyStepper({ qty, onChange, disabled = false }: Props) {
  return (
    <div className="inline-flex items-center border border-[var(--color-border)] rounded">
      <button
        type="button"
        aria-label="Adedi azalt"
        disabled={disabled || qty <= MIN_QTY}
        onClick={() => onChange(qty - 1)}
        className="w-9 h-9 inline-flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <Minus size={14} />
      </button>
      <span className="w-10 text-center text-sm font-bold" aria-live="polite">{qty}</span>
      <button
        type="button"
        aria-label="Adedi artır"
        disabled={disabled || qty >= MAX_QTY}
        onClick={() => onChange(qty + 1)}
        className="w-9 h-9 inline-flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <Plus size={14} />
      </button>
    </div>
  );
}
