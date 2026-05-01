import { Link } from 'react-router-dom';
import { ROUTES } from '../lib/routes';

export default function NotFoundPage() {
  return (
    <div className="mx-auto my-16 max-w-2xl text-center px-4">
      <h1 className="text-2xl font-bold mb-4">Aradığınız sayfayı bulamadık.</h1>
      <p className="mb-6">Bu sayfa mevcut değil veya taşınmış olabilir.</p>
      <Link
        to={ROUTES.HOME}
        className="inline-block bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
      >
        Ana Sayfaya Dön
      </Link>
    </div>
  );
}
