package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.DefaultUrlManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Custom implementation of ONLYOFFICE UrlManager
 * Extends DefaultUrlManager to leverage SDK's URL management features
 * <p>
 * Overrides required methods for application-specific URLs:
 * - getFileUrl(): Returns file download URL with fileKey (UUID) path variable
 * - getCallbackUrl(): Returns callback URL with fileKey (UUID) query parameter
 * - getGobackUrl(): Returns redirect URL with fileKey (UUID) query parameter
 * <p>
 * Inherited features from DefaultUrlManager:
 * - getDocumentServerUrl(): Loads from SettingsManager
 * - getInnerDocumentServerUrl(): Supports Docker internal URLs
 * - getDocumentServerApiUrl(): Constructs API endpoint URLs
 * - getServiceUrl(): Reflection-based service URL lookup
 * - sanitizeUrl(), replaceToDocumentServerUrl(): URL manipulation utilities
 */
@Slf4j
@Component
public class CustomUrlManager extends DefaultUrlManager {

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    public CustomUrlManager(SettingsManager settingsManager) {
        super(settingsManager);
    }

    @Override
    public String getFileUrl(String fileId) {
        // fileId is now fileKey (UUID)
        return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                .path("/files/{fileKey}")
                .buildAndExpand(fileId)
                .encode()
                .toUriString();
    }

    @Override
    public String getCallbackUrl(String fileId) {
        // fileId is now fileKey (UUID)
        return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                .path("/callback")
                .queryParam("fileKey", fileId)
                .encode()
                .toUriString();
    }

    @Override
    public String getGobackUrl(String fileId) {
        // fileId is now fileKey (UUID)
        // Redirect to frontend with fileKey parameter
        return serverBaseUrl + "/?fileKey=" + fileId;
    }

}
