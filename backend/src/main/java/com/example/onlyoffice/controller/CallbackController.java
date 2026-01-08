package com.example.onlyoffice.controller;

import com.example.onlyoffice.exception.DocumentNotFoundException;
import com.example.onlyoffice.sdk.CustomSettingsManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ONLYOFFICE Document Server Callback Controller
 * <p>
 * Leverages ONLYOFFICE SDK CallbackService for:
 * - JWT validation (verifyCallback)
 * - Status-based routing (processCallback)
 * - Type-safe Callback model
 * <p>
 * Business logic implemented in CustomCallbackService
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;  // SDK CallbackService (CustomCallbackService)
    private final CustomSettingsManager settingsManager;  // For getting security header name
    private final ObjectMapper objectMapper;

    /**
     * ONLYOFFICE Document Server callback handler
     * <p>
     * Flow:
     * 1. Parse JSON body to Callback object
     * 2. SDK verifies JWT token
     * 3. SDK routes to appropriate handler based on Status
     * 4. CustomCallbackService executes business logic
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(HttpServletRequest request, @RequestBody String body) {

        try {
            // Get security header name from settings (not hardcoded!)
            String headerName = settingsManager.getSecurityHeader();  // "Authorization"
            String authHeader = request.getHeader(headerName);

            // Extract fileKey from query parameter
            String fileKey = request.getParameter("fileKey");
            if (fileKey == null) {
                log.error("fileKey parameter missing in callback");
                return ResponseEntity.ok(Map.of("error", 1));
            }

            // Parse JSON to Callback object
            Callback callback = objectMapper.readValue(body, Callback.class);

            log.info("Callback received: status={}, key={}, fileKey={}",
                    callback.getStatus(), callback.getKey(), fileKey);

            // SDK verifies JWT and validates callback
            callbackService.verifyCallback(callback, authHeader);

            // SDK routes to appropriate handler (handlerSave, handlerForcesave, etc.)
            // fileKey is passed as the fileId parameter
            callbackService.processCallback(callback, fileKey);

            return ResponseEntity.ok(Map.of("error", 0));

        } catch (LockTimeoutException | PessimisticLockException e) {
            log.warn("Lock timeout for callback, ONLYOFFICE should retry: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", 1, "message", "Document is locked, please retry"));
        } catch (DocumentNotFoundException | IllegalArgumentException e) {
            log.error("Callback failed (non-retryable): {}", e.getMessage());
            return ResponseEntity.ok(Map.of("error", 1));
        } catch (Exception e) {
            log.error("Callback processing failed", e);
            return ResponseEntity.ok(Map.of("error", 1));
        }
    }
}
