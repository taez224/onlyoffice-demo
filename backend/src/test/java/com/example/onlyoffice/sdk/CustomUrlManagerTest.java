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
        @DisplayName("파일 다운로드 URL 생성")
        void shouldGenerateFileDownloadUrl() {
            // given
            String fileId = "sample.docx";

            // when
            String result = urlManager.getFileUrl(fileId);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/sample.docx");
        }

        @Test
        @DisplayName("공백이 포함된 파일명 URL 인코딩")
        void shouldEncodeFileNameWithSpaces() {
            // given
            String fileId = "my file.docx";

            // when
            String result = urlManager.getFileUrl(fileId);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/files/my%20file.docx");
        }
    }

    @Nested
    @DisplayName("getCallbackUrl")
    class GetCallbackUrl {

        @Test
        @DisplayName("콜백 URL 생성 (fileName 쿼리 파라미터 포함)")
        void shouldGenerateCallbackUrlWithFileNameParam() {
            // given
            String fileId = "sample.docx";

            // when
            String result = urlManager.getCallbackUrl(fileId);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/callback?fileName=sample.docx");
        }

        @Test
        @DisplayName("공백이 포함된 파일명 쿼리 파라미터 인코딩")
        void shouldEncodeSpacesInQueryParam() {
            // given
            String fileId = "my file.docx";

            // when
            String result = urlManager.getCallbackUrl(fileId);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/callback?fileName=my%20file.docx");
        }
    }

    @Nested
    @DisplayName("getGobackUrl")
    class GetGobackUrl {

        @Test
        @DisplayName("문서 목록 페이지 URL 반환")
        void shouldReturnDocumentListUrl() {
            // given
            String fileId = "sample.docx";

            // when
            String result = urlManager.getGobackUrl(fileId);

            // then
            assertThat(result).isEqualTo("http://localhost:8080/");
        }

        @Test
        @DisplayName("fileId와 무관하게 동일한 URL 반환")
        void shouldReturnSameUrlRegardlessOfFileId() {
            // when
            String result1 = urlManager.getGobackUrl("file1.docx");
            String result2 = urlManager.getGobackUrl("file2.xlsx");

            // then
            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("getCreateUrl")
    class GetCreateUrl {

        @Test
        @DisplayName("구현되지 않음 - null 반환")
        void shouldReturnNull() {
            // given
            String fileId = "sample.docx";

            // when
            String result = urlManager.getCreateUrl(fileId);

            // then
            assertThat(result).isNull();
        }
    }

}
