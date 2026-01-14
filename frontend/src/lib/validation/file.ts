export const MAX_FILE_SIZE_MB = 100;
export const MAX_FILE_SIZE = MAX_FILE_SIZE_MB * 1024 * 1024;

export const ALLOWED_EXTENSIONS = [
  // Word (문서)
  'doc', 'docm', 'docx', 'dot', 'dotm', 'dotx',
  'epub', 'fb2', 'fodt', 'htm', 'html', 'mht', 'mhtml',
  'odt', 'ott', 'rtf', 'txt', 'xml', 'md',
  'hwp', 'hwpx', 'wps', 'wpt', 'pages',
  // Cell (스프레드시트)
  'csv', 'et', 'ett', 'fods', 'numbers',
  'ods', 'ots', 'sxc',
  'xls', 'xlsb', 'xlsm', 'xlsx', 'xlt', 'xltm', 'xltx',
  // Slide (프레젠테이션)
  'dps', 'dpt', 'fodp', 'key',
  'odg', 'odp', 'otp',
  'pot', 'potm', 'potx', 'pps', 'ppsm', 'ppsx', 'ppt', 'pptm', 'pptx',
  // PDF
  'djvu', 'pdf', 'xps', 'oxps',
  // Diagram (Visio)
  'vsdx', 'vsdm', 'vssx', 'vssm', 'vstx', 'vstm',
] as const;

export type AllowedExtension = (typeof ALLOWED_EXTENSIONS)[number];

// RFC 4122 UUID pattern
export const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isValidUUID(value: string): boolean {
  return UUID_REGEX.test(value);
}

export function getFileExtension(fileName: string): string | undefined {
  return fileName.split('.').pop()?.toLowerCase();
}

export function isAllowedExtension(extension: string): extension is AllowedExtension {
  return ALLOWED_EXTENSIONS.includes(extension as AllowedExtension);
}

export function validateFile(file: File, formatFileSize: (bytes: number) => string): string | null {
  if (file.size > MAX_FILE_SIZE) {
    return `파일 크기가 ${MAX_FILE_SIZE_MB}MB를 초과합니다 (현재: ${formatFileSize(file.size)})`;
  }

  const extension = getFileExtension(file.name);
  if (!extension || !isAllowedExtension(extension)) {
    return `허용되지 않은 파일 형식입니다. 허용: ${ALLOWED_EXTENSIONS.join(', ')}`;
  }

  return null;
}
