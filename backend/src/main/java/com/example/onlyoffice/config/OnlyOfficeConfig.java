package com.example.onlyoffice.config;

import com.example.onlyoffice.sdk.CustomDocumentManager;
import com.example.onlyoffice.sdk.CustomSettingsManager;
import com.example.onlyoffice.sdk.CustomUrlManager;
import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.manager.security.DefaultJwtManager;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.UrlManager;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import com.onlyoffice.service.documenteditor.callback.DefaultCallbackService;
import com.onlyoffice.service.documenteditor.config.ConfigService;
import com.onlyoffice.service.documenteditor.config.DefaultConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ONLYOFFICE SDK Configuration
 * Configures SDK Managers and Services as Spring Beans
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OnlyOfficeConfig {

    private final CustomSettingsManager customSettingsManager;
    private final CustomDocumentManager customDocumentManager;
    private final CustomUrlManager customUrlManager;

    /**
     * SettingsManager Bean
     * Provides application settings to SDK
     */
    @Bean
    public SettingsManager settingsManager() {
        log.info("Initializing ONLYOFFICE SettingsManager");
        return customSettingsManager;
    }

    /**
     * DocumentManager Bean
     * Handles document-related operations
     */
    @Bean
    public DocumentManager documentManager() {
        log.info("Initializing ONLYOFFICE DocumentManager");
        return customDocumentManager;
    }

    /**
     * UrlManager Bean
     * Provides URLs for document server and callbacks
     */
    @Bean
    public UrlManager urlManager() {
        log.info("Initializing ONLYOFFICE UrlManager");
        return customUrlManager;
    }

    /**
     * JwtManager Bean
     * Handles JWT token creation and validation
     */
    @Bean
    public JwtManager jwtManager(SettingsManager settingsManager) {
        log.info("Initializing ONLYOFFICE JwtManager");
        return new DefaultJwtManager(settingsManager);
    }

    /**
     * ConfigService Bean
     * Creates editor configuration objects
     */
    @Bean
    public ConfigService configService(
            DocumentManager documentManager,
            UrlManager urlManager,
            JwtManager jwtManager,
            SettingsManager settingsManager) {
        log.info("Initializing ONLYOFFICE ConfigService");
        return new DefaultConfigService(documentManager, urlManager, jwtManager, settingsManager);
    }

    /**
     * CallbackService Bean
     * Processes callbacks from document server
     */
    @Bean
    public CallbackService callbackService(
            JwtManager jwtManager,
            SettingsManager settingsManager) {
        log.info("Initializing ONLYOFFICE CallbackService");
        return new DefaultCallbackService(jwtManager, settingsManager);
    }
}
