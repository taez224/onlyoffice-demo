package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.url.UrlManager;
import com.onlyoffice.model.common.RequestedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Custom implementation of ONLYOFFICE UrlManager
 * Provides URLs for document server, callbacks, and file operations
 */
@Slf4j
@Component
public class CustomUrlManager implements UrlManager {

    @Value("${onlyoffice.url}")
    private String documentServerUrl;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    @Override
    public String getDocumentServerUrl() {
        return sanitizeUrl(documentServerUrl);
    }

    @Override
    public String getInnerDocumentServerUrl() {
        // No internal URL in our setup
        return getDocumentServerUrl();
    }

    @Override
    public String getDocumentServerApiUrl() {
        return getDocumentServerUrl() + "/web-apps/apps/api/documents/api.js";
    }

    @Override
    public String getDocumentServerApiUrl(String shardKey) {
        if (shardKey == null || shardKey.isBlank()) {
            return getDocumentServerApiUrl();
        }
        return UriComponentsBuilder.fromHttpUrl(getDocumentServerApiUrl())
                .queryParam("shardkey", shardKey)
                .toUriString();
    }

    @Override
    public String getDocumentServerPreloaderApiUrl() {
        return getDocumentServerUrl() + "/web-apps/apps/api/documents/cache-scripts.html";
    }

    @Override
    public String getFileUrl(String fileId) {
        return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                .path("/files/{fileName}")
                .buildAndExpand(fileId)
                .toUriString();
    }

    @Override
    public String getCallbackUrl(String fileId) {
        return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                .path("/callback")
                .queryParam("fileName", fileId)
                .toUriString();
    }

    @Override
    public String getGobackUrl(String fileId) {
        // Return to document list
        return serverBaseUrl + "/";
    }

    @Override
    public String getCreateUrl(String fileId) {
        // Not implemented - return null
        return null;
    }

    @Override
    public String getServiceUrl(RequestedService requestedService) {
        // Not implemented - delegate to SDK
        return null;
    }

    @Override
    public String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        // Remove trailing slash
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public String replaceToDocumentServerUrl(String url) {
        // No replacement needed in our setup
        return url;
    }

    @Override
    public String replaceToInnerDocumentServerUrl(String url) {
        // No replacement needed in our setup
        return url;
    }

    @Override
    public String getTestConvertUrl(String url) {
        return sanitizeUrl(url) + "/test.docx";
    }
}
