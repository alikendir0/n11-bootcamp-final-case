import { apiFetch } from '../lib/apiClient';

export interface CategoryDto {
  id: string;   // UUID
  slug: string;
  nameTr: string;
  sortOrder: number;
}

/** Fetch top-level Turkish categories (public, no auth). */
export function fetchCategories(): Promise<CategoryDto[]> {
  return apiFetch<CategoryDto[]>('/categories');
}

export const categoriesQueryKey = ['categories'] as const;
