import { useState } from 'react';

interface Props {
  description: string;
  // The Product type has just one description field for v1; "Özellikler" + "Kargo" tabs render planner-stub copy
}

type TabKey = 'aciklama' | 'ozellikler' | 'kargo';

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'aciklama', label: 'Açıklama' },
  { key: 'ozellikler', label: 'Özellikler' },
  { key: 'kargo', label: 'Kargo' },
];

export function PdpTabs({ description }: Props) {
  const [active, setActive] = useState<TabKey>('aciklama');
  return (
    <section className="mt-12">
      <div role="tablist" className="border-b border-[var(--color-border)] flex gap-6">
        {TABS.map(t => (
          <button
            key={t.key}
            role="tab"
            aria-selected={active === t.key}
            onClick={() => setActive(t.key)}
            className={`pb-3 text-sm border-b-2 ${
              active === t.key
                ? 'border-[#1C1C1E] font-bold text-[#1C1C1E]'
                : 'border-transparent text-gray-700 hover:text-[#1C1C1E]'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" className="py-6 text-sm leading-relaxed">
        {active === 'aciklama' && <p>{description || 'Bu ürün için açıklama henüz girilmemiştir.'}</p>}
        {active === 'ozellikler' && (
          <p>Ürün özellikleri yakında eklenecek.</p>
        )}
        {active === 'kargo' && (
          <p>Siparişiniz hazırlandıktan sonra anlaşmalı kargo firmaları ile 1-3 iş günü içinde gönderilir.</p>
        )}
      </div>
    </section>
  );
}
