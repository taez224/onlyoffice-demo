package com.example.onlyoffice.config;

import com.example.onlyoffice.sdk.CustomSettingsManager;
import com.onlyoffice.manager.security.DefaultJwtManager;
import com.onlyoffice.manager.security.JwtManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ONLYOFFICE SDK Configuration
 * Configures SDK Services as Spring Beans
 * <p>
 * Note: Custom Managers and Services are already registered as Spring beans via @Component:
 * - CustomSettingsManager
 * - CustomDocumentManager
 * - CustomUrlManager
 * - CustomConfigService (extends DefaultConfigService with Permission & User support)
 * - CustomPermissionManager
 * - CustomUserManager
 * - CustomCallbackService (extends DefaultCallbackService)
 * <p>
 * They are injected directly into @Bean methods as parameters.
 */
@Slf4j
@Configuration
public class OnlyOfficeConfig {

    /**
     * JwtManager Bean
     * Handles JWT token creation and validation
     */
    @Bean
    public JwtManager jwtManager(CustomSettingsManager settingsManager) {
        log.info("Initializing ONLYOFFICE JwtManager");
        return new DefaultJwtManager(settingsManager);
    }

    // Note: CallbackService is provided by CustomCallbackService (@Component)
    // Note: ConfigService is provided by CustomConfigService (@Component)
    // No need to define it here - Spring auto-wires it
}
