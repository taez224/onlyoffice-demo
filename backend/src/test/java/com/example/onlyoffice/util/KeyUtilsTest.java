package com.example.onlyoffice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KeyUtils")
class KeyUtilsTest {

    @Nested
    @DisplayName("generateEditorKey")
    class GenerateEditorKey {

        @Test
        @DisplayName("정상적인 fileKey와 version으로 key 생성")
        void shouldGenerateKeyFromFileKeyAndVersion() {
            // given
            String fileKey = "doc123";
            int version = 5;

            // when
            String result = KeyUtils.generateEditorKey(fileKey, version);

            // then
            assertThat(result).isEqualTo("doc123_v5");
        }

        @Test
        @DisplayName("생성된 key는 128자 이하")
        void shouldGenerateKeyWithinMaxLength() {
            // given
            String longFileKey = "a".repeat(200);
            int version = 999;

            // when
            String result = KeyUtils.generateEditorKey(longFileKey, version);

            // then
            assertThat(result.length()).isLessThanOrEqualTo(KeyUtils.MAX_KEY_LENGTH);
        }

        @Test
        @DisplayName("null fileKey는 예외 발생")
        void shouldThrowExceptionForNullFileKey() {
            assertThatThrownBy(() -> KeyUtils.generateEditorKey(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileKey cannot be null");
        }

        @Test
        @DisplayName("빈 fileKey는 예외 발생")
        void shouldThrowExceptionForBlankFileKey() {
            assertThatThrownBy(() -> KeyUtils.generateEditorKey("  ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("sanitize")
    class Sanitize {

        @Test
        @DisplayName("특수문자 제거")
        void shouldRemoveSpecialCharacters() {
            // given
            String input = "file@name#with$special%chars!";

            // when
            String result = KeyUtils.sanitize(input);

            // then
            assertThat(result).isEqualTo("filenamewithspecialchars");
        }

        @Test
        @DisplayName("허용된 문자는 유지")
        void shouldKeepAllowedCharacters() {
            // given
            String input = "file_name-123";

            // when
            String result = KeyUtils.sanitize(input);

            // then
            assertThat(result).isEqualTo("file_name-123");
        }

        @Test
        @DisplayName("null 입력시 빈 문자열 반환")
        void shouldReturnEmptyStringForNull() {
            assertThat(KeyUtils.sanitize(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isValidKey")
    class IsValidKey {

        @Test
        @DisplayName("유효한 key는 true 반환")
        void shouldReturnTrueForValidKey() {
            assertThat(KeyUtils.isValidKey("doc123_v5")).isTrue();
            assertThat(KeyUtils.isValidKey("file-name_v1")).isTrue();
        }

        @Test
        @DisplayName("null은 false 반환")
        void shouldReturnFalseForNull() {
            assertThat(KeyUtils.isValidKey(null)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 false 반환")
        void shouldReturnFalseForBlank() {
            assertThat(KeyUtils.isValidKey("")).isFalse();
            assertThat(KeyUtils.isValidKey("  ")).isFalse();
        }

        @Test
        @DisplayName("128자 초과는 false 반환")
        void shouldReturnFalseForTooLongKey() {
            String longKey = "a".repeat(129);
            assertThat(KeyUtils.isValidKey(longKey)).isFalse();
        }

        @Test
        @DisplayName("특수문자 포함시 false 반환")
        void shouldReturnFalseForSpecialCharacters() {
            assertThat(KeyUtils.isValidKey("doc@123")).isFalse();
            assertThat(KeyUtils.isValidKey("file#name")).isFalse();
        }
    }

    @Nested
    @DisplayName("generateFileKey")
    class GenerateFileKey {

        @Test
        @DisplayName("파일명과 타임스탬프로 fileKey 생성")
        void shouldGenerateFileKeyFromFileNameAndTimestamp() {
            // given
            String fileName = "document.docx";
            long timestamp = 1234567890L;

            // when
            String result = KeyUtils.generateFileKey(fileName, timestamp);

            // then
            assertThat(result).isEqualTo("documentdocx_1234567890");
        }

        @Test
        @DisplayName("긴 파일명은 해시로 축약")
        void shouldHashLongFileName() {
            // given
            String longFileName = "a".repeat(200) + ".docx";
            long timestamp = 1234567890L;

            // when
            String result = KeyUtils.generateFileKey(longFileName, timestamp);

            // then
            assertThat(result.length()).isLessThanOrEqualTo(KeyUtils.MAX_KEY_LENGTH);
            assertThat(result).contains("_1234567890");
        }
    }
}
