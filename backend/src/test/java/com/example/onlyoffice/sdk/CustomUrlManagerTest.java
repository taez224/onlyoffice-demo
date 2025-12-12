package com.example.onlyoffice.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomUrlManager")
class CustomUrlManagerTest {

    private CustomUrlManager urlManager;
    private CustomSettingsManager settingsManager;

    private static final String SERVER_BASE_URL = "http://localhost:8080";
    private static final String DOCUMENT_SERVER_URL = "http://localhost:9980";

    @BeforeEach
    void setUp() {
        // Use real CustomSettingsManager for integration testing
        settingsManager = new CustomSettingsManager();
        ReflectionTestUtils.setField(settingsManager, "documentServerUrl", DOCUMENT_SERVER_URL);
        ReflectionTestUtils.setField(settingsManager, "jwtSecret", "test-secret");
        ReflectionTestUtils.setField(settingsManager, "serverBaseUrl", SERVER_BASE_URL);

        urlManager = new CustomUrlManager(settingsManager);

        // Set serverBaseUrl using reflection
        ReflectionTestUtils.setField(urlManager, "serverBaseUrl", SERVER_BASE_URL);
    }

    @Nested
    @DisplayName("getFileUrl")
    class GetFileUrl {

        @Test
        @DisplayName("파일 다운로드 URL 생성 (fileKey 기반)")
        void shouldGenerateFileDownloadUrl() {
            // given
            String fileKey = "550e8400-e29b-41d4-a716-446655440000";

            // when
            String result = urlManager.getFileUrl(fileKey);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/550e8400-e29b-41d4-a716-446655440000");
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
