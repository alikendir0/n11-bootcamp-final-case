import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { RequireAuth } from './components/layout/RequireAuth';
import { RedirectIfAuthed } from './components/layout/RedirectIfAuthed';
import HomePage from './pages/HomePage';
import CategoryListingPage from './pages/CategoryListingPage';
import SearchPage from './pages/SearchPage';
import NotFoundPage from './pages/NotFoundPage';
import PlaceholderPage from './pages/PlaceholderPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProductDetailPage from './pages/ProductDetailPage';
import { ROUTES } from './lib/routes';

export const router = createBrowserRouter([
  {
    path: ROUTES.HOME,
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },

      // Search results — Plan 10-04
      { path: 'arama', element: <SearchPage /> },

      // Product detail — Plan 10-05
      { path: 'urun/:slugAndId', element: <ProductDetailPage /> },

      // Cart — Plan 10-06
      { path: 'sepetim', element: <PlaceholderPage name="Sepetim" /> },

      // Anonymous-only auth pages — Plan 10-03
      {
        element: <RedirectIfAuthed />,
        children: [
          { path: 'giris-yap', element: <LoginPage /> },
          { path: 'uye-ol', element: <RegisterPage /> },
        ],
      },

      // Auth-required routes — Plans 10-07 and 10-08
      {
        element: <RequireAuth />,
        children: [
          { path: 'odeme/adres', element: <PlaceholderPage name="Adres" /> },          // Plan 10-07
          { path: 'odeme/odeme', element: <PlaceholderPage name="Ödeme" /> },          // Plan 10-07
          { path: 'odeme/sonuc', element: <PlaceholderPage name="Sonuç" /> },          // Plan 10-07
          { path: 'hesabim', element: <PlaceholderPage name="Hesabım" /> },            // Plan 10-08
          { path: 'siparislerim', element: <PlaceholderPage name="Siparişlerim" /> },  // Plan 10-08
          { path: 'siparislerim/:orderId', element: <PlaceholderPage name="Sipariş Detayı" /> },  // Plan 10-08
          { path: 'adreslerim', element: <PlaceholderPage name="Adreslerim" /> },     // Plan 10-08
        ],
      },

      // Category listing — MUST come LAST before catch-all (after all literal paths)
      // CategoryListingPage validates slug via isCategorySlug and renders NotFoundPage for unknowns
      { path: ':categorySlug', element: <CategoryListingPage /> },

      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
