'use client';

import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { editorConfigQueryOptions } from '@/lib/queries/documents';

export function useEditorConfig(fileKey: string) {
  return useQuery(editorConfigQueryOptions(fileKey));
}

/**
 * Suspense 지원 에디터 설정 훅
 * Suspense 경계 내에서 사용하면 data가 항상 존재함을 보장
 *
 * 주의: useSuspenseQuery는 enabled 옵션을 무시하므로,
 * 반드시 유효한 fileKey를 전달해야 합니다.
 *
 * @throws {Error} fileKey가 비어있으면 에러 발생
 */
export function useEditorConfigSuspense(fileKey: string) {
  if (!fileKey) {
    throw new Error('fileKey is required for useEditorConfigSuspense');
  }
  return useSuspenseQuery(editorConfigQueryOptions(fileKey));
}
