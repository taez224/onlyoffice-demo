package com.example.onlyoffice.sdk;

import com.example.onlyoffice.service.CallbackQueueService;
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
 * - File saving and versioning with queue-based sequential processing
 * - Pessimistic locking for concurrent callback handling
 */
@Slf4j
@Component
public class CustomCallbackService extends DefaultCallbackService {

    private final DocumentService documentService;
    private final CallbackQueueService callbackQueueService;

    public CustomCallbackService(
            JwtManager jwtManager,
            CustomSettingsManager settingsManager,
            DocumentService documentService,
            CallbackQueueService callbackQueueService) {
        super(jwtManager, settingsManager);
        this.documentService = documentService;
        this.callbackQueueService = callbackQueueService;
    }

    /**
     * Handle SAVE status (status=2)
     * Document editing complete and ready for saving
     * - Queue the save operation for sequential processing
     * - Save file from download URL with pessimistic lock
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

        // Queue the callback processing for sequential execution
        callbackQueueService.submitAndWait(fileId, () -> {
            documentService.processCallbackSave(downloadUrl, fileId);
        });

        log.info("Document saved and version incremented for fileKey: {}", fileId);
    }

    /**
     * Handle FORCESAVE status (status=6)
     * Force save during co-editing session
     * - Queue the save operation for sequential processing
     * - Save file from download URL with pessimistic lock
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

        // Queue the callback processing for sequential execution
        callbackQueueService.submitAndWait(fileId, () -> {
            documentService.processCallbackForceSave(downloadUrl, fileId);
        });

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
