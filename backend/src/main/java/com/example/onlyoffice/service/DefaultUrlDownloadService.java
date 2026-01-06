package com.example.onlyoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

/**
 * URL에서 파일을 다운로드하여 MinIO 스토리지에 저장하는 기본 구현체.
 *
 * <p>ONLYOFFICE Document Server callback에서 제공하는 URL로부터
 * 편집된 문서를 다운로드하여 저장합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUrlDownloadService implements UrlDownloadService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int CONNECTION_TIMEOUT_MS = 10_000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 60_000; // 60 seconds

    private final MinioStorageService storageService;

    @Override
    public DownloadResult downloadAndSave(String downloadUrl, String storagePath) {
        log.info("Downloading file from {} to {}", downloadUrl, storagePath);

        try {
            URLConnection connection = URI.create(downloadUrl).toURL().openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            long contentLength = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = DEFAULT_CONTENT_TYPE;
            }

            try (ByteCountingInputStream inputStream = new ByteCountingInputStream(connection.getInputStream())) {
                storageService.uploadStream(inputStream, contentLength, contentType, storagePath);

                long uploadedSize = inputStream.getBytesRead();
                long fileSize = uploadedSize > 0 ? uploadedSize : (contentLength > 0 ? contentLength : 0);

                log.info("File downloaded and saved successfully. storagePath: {}, size: {}", storagePath, fileSize);
                return new DownloadResult(fileSize);
            }
        } catch (Exception e) {
            log.error("Error downloading file from {}", downloadUrl, e);
            throw new RuntimeException("Failed to download and save file from URL", e);
        }
    }

    /**
     * 읽은 바이트 수를 추적하는 InputStream 래퍼.
     */
    private static class ByteCountingInputStream extends FilterInputStream {

        private long bytesRead;

        protected ByteCountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result >= 0) {
                bytesRead++;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result > 0) {
                bytesRead += result;
            }
            return result;
        }

        long getBytesRead() {
            return bytesRead;
        }
    }
}
