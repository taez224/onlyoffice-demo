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

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final DocumentService documentService;
    private final JwtManager jwtManager;
    private final ObjectMapper objectMapper;

    /**
     * ONLYOFFICE Document Server 콜백 처리
     *
     * Status 코드:
     * - 1: 편집 중 (사용자 접속/해제)
     * - 2: 저장 완료 (편집 종료) - 파일 저장 + editorVersion 증가
     * - 3: 저장 에러
     * - 4: 변경 없이 닫힘
     * - 6: Force Save - 파일 저장만 (editorVersion 유지)
     * - 7: Force Save 에러
     */
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
            String key = (String) callbackData.get("key");

            log.info("Callback received: status={}, key={}", status, key);

            String fileName = request.getParameter("fileName");
            if (fileName == null) {
                fileName = "saved_" + System.currentTimeMillis() + ".docx";
                log.warn("fileName parameter missing, using fallback: {}", fileName);
            }

            switch (status) {
                case 2 -> {
                    // 편집 종료 & 저장 완료 - 파일 저장 + editorVersion 증가
                    String downloadUrl = (String) callbackData.get("url");
                    documentService.saveDocumentFromUrl(downloadUrl, fileName);
                    documentService.incrementEditorVersion(fileName);
                }
                case 6 -> {
                    // Force Save - 파일만 저장, editorVersion 유지 (co-editing 세션 유지)
                    String downloadUrl = (String) callbackData.get("url");
                    documentService.saveDocumentFromUrl(downloadUrl, fileName);
                    log.info("Force save completed for {}, editorVersion not changed", fileName);
                }
                case 1 -> log.debug("Document being edited: {}", fileName);
                case 4 -> log.info("Document closed without changes: {}", fileName);
                case 3, 7 -> log.error("Document save error, status={}, key={}", status, key);
                default -> log.warn("Unknown callback status: {}", status);
            }

        } catch (Exception e) {
            log.error("Error processing callback", e);
            return Map.of("error", 1);
        }

        return Map.of("error", 0);
    }
}
