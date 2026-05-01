import { describe, expect, it, vi } from 'vitest';

import type { BackendProductPage } from '../lib/types';
import { fetchProducts, normalizeProductPage } from './productApi';
import { apiFetch } from '../lib/apiClient';

vi.mock('../lib/apiClient', () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

describe('productApi normalization', () => {
  it('maps backend ProductSummaryDto fields before listing rendering', async () => {
    mockedApiFetch.mockResolvedValueOnce({
      content: [
        {
          id: 'p-1',
          nameTr: 'Çay Makinesi',
          priceGross: 1299.9,
          firstImageUrl: 'https://cdn.test/cay.jpg',
          categoryName: 'Elektrikli Ev Aletleri',
          categoryId: 'cat-1',
          stockQty: 7,
          createdAt: '2026-05-01T10:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    } satisfies BackendProductPage);

    const page = await fetchProducts({ page: 0, size: 20, sort: 'created_at,desc' });

    expect(page.content[0]).toMatchObject({
      id: 'p-1',
      name: 'Çay Makinesi',
      imageUrl: 'https://cdn.test/cay.jpg',
      categoryLabel: 'Elektrikli Ev Aletleri',
      categoryId: 'cat-1',
      stockQty: 7,
    });
  });

  it('supplies safe defaults for optional listing fields', () => {
    const page = normalizeProductPage({
      content: [
        {
          id: 'p-2',
          nameTr: 'Kablosuz Mouse',
          priceGross: 399,
          firstImageUrl: null,
          categoryName: 'Elektronik',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    expect(page.content[0]).toEqual({
      id: 'p-2',
      name: 'Kablosuz Mouse',
      description: '',
      priceGross: 399,
      kdvRate: 0,
      imageUrl: '',
      categoryId: '',
      categoryLabel: 'Elektronik',
      stockQty: 0,
      createdAt: '',
    });
  });
});
