package com.example.onlyoffice.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomSettingsManager")
class CustomSettingsManagerTest {

    private CustomSettingsManager settingsManager;

    @BeforeEach
    void setUp() {
        settingsManager = new CustomSettingsManager();
        // Set test values using reflection
        ReflectionTestUtils.setField(settingsManager, "documentServerUrl", "http://localhost:9980");
        ReflectionTestUtils.setField(settingsManager, "jwtSecret", "test-secret-key-32-chars-long-min");
        ReflectionTestUtils.setField(settingsManager, "serverBaseUrl", "http://localhost:8080");

        // Manually call @PostConstruct method to initialize settings
        settingsManager.init();
    }

    @Nested
    @DisplayName("getSetting")
    class GetSetting {

        @Test
        @DisplayName("Document Server URL 설정 반환")
        void shouldReturnDocumentServerUrl() {
            // when
            String result = settingsManager.getSetting("files.docservice.url.site");

            // then
            assertThat(result).isEqualTo("http://localhost:9980");
        }

        @Test
        @DisplayName("Document Server API URL 설정 반환")
        void shouldReturnDocumentServerApiUrl() {
            // when
            String result = settingsManager.getSetting("files.docservice.url.api");

            // then
            assertThat(result).isEqualTo("http://localhost:9980/web-apps/apps/api/documents/api.js");
        }

        @Test
        @DisplayName("Document Server Preloader URL 설정 반환")
        void shouldReturnDocumentServerPreloaderUrl() {
            // when
            String result = settingsManager.getSetting("files.docservice.url.preloader");

            // then
            assertThat(result).isEqualTo("http://localhost:9980/web-apps/apps/api/documents/cache-scripts.html");
        }

        @Test
        @DisplayName("JWT Secret 설정 반환")
        void shouldReturnJwtSecret() {
            // when
            String result = settingsManager.getSetting("files.docservice.secret");

            // then
            assertThat(result).isEqualTo("test-secret-key-32-chars-long-min");
        }

        @Test
        @DisplayName("JWT 활성화 설정 반환")
        void shouldReturnJwtEnabled() {
            // when
            String result = settingsManager.getSetting("files.docservice.secret.enable");

            // then
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("JWT 헤더 설정 반환")
        void shouldReturnJwtHeader() {
            // when
            String result = settingsManager.getSetting("files.docservice.secret.header");

            // then
            assertThat(result).isEqualTo("Authorization");
        }

        @Test
        @DisplayName("알 수 없는 설정은 null 반환")
        void shouldReturnNullForUnknownSetting() {
            // when
            String result = settingsManager.getSetting("unknown.setting");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("setSetting")
    class SetSetting {

        @Test
        @DisplayName("런타임 설정 저장 및 조회")
        void shouldStoreAndRetrieveRuntimeSetting() {
            // given
            String settingName = "custom.setting";
            String settingValue = "custom-value";

            // when
            settingsManager.setSetting(settingName, settingValue);
            String result = settingsManager.getSetting(settingName);

            // then
            assertThat(result).isEqualTo(settingValue);
        }

        @Test
        @DisplayName("런타임 설정이 application.yml 설정보다 우선")
        void shouldPrioritizeRuntimeSettingOverApplicationSetting() {
            // given
            String settingName = "files.docservice.url.site";
            String overrideValue = "http://override:9980";

            // when
            settingsManager.setSetting(settingName, overrideValue);
            String result = settingsManager.getSetting(settingName);

            // then
            assertThat(result).isEqualTo(overrideValue);
        }
    }
}