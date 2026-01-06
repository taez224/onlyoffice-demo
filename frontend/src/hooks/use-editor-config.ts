'use client';

import { useQuery } from '@tanstack/react-query';
import { editorConfigQueryOptions } from '@/lib/queries/documents';

export function useEditorConfig(fileKey: string) {
  return useQuery(editorConfigQueryOptions(fileKey));
}
