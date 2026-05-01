import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RouteErrorFallback } from './ErrorBoundary';

function ThrowingRoute() {
  throw new Error('secret route stack should not be shown');
}

describe('RouteErrorFallback', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('replaces React Router developer error UI with Turkish fallback copy', async () => {
    const router = createMemoryRouter([
      {
        path: '/',
        element: <ThrowingRoute />,
        errorElement: <RouteErrorFallback />,
      },
    ]);

    render(<RouterProvider router={router} />);

    expect(await screen.findByText('Bir hata oluştu')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Sayfayı Yenile' })).toBeVisible();
    expect(screen.queryByText(/Unexpected Application Error/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/secret route stack/i)).not.toBeInTheDocument();
  });
});
