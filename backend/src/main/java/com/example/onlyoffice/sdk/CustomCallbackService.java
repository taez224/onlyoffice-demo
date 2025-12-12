package com.example.onlyoffice.sdk;

import com.example.onlyoffice.service.DocumentService;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.service.documenteditor.callback.DefaultCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom implementation of ONLYOFFICE CallbackService
 * Extends DefaultCallbackService to leverage SDK's callback processing features
 * <p>
 * SDK handles:
 * - JWT validation (verifyCallback)
 * - Status-based routing (processCallback)
 * - Standard error handling
 * <p>
 * Custom implementation focuses on:
 * - Business logic for each callback status
 * - File saving and versioning
 */
@Slf4j
@Component
public class CustomCallbackService extends DefaultCallbackService {

    private final DocumentService documentService;

    public CustomCallbackService(
            JwtManager jwtManager,
            CustomSettingsManager settingsManager,
            DocumentService documentService) {
        super(jwtManager, settingsManager);
        this.documentService = documentService;
    }

    /**
     * Handle SAVE status (status=2)
     * Document editing complete and ready for saving
     * - Save file from download URL
     * - Increment editor version (triggers new document key)
     *
     * @param fileId now represents fileKey (UUID)
     */
    @Override
    public void handlerSave(Callback callback, String fileId) throws Exception {
        String downloadUrl = callback.getUrl();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.error("Download URL is missing for SAVE callback, fileKey: {}", fileId);
            throw new IllegalArgumentException("Download URL is required for SAVE");
        }

        // fileId is now fileKey (UUID)
        documentService.saveDocumentFromUrlByFileKey(downloadUrl, fileId);
        documentService.incrementEditorVersionByFileKey(fileId);
        log.info("Document saved and version incremented for fileKey: {}", fileId);
    }

    /**
     * Handle FORCESAVE status (status=6)
     * Force save during co-editing session
     * - Save file from download URL
     * - Do NOT increment version (co-editing continues)
     *
     * @param fileId now represents fileKey (UUID)
     */
    @Override
    public void handlerForcesave(Callback callback, String fileId) throws Exception {
        String downloadUrl = callback.getUrl();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.error("Download URL is missing for FORCESAVE callback, fileKey: {}", fileId);
            throw new IllegalArgumentException("Download URL is required for FORCESAVE");
        }

        // fileId is now fileKey (UUID)
        documentService.saveDocumentFromUrlByFileKey(downloadUrl, fileId);
        log.info("Force save completed (version unchanged) for fileKey: {}", fileId);
    }

    /**
     * Handle EDITING status (status=1)
     * Document is currently being edited
     */
    @Override
    public void handlerEditing(Callback callback, String fileId) throws Exception {
        log.debug("Document being edited: {}", fileId);
    }

    /**
     * Handle CLOSED status (status=4)
     * Document closed without changes
     */
    @Override
    public void handlerClosed(Callback callback, String fileId) throws Exception {
        log.info("Document closed without changes: {}", fileId);
    }

    /**
     * Handle SAVE_CORRUPTED status (status=3)
     * Document save error occurred
     */
    @Override
    public void handlerSaveCorrupted(Callback callback, String fileId) throws Exception {
        log.error("Document save error, status=SAVE_CORRUPTED, key={}", callback.getKey());
    }

    /**
     * Handle FORCESAVE_CORRUPTED status (status=7)
     * Force save error occurred
     */
    @Override
    public void handlerForcesaveCurrupted(Callback callback, String fileId) throws Exception {
        log.error("Force save error, status=FORCESAVE_CORRUPTED, key={}", callback.getKey());
    }
}
