'use client';

import { useQuery } from '@tanstack/react-query';
import { documentsQueryOptions } from '@/lib/queries/documents';

export function useDocuments() {
  return useQuery(documentsQueryOptions());
}
