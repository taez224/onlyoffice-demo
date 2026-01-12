'use client';

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { editorConfigQueryOptions } from '@/lib/queries/documents';

export function useEditorConfig(fileKey: string) {
  return useQuery(editorConfigQueryOptions(fileKey));
}

/**
 * Suspense 지원 에디터 설정 훅
 * Suspense 경계 내에서 사용하면 data가 항상 존재함을 보장
 */
export function useEditorConfigSuspense(fileKey: string) {
  return useSuspenseQuery(editorConfigQueryOptions(fileKey));
}
