'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { deleteDocuments } from '@/api/documents';
import { documentKeys } from '@/lib/queries/documents';
import { isApiError } from '@/lib/api-client';
import type { DocumentResponse } from '@/types/document';

/**
 * Batch delete hook for multiple documents.
 * Uses a single mutation to avoid race conditions with optimistic updates.
 */
export function useDeleteDocuments() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteDocuments,
    onMutate: async (fileKeys) => {
      await queryClient.cancelQueries({ queryKey: documentKeys.lists() });

      const previousDocuments = queryClient.getQueryData<DocumentResponse[]>(
        documentKeys.lists()
      );

      queryClient.setQueryData<DocumentResponse[]>(
        documentKeys.lists(),
        (old) => old?.filter((doc) => !fileKeys.includes(doc.fileKey)) ?? []
      );

      return { previousDocuments };
    },
    onError: (error, _fileKeys, context) => {
      queryClient.setQueryData(documentKeys.lists(), context?.previousDocuments);
      const message = isApiError(error) ? error.message : '삭제에 실패했습니다';
      toast.error(message);
    },
    onSuccess: (_data, fileKeys) => {
      const count = fileKeys.length;
      toast.success(`${count}개 문서가 삭제되었습니다`);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
    },
  });
}
