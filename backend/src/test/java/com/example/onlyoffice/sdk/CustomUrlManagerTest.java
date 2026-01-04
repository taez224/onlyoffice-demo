package com.example.onlyoffice.sdk;

import com.example.onlyoffice.config.OnlyOfficeProperties;
import com.example.onlyoffice.service.MinioStorageService;
import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.manager.settings.SettingsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUrlManager")
class CustomUrlManagerTest {

    @Mock(lenient = true)
    private SettingsManager settingsManager;

    @Mock(lenient = true)
    private OnlyOfficeProperties onlyOfficeProperties;

    @Mock(lenient = true)
    private MinioStorageService minioStorageService;

    @Mock(lenient = true)
    private DocumentManager documentManager;

    private CustomUrlManager urlManager;

    private static final String SERVER_BASE_URL = "http://localhost:8080";
    private static final String MINIO_EXTERNAL_ENDPOINT = "http://minio:9000";

    @BeforeEach
    void setUp() {
        urlManager = new CustomUrlManager(
                settingsManager,
                onlyOfficeProperties,
                minioStorageService,
                documentManager
        );

        // Set serverBaseUrl using reflection
        ReflectionTestUtils.setField(urlManager, "serverBaseUrl", SERVER_BASE_URL);

        // Default property values
        when(onlyOfficeProperties.getMinioExternalEndpoint()).thenReturn(MINIO_EXTERNAL_ENDPOINT);
    }

    @Nested
    @DisplayName("getFileUrl - Backend Proxy Mode")
    class GetFileUrlProxyMode {

        @BeforeEach
        void setUp() {
            when(onlyOfficeProperties.isUsePresignedUrls()).thenReturn(false);
        }

        @Test
        @DisplayName("presigned URLs 비활성화 시 백엔드 프록시 URL 반환")
        void shouldReturnBackendProxyUrlWhenPresignedUrlsDisabled() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/550e8400-e29b-41d4-a716-446655440000");
            verify(minioStorageService, never()).generatePresignedUrl(anyString());
            verify(documentManager, never()).getDocumentName(anyString());
        }

        @Test
        @DisplayName("하이픈이 포함된 UUID fileKey URL 생성")
        void shouldEncodeFileKeyWithHyphens() {
            // given
            String fileKey = "abc-123-def-456";

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/abc-123-def-456");
        }
    }

    @Nested
    @DisplayName("getFileUrl - Presigned URL Mode")
    class GetFileUrlPresignedMode {

        @BeforeEach
        void setUp() {
            when(onlyOfficeProperties.isUsePresignedUrls()).thenReturn(true);
        }

        @Test
        @DisplayName("presigned URLs 활성화 시 MinIO presigned URL 반환")
        void shouldReturnMinioPresignedUrlWhenEnabled() {
            // given
            String fileKey = "test-file-key";
            String filename = "test.docx";
            String expectedObjectPath = "documents/test-file-key/test.docx";
            String localhostPresignedUrl = "http://localhost:9000/onlyoffice-documents/documents/test-file-key/test.docx?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=minioadmin&X-Amz-Signature=abc123";
            String expectedPresignedUrl = "http://minio:9000/onlyoffice-documents/documents/test-file-key/test.docx?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=minioadmin&X-Amz-Signature=abc123";

            when(documentManager.getDocumentName(fileKey)).thenReturn(filename);
            when(minioStorageService.generatePresignedUrl(expectedObjectPath)).thenReturn(localhostPresignedUrl);

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo(expectedPresignedUrl);
            verify(documentManager).getDocumentName(fileKey);
            verify(minioStorageService).generatePresignedUrl(expectedObjectPath);
        }

        @Test
        @DisplayName("127.0.0.1 주소도 docker endpoint로 변환")
        void shouldReplaceLocalhostIpWithDockerEndpoint() {
            // given
            String fileKey = "test-file-key";
            String filename = "test.xlsx";
            String objectPath = "documents/test-file-key/test.xlsx";
            String presignedUrlWith127 = "http://127.0.0.1:9000/bucket/path?signature=xyz";
            String expectedUrl = "http://minio:9000/bucket/path?signature=xyz";

            when(documentManager.getDocumentName(fileKey)).thenReturn(filename);
            when(minioStorageService.generatePresignedUrl(objectPath)).thenReturn(presignedUrlWith127);

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("presigned URL 생성 실패 시 백엔드 프록시로 fallback")
        void shouldFallbackToProxyOnPresignedUrlError() {
            // given
            String fileKey = "test-file-key";

            when(documentManager.getDocumentName(fileKey)).thenThrow(new RuntimeException("MinIO error"));

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/test-file-key");
            verify(documentManager).getDocumentName(fileKey);
        }

        @Test
        @DisplayName("복잡한 파일명도 올바른 object path 생성")
        void shouldHandleComplexFilenames() {
            // given
            String fileKey = "uuid-123";
            String filename = "My Document (Final).docx";
            String expectedObjectPath = "documents/uuid-123/My Document (Final).docx";
            String presignedUrl = "http://localhost:9000/bucket/path?sig=abc";

            when(documentManager.getDocumentName(fileKey)).thenReturn(filename);
            when(minioStorageService.generatePresignedUrl(expectedObjectPath)).thenReturn(presignedUrl);

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            verify(minioStorageService).generatePresignedUrl(expectedObjectPath);
            assertThat(result).startsWith("http://minio:9000");
        }
    }

    @Nested
    @DisplayName("getCallbackUrl")
    class GetCallbackUrl {

        @Test
        @DisplayName("콜백 URL 생성 (fileKey 쿼리 파라미터 포함)")
        void shouldGenerateCallbackUrlWithFileKeyParam() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";

            // when
            String result = urlManager.getCallbackUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/callback?fileKey=550e8400-e29b-41d4-a716-446655440000");
        }

        @Test
        @DisplayName("하이픈이 포함된 UUID fileKey 쿼리 파라미터")
        void shouldEncodeHyphensInQueryParam() {
            // given
            String fileKey = "abc-123-def-456";

            // when
            String result = urlManager.getCallbackUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/callback?fileKey=abc-123-def-456");
        }
    }

    @Nested
    @DisplayName("getGobackUrl")
    class GetGobackUrl {

        @Test
        @DisplayName("문서 목록 페이지 URL 반환 (fileKey 쿼리 파라미터 포함)")
        void shouldReturnDocumentListUrl() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";

            // when
            String result = urlManager.getGobackUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/?fileKey=550e8400-e29b-41d4-a716-446655440000");
        }

        @Test
        @DisplayName("각 fileKey마다 고유한 URL 반환")
        void shouldReturnDifferentUrlForDifferentFileKeys() {
            // when
            String result1 = urlManager.getGobackUrl("abc-123");
            String result2 = urlManager.getGobackUrl("def-456");

            // then
            assertThat(result1).isEqualTo("http://localhost:8080/?fileKey=abc-123");
            assertThat(result2).isEqualTo("http://localhost:8080/?fileKey=def-456");
            assertThat(result1).isNotEqualTo(result2);
        }
    }
}
