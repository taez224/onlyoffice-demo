package com.example.onlyoffice.sdk;

import com.onlyoffice.model.settings.SettingsConstants;
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

        // Set test values using reflection (inject @Value fields)
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
        @DisplayName("Document Server URL 설정 반환 (SDK SettingsConstants.URL)")
        void shouldReturnDocumentServerUrl() {
            // when
            String result = settingsManager.getSetting(SettingsConstants.URL);

            // then
            assertThat(result).isEqualTo("http://localhost:9980");
        }

        @Test
        @DisplayName("JWT Secret 설정 반환 (SDK SettingsConstants.SECURITY_KEY)")
        void shouldReturnJwtSecret() {
            // when
            String result = settingsManager.getSetting(SettingsConstants.SECURITY_KEY);

            // then
            assertThat(result).isEqualTo("test-secret-key-32-chars-long-min");
        }


        @Test
        @DisplayName("JWT 헤더 설정 반환 (SDK SettingsConstants.SECURITY_HEADER)")
        void shouldReturnJwtHeader() {
            // when
            String result = settingsManager.getSetting(SettingsConstants.SECURITY_HEADER);

            // then
            assertThat(result).isEqualTo("Authorization");
        }

        @Test
        @DisplayName("JWT 프리픽스 설정 반환 (SDK SettingsConstants.SECURITY_PREFIX)")
        void shouldReturnJwtPrefix() {
            // when
            String result = settingsManager.getSetting(SettingsConstants.SECURITY_PREFIX);

            // then
            assertThat(result).isEqualTo("Bearer "); // SDK default includes trailing space
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
    }

    @Nested
    @DisplayName("Security Settings (SDK)")
    class SecuritySettings {

        @Test
        @DisplayName("isSecurityEnabled: SECURITY_KEY가 설정되어 있으면 true 반환")
        void shouldReturnTrueWhenSecurityKeyIsSet() {
            // when
            boolean result = settingsManager.isSecurityEnabled();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("getSecurityKey: SECURITY_KEY 값 반환")
        void shouldReturnSecurityKey() {
            // when
            String result = settingsManager.getSecurityKey();

            // then
            assertThat(result).isEqualTo("test-secret-key-32-chars-long-min");
        }

        @Test
        @DisplayName("getSecurityHeader: 기본값 'Authorization' 반환")
        void shouldReturnSecurityHeader() {
            // when
            String result = settingsManager.getSecurityHeader();

            // then
            assertThat(result).isEqualTo("Authorization");
        }

        @Test
        @DisplayName("getSecurityPrefix: SDK 기본값 'Bearer ' 반환 (뒤에 공백 포함)")
        void shouldReturnSecurityPrefix() {
            // when
            String result = settingsManager.getSecurityPrefix();

            // then
            assertThat(result).isEqualTo("Bearer "); // SDK default includes trailing space
        }
    }
}