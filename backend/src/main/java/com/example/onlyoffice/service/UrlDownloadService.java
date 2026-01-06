package com.example.onlyoffice.service;

/**
 * URL에서 파일을 다운로드하여 스토리지에 저장하는 서비스 인터페이스.
 *
 * <p>ONLYOFFICE Document Server callback에서 제공하는 URL로부터
 * 편집된 문서를 다운로드하여 MinIO 스토리지에 저장합니다.</p>
 */
public interface UrlDownloadService {

    /**
     * URL에서 파일을 다운로드하여 지정된 스토리지 경로에 저장합니다.
     *
     * @param downloadUrl 다운로드할 파일의 URL
     * @param storagePath 저장할 스토리지 경로
     * @return 다운로드 결과 (파일 크기 포함)
     * @throws RuntimeException 다운로드 또는 저장 실패 시
     */
    DownloadResult downloadAndSave(String downloadUrl, String storagePath);

    /**
     * 다운로드 결과를 담는 레코드.
     *
     * @param fileSize 다운로드된 파일의 크기 (bytes)
     */
    record DownloadResult(long fileSize) {}
}
