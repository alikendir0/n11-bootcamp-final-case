import { computeTaksit } from '../../lib/taksit';
import { formatTRY } from '../../lib/format';

export function TaksitTable({ priceGross }: { priceGross: number }) {
  const rows = computeTaksit(priceGross);
  return (
    <section aria-labelledby="taksit-heading" className="mt-6 border border-[var(--color-border)] rounded p-4 bg-white">
      <h2 id="taksit-heading" className="text-sm font-bold mb-3">Taksit Seçenekleri</h2>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-600">
            <th className="py-1 font-normal">Taksit Sayısı</th>
            <th className="py-1 font-normal text-right">Aylık Tutar</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.installments} className="border-t border-[var(--color-border)]">
              <td className="py-1.5">{r.installments} Taksit</td>
              <td className="py-1.5 text-right font-bold">{formatTRY(r.monthly)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
