import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getQueryClient } from '@/lib/query-client';
import { documentsQueryOptions } from '@/lib/queries/documents';
import { DocumentsPage } from '@/components/documents';

export default async function HomePage() {
  const queryClient = getQueryClient();

  await queryClient.prefetchQuery(documentsQueryOptions());

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
