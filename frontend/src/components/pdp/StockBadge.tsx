export function StockBadge({ qty }: { qty: number }) {
  const inStock = qty > 0;
  return (
    <p className="flex items-center gap-2 text-sm">
      <span
        aria-hidden
        className={`w-2.5 h-2.5 rounded-full ${inStock ? 'bg-[#34A853]' : 'bg-[#DC2626]'}`}
      />
      <span className={inStock ? 'text-[#34A853]' : 'text-[#DC2626]'}>
        {inStock ? 'Stokta' : 'Tükendi'}
      </span>
    </p>
  );
}
