import { apiClient } from '@/lib/api-client';
import type {
  DocumentResponse,
  DocumentUploadResponse,
  EditorConfigResponse,
} from '@/types/document';

export async function getDocuments(): Promise<DocumentResponse[]> {
  const { data } = await apiClient.get<DocumentResponse[]>('/documents');
  return data;
}

export async function uploadDocument(file: File): Promise<DocumentUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const { data } = await apiClient.post<DocumentUploadResponse>(
    '/documents/upload',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
    }
  );
  return data;
}

export async function deleteDocument(fileKey: string): Promise<void> {
  await apiClient.delete(`/documents/${fileKey}`);
}

export async function deleteDocuments(fileKeys: string[]): Promise<void> {
  await Promise.all(fileKeys.map((fileKey) => apiClient.delete(`/documents/${fileKey}`)));
}

export async function getEditorConfig(fileKey: string): Promise<EditorConfigResponse> {
  const { data } = await apiClient.get<EditorConfigResponse>(
    `/documents/${fileKey}/config`
  );
  return data;
}
