import { useParams } from 'react-router-dom';
import { isCategorySlug, CATEGORY_LABELS } from '../lib/categories';
import { Breadcrumbs } from '../components/listing/Breadcrumbs';
import { ListingGrid } from '../components/listing/ListingGrid';
import NotFoundPage from './NotFoundPage';

export default function CategoryListingPage() {
  const { categorySlug } = useParams<{ categorySlug: string }>();
  if (!categorySlug || !isCategorySlug(categorySlug)) {
    return <NotFoundPage />;
  }
  const label = CATEGORY_LABELS[categorySlug];
  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <Breadcrumbs items={[{ label: 'Ana Sayfa', to: '/' }, { label }]} />
      <h1 className="text-2xl font-bold mb-6">{label}</h1>
      <ListingGrid categorySlug={categorySlug} />
    </div>
  );
}
