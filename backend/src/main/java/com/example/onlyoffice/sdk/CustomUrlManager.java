package com.example.onlyoffice.sdk;

import com.example.onlyoffice.config.OnlyOfficeProperties;
import com.example.onlyoffice.service.MinioStorageService;
import com.onlyoffice.manager.document.DocumentManager;
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

    private final OnlyOfficeProperties onlyOfficeProperties;
    private final MinioStorageService minioStorageService;
    private final DocumentManager documentManager;

    public CustomUrlManager(
            SettingsManager settingsManager,
            OnlyOfficeProperties onlyOfficeProperties,
            MinioStorageService minioStorageService,
            DocumentManager documentManager) {
        super(settingsManager);
        this.onlyOfficeProperties = onlyOfficeProperties;
        this.minioStorageService = minioStorageService;
        this.documentManager = documentManager;
    }

    @Override
    public String getFileUrl(String fileId) {
        // fileId is now fileKey (UUID)
        // Check if presigned URLs are enabled
        if (onlyOfficeProperties.isUsePresignedUrls()) {
            return getPresignedFileUrl(fileId);
        }

        // Default: Backend proxy URL
        return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                .path("/files/{fileKey}")
                .buildAndExpand(fileId)
                .encode()
                .toUriString();
    }

    /**
     * Generate MinIO presigned URL for file access
     *
     * Constructs the MinIO object path and generates a presigned URL
     * that is accessible from ONLYOFFICE Document Server.
     *
     * @param fileKey The file key identifying the document
     * @return Presigned URL valid for 1 hour
     */
    private String getPresignedFileUrl(String fileKey) {
        try {
            // Get filename from DocumentManager
            String filename = documentManager.getDocumentName(fileKey);

            // Construct MinIO object path: documents/{fileKey}/{filename}
            String objectPath = String.format("documents/%s/%s", fileKey, filename);

            // Generate presigned URL
            String presignedUrl = minioStorageService.generatePresignedUrl(objectPath);

            // Replace localhost endpoint with docker-accessible endpoint
            String externalUrl = replaceMinioEndpoint(presignedUrl);

            log.debug("Generated presigned URL for fileKey={}: {}", fileKey, externalUrl);
            return externalUrl;

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for fileKey={}, falling back to proxy", fileKey, e);
            // Fallback to backend proxy on error
            return UriComponentsBuilder.fromHttpUrl(serverBaseUrl)
                    .path("/files/{fileKey}")
                    .buildAndExpand(fileKey)
                    .encode()
                    .toUriString();
        }
    }

    /**
     * Replace localhost MinIO endpoint with docker-accessible endpoint
     *
     * MinioStorageService generates URLs with minio.endpoint (http://localhost:9000)
     * which is not accessible from ONLYOFFICE Document Server container.
     * Replace with minio-external-endpoint (http://minio:9000) for internal access.
     *
     * @param presignedUrl Original presigned URL with localhost endpoint
     * @return Modified URL with docker-accessible endpoint
     */
    private String replaceMinioEndpoint(String presignedUrl) {
        String externalEndpoint = onlyOfficeProperties.getMinioExternalEndpoint();

        // Handle common localhost variations
        if (presignedUrl.startsWith("http://localhost:9000")) {
            return presignedUrl.replace("http://localhost:9000", externalEndpoint);
        } else if (presignedUrl.startsWith("http://127.0.0.1:9000")) {
            return presignedUrl.replace("http://127.0.0.1:9000", externalEndpoint);
        }

        // Return unchanged if no localhost pattern found
        return presignedUrl;
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
