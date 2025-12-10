package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.settings.DefaultSettingsManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom implementation of ONLYOFFICE SettingsManager
 * Extends DefaultSettingsManager to leverage SDK's configuration features
 *
 * Only implements required abstract methods:
 * - getSetting(): Provides settings from application.yml to SDK
 * - setSetting(): Stores runtime settings (read-only in our implementation)
 *
 * Inherited features from DefaultSettingsManager:
 * - SDK properties loading (getDocsIntegrationSdkProperties)
 * - Security settings (isSecurityEnabled, getSecurityKey, getSecurityHeader, getSecurityPrefix)
 * - Demo mode management (enableDemo, disableDemo, isDemoActive, isDemoAvailable)
 * - Settings conversion (setSettings, getSettings)
 * - Type conversion helpers (getSettingBoolean, getSettingInteger)
 */
@Slf4j
@Component
public class CustomSettingsManager extends DefaultSettingsManager {

    @Value("${onlyoffice.url}")
    private String documentServerUrl;

    @Value("${onlyoffice.secret}")
    private String jwtSecret;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    private final Map<String, String> settings = new HashMap<>();

    @Override
    public String getSetting(String name) {
        // First check runtime settings
        if (settings.containsKey(name)) {
            return settings.get(name);
        }

        // Then provide settings from application.yml
        return switch (name) {
            // Document Server settings
            case "files.docservice.url.site" -> documentServerUrl;
            case "files.docservice.url.api" -> documentServerUrl + "/web-apps/apps/api/documents/api.js";
            case "files.docservice.url.preloader" -> documentServerUrl + "/web-apps/apps/api/documents/cache-scripts.html";

            // Security settings
            case "files.docservice.secret" -> jwtSecret;
            case "files.docservice.secret.enable" -> "true";
            case "files.docservice.secret.header" -> "Authorization";

            default -> {
                log.warn("Unknown setting requested: {}", name);
                yield null;
            }
        };
    }

    @Override
    public void setSetting(String name, String value) {
        settings.put(name, value);
        log.debug("Setting updated: {} = {}", name, value);
    }
}
