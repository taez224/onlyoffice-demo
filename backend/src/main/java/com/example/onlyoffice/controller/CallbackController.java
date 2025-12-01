package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.util.JwtManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final DocumentService documentService;
    private final JwtManager jwtManager;
    private final ObjectMapper objectMapper;

    @PostMapping("/callback")
    public Map<String, Object> callback(HttpServletRequest request, @RequestBody String body) {

        try {
            String authHeader = request.getHeader("Authorization");
            if (!jwtManager.validateToken(authHeader)) {
                log.error("Invalid JWT token");
                return Map.of("error", 1);
            }

            Map<String, Object> callbackData = objectMapper.readValue(body, Map.class);
            int status = (int) callbackData.get("status");

            log.info("Callback received: status={}", status);

            if (status == 2 || status == 6) { // 2 = Ready for saving, 6 = Force save
                String downloadUrl = (String) callbackData.get("url");
                String key = (String) callbackData.get("key");
                // Extract filename from key or pass it in url parameters.
                // For simplicity, we assume the key contains the filename or we use a fixed
                // mapping.
                // Here we will try to get it from the users list or just save it as
                // 'saved_<key>.docx'
                // But better approach for this demo: The EditorController generates a key based
                // on filename.
                // We can't easily get original filename from just key unless we store state.
                // So we will rely on a custom parameter in the url if possible, or just
                // overwrite the file if we knew which one it was.

                // Wait, the callback doesn't easily give the original filename back unless we
                // encoded it in the 'key' or 'userdata'.
                // Let's assume the 'key' is the filename for this simple demo, or we just save
                // it back to the same name if we can find it.
                // Actually, let's look at 'users' or 'actions'.

                // For this template, let's just download the file from 'url' and save it.
                // We need to know WHERE to save it.
                // A common pattern is to encode the filename in the 'key' or pass it as a query
                // param to the callbackUrl.
                // But the callbackUrl is set in the config.

                // Let's assume we pass the filename as a query param in the callbackUrl?
                // No, the callbackUrl is defined in the config object sent to the editor.
                // We can append ?fileName=... to the callbackUrl in EditorController.

                String fileName = request.getParameter("fileName");
                if (fileName == null) {
                    fileName = "saved_" + System.currentTimeMillis() + ".docx";
                }

                log.info("Downloading file from {} to {}", downloadUrl, fileName);
                try (java.io.InputStream in = URI.create(downloadUrl).toURL().openStream()) {
                    documentService.saveFile(fileName, in);
                } catch (Exception e) {
                    log.error("Error downloading file", e);
                    return Map.of("error", 1);
                }
            }
        } catch (Exception e) {
            log.error("Error processing callback", e);
            return Map.of("error", 1);
        }

        return Map.of("error", 0);
    }
}
