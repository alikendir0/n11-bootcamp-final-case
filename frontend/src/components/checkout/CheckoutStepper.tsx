import { Link } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';

export type CheckoutStep = 'adres' | 'odeme' | 'onay';

const STEPS: Array<{ key: CheckoutStep; label: string; href?: string }> = [
  { key: 'adres', label: 'Adres', href: ROUTES.CHECKOUT_ADDRESS },
  { key: 'odeme', label: 'Ödeme', href: ROUTES.CHECKOUT_PAYMENT },
  { key: 'onay', label: 'Onay' }, // result page; not directly clickable until reached
];

export function CheckoutStepper({ active }: { active: CheckoutStep }) {
  const activeIdx = STEPS.findIndex(s => s.key === active);
  return (
    <ol aria-label="Adımlar" className="flex items-center justify-center gap-3 my-6 text-sm">
      {STEPS.map((step, i) => {
        const isActive = i === activeIdx;
        const isPast = i < activeIdx;
        const Indicator = (
          <span
            aria-current={isActive ? 'step' : undefined}
            className={`inline-flex items-center justify-center w-8 h-8 rounded-full text-xs font-bold ${
              isActive
                ? 'bg-[#1C1C1E] text-white'
                : isPast
                  ? 'bg-[#34A853] text-white'
                  : 'bg-gray-200 text-gray-600'
            }`}
          >
            {i + 1}
          </span>
        );
        return (
          <li key={step.key} className="flex items-center gap-3">
            {step.href && isPast ? (
              <Link to={step.href} className="flex items-center gap-2 hover:underline">
                {Indicator} <span>{step.label}</span>
              </Link>
            ) : (
              <span className="flex items-center gap-2">
                {Indicator} <span className={isActive ? 'font-bold' : ''}>{step.label}</span>
              </span>
            )}
            {i < STEPS.length - 1 && <span aria-hidden className="text-gray-400">›</span>}
          </li>
        );
      })}
    </ol>
  );
}
