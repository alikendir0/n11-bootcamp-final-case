import type { SiralamaKey } from '../../lib/listingParams';

const OPTIONS: Array<{ value: SiralamaKey; label: string }> = [
  { value: 'tarih-yeni', label: 'Tarihe Göre (Yeni→Eski)' },
  { value: 'fiyat-artan', label: 'Fiyata Göre (Düşük→Yük)' },
  { value: 'fiyat-azalan', label: 'Fiyata Göre (Yük→Düş)' },
];

interface Props {
  value: SiralamaKey;
  onChange: (next: SiralamaKey) => void;
}

export function SortControl({ value, onChange }: Props) {
  return (
    <label className="inline-flex items-center gap-2 text-sm">
      <span>Sıralama:</span>
      <select
        value={value}
        onChange={e => onChange(e.target.value as SiralamaKey)}
        className="h-10 border border-[var(--color-border)] rounded px-3 bg-white"
      >
        {OPTIONS.map(o => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
