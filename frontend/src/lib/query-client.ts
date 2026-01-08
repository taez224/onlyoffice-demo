import { QueryClient } from '@tanstack/react-query';
import { cache } from 'react';

export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
      },
    },
  });
}

// Server: cache() creates request-scoped instance (prevents data leakage)
// Client: singleton pattern
let browserQueryClient: QueryClient | undefined;

export const getQueryClient = cache(() => {
  if (typeof window === 'undefined') {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
});
