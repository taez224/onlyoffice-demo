/**
 * Document API Types
 * Backend DTO와 일치하는 타입 정의
 */

/** 문서 상태 */
export type DocumentStatus = 'PENDING' | 'ACTIVE' | 'DELETED';

/** 문서 타입 (ONLYOFFICE) */
export type DocumentType = 'word' | 'cell' | 'slide' | 'pdf' | 'diagram';

/** GET /api/documents 응답 */
export interface DocumentResponse {
  id: number;
  fileName: string;
  fileKey: string;
  fileType: string;
  documentType: DocumentType;
  fileSize: number;
  status: DocumentStatus;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

/** POST /api/documents/upload 응답 */
export interface DocumentUploadResponse {
  id: number;
  fileName: string;
  fileKey: string;
  fileType: string;
  documentType: DocumentType;
  fileSize: number;
  message: string;
}

/** GET /api/documents/{fileKey}/config 응답 */
export interface EditorConfigResponse {
  config: OnlyOfficeConfig;
  documentServerUrl: string;
}

/** ONLYOFFICE Editor Config (SDK 생성) */
export interface OnlyOfficeConfig {
  documentType: DocumentType;
  document: {
    fileType: string;
    key: string;
    title: string;
    url: string;
    permissions?: {
      edit: boolean;
      download: boolean;
      print: boolean;
    };
  };
  editorConfig: {
    callbackUrl: string;
    lang?: string;
    mode?: 'edit' | 'view';
    user?: {
      id: string;
      name: string;
    };
  };
  token?: string;
}

/** API 에러 응답 */
export interface ApiError {
  message: string;
  status: number;
  timestamp?: string;
  path?: string;
}
