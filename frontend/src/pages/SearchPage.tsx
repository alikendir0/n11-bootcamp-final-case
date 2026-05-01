import { useSearchParams } from 'react-router-dom';
import { Breadcrumbs } from '../components/listing/Breadcrumbs';
import { ListingGrid } from '../components/listing/ListingGrid';

export default function SearchPage() {
  const [params] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <Breadcrumbs
        items={[{ label: 'Ana Sayfa', to: '/' }, { label: 'Arama Sonuçları' }]}
      />
      <h1 className="text-2xl font-bold mb-6">
        {q ? `'${q}' için arama sonuçları` : 'Arama'}
      </h1>
      {q ? (
        <ListingGrid query={q} />
      ) : (
        <p className="text-gray-700">
          Lütfen arama yapmak için bir terim giriniz.
        </p>
      )}
    </div>
  );
}
