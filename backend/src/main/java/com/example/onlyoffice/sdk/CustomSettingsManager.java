package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.model.properties.DocsIntegrationSdkProperties;
import com.onlyoffice.model.settings.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom implementation of ONLYOFFICE SettingsManager
 * Provides settings from application.yml to the SDK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomSettingsManager implements SettingsManager {

    @Value("${onlyoffice.url}")
    private String documentServerUrl;

    @Value("${onlyoffice.secret}")
    private String jwtSecret;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    private final Map<String, String> settings = new HashMap<>();

    @Override
    public String getSetting(String name) {
        return switch (name) {
            // Document Server settings
            case "files.docservice.url.site" -> documentServerUrl;
            case "files.docservice.url.api" -> documentServerUrl + "/web-apps/apps/api/documents/api.js";

            // Security settings
            case "files.docservice.secret" -> jwtSecret;
            case "files.docservice.secret.enable" -> "true";
            case "files.docservice.secret.header" -> "Authorization";

            // Application URL
            case "files.docservice.url.preloader" -> documentServerUrl + "/web-apps/apps/api/documents/cache-scripts.html";

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

    @Override
    public void setSettings(Settings settings)
            throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Not implemented - read-only configuration
        throw new UnsupportedOperationException("Settings are read-only from application.yml");
    }

    @Override
    public Map<String, String> getSettings()
            throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        Map<String, String> allSettings = new HashMap<>();
        allSettings.put("files.docservice.url.site", documentServerUrl);
        allSettings.put("files.docservice.secret", jwtSecret);
        allSettings.put("files.docservice.secret.enable", "true");
        return allSettings;
    }

    @Override
    public Boolean getSettingBoolean(String name, Boolean defaultValue) {
        String value = getSetting(name);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public DocsIntegrationSdkProperties getDocsIntegrationSdkProperties() {
        // Return null - not using SDK properties file
        return null;
    }

    @Override
    public Boolean isSecurityEnabled() {
        return jwtSecret != null && !jwtSecret.isBlank();
    }

    @Override
    public Boolean isIgnoreSSLCertificate() {
        return false; // Always validate SSL in production
    }

    @Override
    public String getSecurityKey() {
        return jwtSecret;
    }

    @Override
    public String getSecurityHeader() {
        return "Authorization";
    }

    @Override
    public String getSecurityPrefix() {
        return "Bearer";
    }

    @Override
    public Boolean enableDemo() {
        return false; // Demo mode disabled
    }

    @Override
    public void disableDemo() {
        // Already disabled
    }

    @Override
    public Boolean isDemoActive() {
        return false;
    }

    @Override
    public Boolean isDemoAvailable() {
        return false;
    }
}
