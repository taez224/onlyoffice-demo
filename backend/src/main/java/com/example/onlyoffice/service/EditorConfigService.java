package com.example.onlyoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.model.documenteditor.config.document.Type;
import com.onlyoffice.service.documenteditor.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Editor Configuration Service
 * Wraps ONLYOFFICE SDK ConfigService for creating editor configurations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EditorConfigService {

    private final ConfigService sdkConfigService;
    private final ObjectMapper objectMapper;

    @Value("${onlyoffice.url}")
    private String onlyofficeUrl;

    /**
     * Create editor configuration for a file by fileKey
     *
     * @param fileKey The file's unique identifier (UUID)
     * @return Editor configuration response with config and documentServerUrl
     */
    public Map<String, Object> createEditorResponseByFileKey(String fileKey) {
        // SDK ConfigService expects fileId parameter
        // We pass fileKey as fileId
        Config config = sdkConfigService.createConfig(fileKey, Mode.EDIT, Type.DESKTOP);

        // Convert Config object to Map for JSON response
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = objectMapper.convertValue(config, Map.class);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("config", configMap);
        response.put("documentServerUrl", onlyofficeUrl);

        log.info("Editor config created for fileKey: {}, document.key: {}",
            fileKey, config.getDocument().getKey());

        return response;
    }

    /**
     * Create editor configuration for a file
     *
     * @deprecated Use {@link #createEditorResponseByFileKey(String)} instead
     * @param fileName The file name
     * @return Editor configuration response with config and documentServerUrl
     */
    @Deprecated
    public Map<String, Object> createEditorResponse(String fileName) {
        // Use SDK ConfigService to create type-safe Config object
        Config config = sdkConfigService.createConfig(fileName, Mode.EDIT, Type.DESKTOP);

        // Convert Config object to Map for JSON response
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = objectMapper.convertValue(config, Map.class);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("config", configMap);
        response.put("documentServerUrl", onlyofficeUrl);

        log.info("Editor config created for file: {}, key: {}", fileName, config.getDocument().getKey());

        return response;
    }
}
