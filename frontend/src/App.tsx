import { useEffect } from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { router } from './router';
import { queryClient } from './lib/queryClient';
import { useAuthStore } from './store/authStore';
import { ErrorBoundary } from './components/feedback/ErrorBoundary';

export default function App() {
  const hydrateFromStorage = useAuthStore(s => s.hydrateFromStorage);

  useEffect(() => {
    hydrateFromStorage();
  }, [hydrateFromStorage]);

  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
