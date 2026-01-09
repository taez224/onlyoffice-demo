import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getQueryClient } from '@/lib/query-client';
import { documentsQueryOptions } from '@/lib/queries/documents';
import { DocumentsPage } from '@/components/documents';

export default function HomePage() {
  const queryClient = getQueryClient();

  queryClient.prefetchQuery(documentsQueryOptions());

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
