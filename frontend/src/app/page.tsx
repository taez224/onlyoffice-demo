import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getQueryClient } from '@/lib/query-client';
import { documentsQueryOptions } from '@/lib/queries/documents';
import { DocumentsPage } from '@/components/documents';

// 빌드 시점에 백엔드 API가 없으므로 동적 렌더링 사용
export const dynamic = 'force-dynamic';

export default function HomePage() {
  const queryClient = getQueryClient();

  queryClient.prefetchQuery(documentsQueryOptions());

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
