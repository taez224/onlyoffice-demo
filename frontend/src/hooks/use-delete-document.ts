'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { deleteDocument } from '@/api/documents';
import { documentKeys } from '@/lib/queries/documents';
import { isApiError } from '@/lib/api-client';
import type { DocumentResponse } from '@/types/document';

export function useDeleteDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteDocument,
    retry: 0,
    onMutate: async (fileKey) => {
      await queryClient.cancelQueries({ queryKey: documentKeys.lists() });

      const previousDocuments = queryClient.getQueryData<DocumentResponse[]>(
        documentKeys.lists()
      );

      queryClient.setQueryData<DocumentResponse[]>(
        documentKeys.lists(),
        (old) => old?.filter((doc) => doc.fileKey !== fileKey) ?? []
      );

      return { previousDocuments };
    },
    onError: (error, _fileKey, context) => {
      queryClient.setQueryData(documentKeys.lists(), context?.previousDocuments);
      const message = isApiError(error) ? error.message : '삭제에 실패했습니다';
      toast.error(message);
    },
    onSuccess: () => {
      toast.success('문서가 삭제되었습니다');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
    },
  });
}
