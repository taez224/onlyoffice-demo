'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { uploadDocument } from '@/api/documents';
import { documentKeys } from '@/lib/queries/documents';
import { isApiError } from '@/lib/api-client';

export function useUploadDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadDocument,
    retry: 0,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
      toast.success(`${data.fileName} 업로드 완료`);
    },
    onError: (error) => {
      const message = isApiError(error) ? error.message : '업로드에 실패했습니다';
      toast.error(message);
    },
  });
}
