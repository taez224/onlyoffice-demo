package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.model.documenteditor.config.document.Permissions;
import com.onlyoffice.model.documenteditor.config.document.permissions.CommentGroups;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom Permission Manager
 * Provides document permissions for ONLYOFFICE editor
 * <p>
 * Demo implementation: Returns full edit permissions for all users
 */
@Slf4j
@Component
public class CustomPermissionManager {

    private final DocumentManager documentManager;

    public CustomPermissionManager(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    /**
     * Get permissions for a document
     *
     * @param fileId The document's fileKey (UUID)
     * @return Permissions object with full edit permissions (demo)
     */
    public Permissions getPermission(String fileId) {
        log.debug("Creating permissions for fileId: {}", fileId);

        // Demo: Full edit permissions for all documents
        return Permissions.builder()
                .edit(true)
                .download(true)
                .print(true)
                .review(true)
                .comment(true)
                .fillForms(true)
                .modifyFilter(true)
                .modifyContentControl(true)
                .commentGroups(CommentGroups.builder().build())
                .copy(true)
                .build();
    }
}
