import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { RequireAuth } from './components/layout/RequireAuth';
import { RedirectIfAuthed } from './components/layout/RedirectIfAuthed';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import PlaceholderPage from './pages/PlaceholderPage';
import LoginPage from './pages/LoginPage';
import { CATEGORY_SLUGS } from './lib/categories';
import { ROUTES } from './lib/routes';

export const router = createBrowserRouter([
  {
    path: ROUTES.HOME,
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },

      // 8 category routes — Plan 10-04 replaces PlaceholderPage with CategoryListingPage
      ...CATEGORY_SLUGS.map(slug => ({
        path: slug,
        element: <PlaceholderPage name={`Category: ${slug}`} />,
      })),

      { path: 'arama', element: <PlaceholderPage name="Arama" /> },                    // Plan 10-04
      { path: 'urun/:slugAndId', element: <PlaceholderPage name="Ürün Detay" /> },     // Plan 10-05
      { path: 'sepetim', element: <PlaceholderPage name="Sepetim" /> },                // Plan 10-06

      // Anonymous-only auth pages — Plan 10-03 replaces these
      {
        element: <RedirectIfAuthed />,
        children: [
          { path: 'giris-yap', element: <LoginPage /> },
          { path: 'uye-ol', element: <PlaceholderPage name="Üye Ol" /> },
        ],
      },

      // Auth-required routes — Plans 10-07 and 10-08 replace these
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

      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
