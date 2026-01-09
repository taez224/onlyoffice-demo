import { isServer, QueryClient, defaultShouldDehydrateQuery } from '@tanstack/react-query';
import { cache } from 'react';

export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
      },
      dehydrate: {
        // pending 상태의 쿼리도 dehydrate (스트리밍 지원)
        shouldDehydrateQuery: (query) =>
          defaultShouldDehydrateQuery(query) ||
          query.state.status === 'pending',
        shouldRedactErrors: (error) => {
          // Next.js 서버 에러를 가로채면 안 됨
          // → Next.js가 동적 페이지를 감지하는 방식이기 때문
          return false; // 에러 정보를 그대로 유지
        },
      },
    },
  });
}

// Server: cache() creates request-scoped instance (prevents data leakage)
// Client: singleton pattern
let browserQueryClient: QueryClient | undefined;

export const getQueryClient = cache(() => {
  if (isServer) {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
});
