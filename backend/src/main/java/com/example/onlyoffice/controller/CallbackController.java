package com.example.onlyoffice.controller;

import com.example.onlyoffice.sdk.CustomSettingsManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public Map<String, Object> callback(HttpServletRequest request, @RequestBody String body) {

        try {
            // Get security header name from settings (not hardcoded!)
            String headerName = settingsManager.getSecurityHeader();  // "Authorization"
            String authHeader = request.getHeader(headerName);

            // Extract fileName from query parameter
            String fileName = request.getParameter("fileName");
            if (fileName == null) {
                fileName = "saved_" + System.currentTimeMillis() + ".docx";
                log.warn("fileName parameter missing, using fallback: {}", fileName);
            }

            // Parse JSON to Callback object
            Callback callback = objectMapper.readValue(body, Callback.class);

            log.info("Callback received: status={}, key={}", callback.getStatus(), callback.getKey());

            // SDK verifies JWT and validates callback
            callbackService.verifyCallback(callback, authHeader);

            // SDK routes to appropriate handler (handlerSave, handlerForcesave, etc.)
            callbackService.processCallback(callback, fileName);

            return Map.of("error", 0);

        } catch (Exception e) {
            log.error("Callback processing failed", e);
            return Map.of("error", 1);
        }
    }
}
