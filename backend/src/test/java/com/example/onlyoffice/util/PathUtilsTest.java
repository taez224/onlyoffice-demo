package com.example.onlyoffice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathUtils")
class PathUtilsTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("validateAndResolve")
    class ValidateAndResolve {

        @Test
        @DisplayName("정상적인 상대 경로는 해석된 절대 경로 반환")
        void shouldResolveValidRelativePath() {
            // given
            String relativePath = "documents/file.docx";

            // when
            Path result = PathUtils.validateAndResolve(tempDir, relativePath);

            // then
            assertThat(result).isEqualTo(tempDir.resolve("documents/file.docx").normalize());
            assertThat(result.startsWith(tempDir)).isTrue();
        }

        @Test
        @DisplayName("단순 파일명은 정상 해석")
        void shouldResolveSimpleFileName() {
            // given
            String fileName = "test.docx";

            // when
            Path result = PathUtils.validateAndResolve(tempDir, fileName);

            // then
            assertThat(result).isEqualTo(tempDir.resolve("test.docx"));
        }

        @Test
        @DisplayName("../ 경로 탈출 시도는 SecurityException 발생")
        void shouldThrowExceptionForParentDirectoryTraversal() {
            // given
            String maliciousPath = "../../../etc/passwd";

            // when & then
            assertThatThrownBy(() -> PathUtils.validateAndResolve(tempDir, maliciousPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");
        }

        @Test
        @DisplayName("../ 중첩 경로 탈출 시도는 SecurityException 발생")
        void shouldThrowExceptionForNestedTraversal() {
            // given
            String maliciousPath = "subdir/../../secret.txt";

            // when & then
            assertThatThrownBy(() -> PathUtils.validateAndResolve(tempDir, maliciousPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");
        }

        @Test
        @DisplayName("절대 경로로 탈출 시도는 SecurityException 발생")
        void shouldThrowExceptionForAbsolutePathEscape() {
            // given
            String absolutePath = "/etc/passwd";

            // when & then
            assertThatThrownBy(() -> PathUtils.validateAndResolve(tempDir, absolutePath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");
        }

        @Test
        @DisplayName("하위 디렉토리 내 ../ 는 rootLocation 내에 있으면 허용")
        void shouldAllowTraversalWithinRoot() {
            // given
            String path = "subdir/../file.docx";

            // when
            Path result = PathUtils.validateAndResolve(tempDir, path);

            // then
            assertThat(result).isEqualTo(tempDir.resolve("file.docx"));
            assertThat(result.startsWith(tempDir)).isTrue();
        }

        @Test
        @DisplayName("URL 인코딩된 경로 탈출 시도 방어")
        void shouldHandleEncodedTraversal() {
            // given - %2e%2e = ..
            String encodedPath = "%2e%2e/%2e%2e/etc/passwd";

            // when
            Path result = PathUtils.validateAndResolve(tempDir, encodedPath);

            // then - URL 인코딩된 문자는 그대로 파일명으로 처리됨 (보안상 안전)
            assertThat(result.startsWith(tempDir)).isTrue();
        }

        @Test
        @DisplayName("빈 문자열은 rootLocation 자체 반환")
        void shouldReturnRootForEmptyPath() {
            // given
            String emptyPath = "";

            // when
            Path result = PathUtils.validateAndResolve(tempDir, emptyPath);

            // then
            assertThat(result).isEqualTo(tempDir.normalize());
        }
    }
}
