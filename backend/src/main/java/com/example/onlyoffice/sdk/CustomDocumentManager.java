package com.example.onlyoffice.sdk;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.service.DocumentService;
import com.onlyoffice.manager.document.DefaultDocumentManager;
import com.onlyoffice.manager.settings.SettingsManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom implementation of ONLYOFFICE DocumentManager
 * Extends DefaultDocumentManager to leverage SDK's format database and features
 * <p>
 * Only implements required abstract methods:
 * - getDocumentKey(): fileId (UUID fileKey)로 Document 조회 후 fileKey_v{version} 형식의 key 반환
 * - getDocumentName(): fileId (UUID fileKey)로 Document 조회 후 원본 fileName 반환
 * <p>
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

    private final DocumentService documentService;

    public CustomDocumentManager(SettingsManager settingsManager,
                                 DocumentService documentService) {
        super(settingsManager);
        this.documentService = documentService;
    }

    @Override
    public String getDocumentKey(String fileId, boolean embedded) {
        // fileId is now fileKey (UUID)
        // Look up Document and return fileKey_v{version} format
        return documentService.getEditorKeyByFileKey(fileId);
    }

    @Override
    public String getDocumentName(String fileId) {
        // fileId is now fileKey (UUID)
        // Return original fileName from Document
        return documentService.findByFileKey(fileId)
                .map(Document::getFileName)
                .orElse(fileId); // fallback to fileId if not found
    }
}
