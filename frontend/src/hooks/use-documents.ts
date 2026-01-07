'use client';

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { documentsQueryOptions } from '@/lib/queries/documents';

export function useDocuments() {
  return useQuery(documentsQueryOptions());
}

export function useDocumentsSuspense() {
  return useSuspenseQuery(documentsQueryOptions());
}
