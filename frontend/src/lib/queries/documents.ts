import { queryOptions } from '@tanstack/react-query';
import { getDocuments, getEditorConfig } from '@/api/documents';

export const documentKeys = {
  all: ['documents'] as const,
  lists: () => [...documentKeys.all, 'list'] as const,
  detail: (fileKey: string) => [...documentKeys.all, 'detail', fileKey] as const,
  config: (fileKey: string) => [...documentKeys.all, 'config', fileKey] as const,
};

export function documentsQueryOptions() {
  return queryOptions({
    queryKey: documentKeys.lists(),
    queryFn: getDocuments,
    staleTime: 60 * 1000,
    gcTime: 5 * 60 * 1000,
  });
}

export function editorConfigQueryOptions(fileKey: string) {
  return queryOptions({
    queryKey: documentKeys.config(fileKey),
    queryFn: () => getEditorConfig(fileKey),
    enabled: Boolean(fileKey),
    staleTime: 0,
    gcTime: 0,
  });
}
