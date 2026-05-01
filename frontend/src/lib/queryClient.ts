import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './apiClient';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,           // 1 min — catalog browsing burst-friendly
      gcTime: 5 * 60_000,
      retry: (failureCount, error) => {
        // Do not retry 4xx (auth, validation) — only 5xx and network errors
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,  // PDP/listing don't need to refetch on tab return
    },
    mutations: {
      retry: false,                  // mutations are not idempotent (except POST /orders which has its own Idempotency-Key)
    },
  },
});
