import { useQuery } from '@tanstack/react-query';
import { fetchProducts, productsQueryKey } from '../../api/productApi';
import { ProductCard } from '../listing/ProductCard';
import { SkeletonCard } from '../feedback/SkeletonCard';

const RAIL_PARAMS = {
  page: 0,
  size: 10,
  sort: 'created_at,desc' as const,
};

export function ProductRail({ heading }: { heading: string }) {
  const { data, isLoading } = useQuery({
    queryKey: productsQueryKey(RAIL_PARAMS),
    queryFn: () => fetchProducts(RAIL_PARAMS),
  });

  return (
    <section aria-label={heading} className="my-12">
      <h2 className="text-xl font-bold mb-4">{heading}</h2>
      {isLoading ? (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {data?.content.slice(0, 10).map(p => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      )}
    </section>
  );
}
