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
    @DisplayName("isValidFileKey")
    class IsValidFileKey {

        @Test
        @DisplayName("유효한 UUID는 true 반환")
        void shouldReturnTrueForValidUuid() {
            assertThat(KeyUtils.isValidFileKey("550e8400-e29b-41d4-a716-446655440000")).isTrue();
            assertThat(KeyUtils.isValidFileKey("a1b2c3d4-e5f6-7890-abcd-ef1234567890")).isTrue();
        }

        @Test
        @DisplayName("대문자 UUID는 false 반환")
        void shouldReturnFalseForUppercaseUuid() {
            assertThat(KeyUtils.isValidFileKey("550E8400-E29B-41D4-A716-446655440000")).isFalse();
            assertThat(KeyUtils.isValidFileKey("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")).isFalse();
        }

        @Test
        @DisplayName("null은 false 반환")
        void shouldReturnFalseForNull() {
            assertThat(KeyUtils.isValidFileKey(null)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 false 반환")
        void shouldReturnFalseForBlank() {
            assertThat(KeyUtils.isValidFileKey("")).isFalse();
            assertThat(KeyUtils.isValidFileKey("  ")).isFalse();
        }

        @Test
        @DisplayName("잘못된 형식은 false 반환")
        void shouldReturnFalseForInvalidFormat() {
            assertThat(KeyUtils.isValidFileKey("not-a-uuid")).isFalse();
            assertThat(KeyUtils.isValidFileKey("doc123_v5")).isFalse();
            assertThat(KeyUtils.isValidFileKey("550e8400e29b41d4a716446655440000")).isFalse(); // 하이픈 없음
        }

        @Test
        @DisplayName("generateFileKey()로 생성된 키는 유효")
        void shouldReturnTrueForGeneratedFileKey() {
            String generatedKey = KeyUtils.generateFileKey();
            assertThat(KeyUtils.isValidFileKey(generatedKey)).isTrue();
        }
    }
}
