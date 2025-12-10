package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Status;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ONLYOFFICE Document Server Callback Controller
 *
 * Uses ONLYOFFICE SDK for:
 * - Type-safe Callback model
 * - Status enum (no more magic numbers!)
 * - JWT validation
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final DocumentService documentService;
    private final JwtManager jwtManager; // SDK JwtManager
    private final ObjectMapper objectMapper;

    /**
     * ONLYOFFICE Document Server callback handler
     *
     * Status enum values:
     * - EDITING(1): Document is being edited
     * - SAVE(2): Document ready for saving (editing complete) - Save file + increment editorVersion
     * - SAVE_CORRUPTED(3): Document saving error
     * - CLOSED(4): Document closed with no changes
     * - FORCESAVE(6): Force save - Save file only (editorVersion unchanged, co-editing session continues)
     * - FORCESAVE_CORRUPTED(7): Force save error
     */
    @PostMapping("/callback")
    public Map<String, Object> callback(HttpServletRequest request, @RequestBody String body) {

        try {
            // JWT validation using SDK JwtManager
            String authHeader = request.getHeader("Authorization");
            try {
                String payload = jwtManager.verify(authHeader);
                if (payload == null || payload.isBlank()) {
                    log.error("Invalid JWT token: payload is null or empty");
                    return Map.of("error", 1);
                }
            } catch (Exception e) {
                log.error("JWT validation failed: {}", e.getMessage());
                return Map.of("error", 1);
            }

            // Parse callback body to SDK Callback model
            Callback callback = objectMapper.readValue(body, Callback.class);
            Status status = callback.getStatus();
            String key = callback.getKey();

            log.info("Callback received: status={}, key={}", status, key);

            String fileName = request.getParameter("fileName");
            if (fileName == null) {
                fileName = "saved_" + System.currentTimeMillis() + ".docx";
                log.warn("fileName parameter missing, using fallback: {}", fileName);
            }

            // Use type-safe Status enum instead of magic numbers!
            switch (status) {
                case SAVE -> {
                    // Editing complete & save ready - Save file + increment editorVersion
                    String downloadUrl = callback.getUrl();
                    documentService.saveDocumentFromUrl(downloadUrl, fileName);
                    documentService.incrementEditorVersion(fileName);
                    log.info("Document saved: {}", fileName);
                }
                case FORCESAVE -> {
                    // Force save - Save file only, editorVersion unchanged (co-editing session continues)
                    String downloadUrl = callback.getUrl();
                    documentService.saveDocumentFromUrl(downloadUrl, fileName);
                    log.info("Force save completed for {}, editorVersion not changed", fileName);
                }
                case EDITING -> {
                    log.debug("Document being edited: {}", fileName);
                }
                case CLOSED -> {
                    log.info("Document closed without changes: {}", fileName);
                }
                case SAVE_CORRUPTED, FORCESAVE_CORRUPTED -> {
                    log.error("Document save error, status={}, key={}", status, key);
                }
                default -> {
                    log.warn("Unknown callback status: {}", status);
                }
            }

        } catch (Exception e) {
            log.error("Error processing callback", e);
            return Map.of("error", 1);
        }

        return Map.of("error", 0);
    }
}
