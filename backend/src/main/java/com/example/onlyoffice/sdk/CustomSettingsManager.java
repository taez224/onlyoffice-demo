package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.settings.DefaultSettingsManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom implementation of ONLYOFFICE SettingsManager
 * Extends DefaultSettingsManager to leverage SDK's configuration features
 *
 * Settings are initialized once in @PostConstruct from application.yml values,
 * then stored in a Map for efficient retrieval.
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

    /**
     * Initialize ONLYOFFICE settings from application.yml
     * Called after dependency injection is complete
     */
    @PostConstruct
    public void init() {
        // Document Server settings
        setSetting("files.docservice.url.site", documentServerUrl);
        setSetting("files.docservice.url.api", documentServerUrl + "/web-apps/apps/api/documents/api.js");
        setSetting("files.docservice.url.preloader", documentServerUrl + "/web-apps/apps/api/documents/cache-scripts.html");

        // Security settings
        setSetting("files.docservice.secret", jwtSecret);
        setSetting("files.docservice.secret.enable", "true");
        setSetting("files.docservice.secret.header", "Authorization");

        log.info("ONLYOFFICE settings initialized: documentServerUrl={}, serverBaseUrl={}",
                documentServerUrl, serverBaseUrl);
    }

    @Override
    public String getSetting(String name) {
        return settings.get(name);
    }

    @Override
    public void setSetting(String name, String value) {
        settings.put(name, value);
        log.debug("Setting updated: {} = {}", name, value);
    }
}
