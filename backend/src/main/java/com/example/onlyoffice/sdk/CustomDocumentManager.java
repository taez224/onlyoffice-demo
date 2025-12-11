package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.document.DefaultDocumentManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.example.onlyoffice.util.KeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom implementation of ONLYOFFICE DocumentManager
 * Extends DefaultDocumentManager to leverage SDK's format database and features
 *
 * Only implements required abstract methods:
 * - getDocumentKey(): Uses KeyUtils for consistent document key generation
 * - getDocumentName(): Returns the fileId as-is (fileId = fileName in our implementation)
 *
 * Inherited features from DefaultDocumentManager:
 * - Format database (JSON-based format definitions)
 * - Blank file templates (getNewBlankFile)
 * - File type detection (isEditable, isViewable, isFillable)
 * - Conversion support (getConvertExtensionList, getLossyEditableMap)
 * - Extension utilities (getExtension, getBaseName, getDocumentType)
 */
@Slf4j
@Component
public class CustomDocumentManager extends DefaultDocumentManager {

    public CustomDocumentManager(SettingsManager settingsManager) {
        super(settingsManager);
    }

    @Override
    public String getDocumentKey(String fileId, boolean embedded) {
        // Use KeyUtils for consistent document key generation
        // Note: version is managed by DocumentService, here we use fileId as-is
        return KeyUtils.sanitize(fileId);
    }

    @Override
    public String getDocumentName(String fileId) {
        // FileId is the filename in our implementation
        return fileId;
    }
}
