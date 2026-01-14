package com.example.onlyoffice.sdk;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
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

    private final DocumentRepository documentRepository;

    public CustomDocumentManager(SettingsManager settingsManager,
                                 DocumentRepository documentRepository) {
        super(settingsManager);
        this.documentRepository = documentRepository;
    }

    @Override
    public String getDocumentKey(String fileId, boolean embedded) {
        // fileId is now fileKey (UUID)
        // Look up Document and return fileKey_v{version} format
        return documentRepository.findByFileKey(fileId)
                .map(doc -> KeyUtils.generateEditorKey(doc.getFileKey(), doc.getEditorVersion()))
                .orElseThrow(() -> new DocumentNotFoundException("fileKey: " + fileId));
    }

    @Override
    public String getDocumentName(String fileId) {
        // fileId is now fileKey (UUID)
        // Return original fileName from Document
        return documentRepository.findByFileKey(fileId)
                .map(Document::getFileName)
                .orElseThrow(() -> new DocumentNotFoundException("fileKey: " + fileId));
    }
}
