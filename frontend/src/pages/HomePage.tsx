import { HeroCarousel } from '../components/home/HeroCarousel';
import { ProductRail } from '../components/home/ProductRail';

export default function HomePage() {
  return (
    <div className="mx-auto max-w-7xl px-4">
      <HeroCarousel />
      <ProductRail heading="Yeni Gelenler" />
    </div>
  );
}
