'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { deleteDocuments } from '@/api/documents';
import { documentKeys } from '@/lib/queries/documents';
import type { DocumentResponse } from '@/types/document';

export function useDeleteDocuments() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteDocuments,
    retry: 0,
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
    onSuccess: (result, _fileKeys, context) => {
      const { succeeded, failed } = result;

      if (failed.length > 0) {
        const failedDocs = context?.previousDocuments?.filter((doc) =>
          failed.includes(doc.fileKey)
        );
        queryClient.setQueryData<DocumentResponse[]>(documentKeys.lists(), (old) => [
          ...(old ?? []),
          ...(failedDocs ?? []),
        ]);
      }

      if (succeeded.length > 0 && failed.length === 0) {
        toast.success(`${succeeded.length}개 문서가 삭제되었습니다`);
      } else if (succeeded.length > 0 && failed.length > 0) {
        toast.warning(
          `${succeeded.length}개 삭제 완료, ${failed.length}개 삭제 실패`
        );
      } else if (failed.length > 0) {
        toast.error(`${failed.length}개 문서 삭제에 실패했습니다`);
      }
    },
    onError: (_error, _fileKeys, context) => {
      queryClient.setQueryData(documentKeys.lists(), context?.previousDocuments);
      toast.error('삭제에 실패했습니다');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
    },
  });
}
