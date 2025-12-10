package com.example.onlyoffice.service;

import com.example.onlyoffice.util.JwtManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * ONLYOFFICE 에디터 Config 생성 서비스
 * - 에디터 설정 JSON 생성
 * - JWT 토큰 생성
 * - 문서 타입 결정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final DocumentService documentService;
    private final JwtManager jwtManager;

    @Value("${onlyoffice.url}")
    private String onlyofficeUrl;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    /**
     * 에디터 Config 생성
     *
     * @param fileName 파일명
     * @return ONLYOFFICE 에디터 설정
     */
    public Map<String, Object> createEditorConfig(String fileName) {
        String fileExtension = getFileExtension(fileName);
        String documentType = getDocumentType(fileExtension);
        String editorKey = documentService.getEditorKey(fileName);

        // 1. Config 구조 생성
        Map<String, Object> config = new HashMap<>();
        config.put("documentType", documentType);
        config.put("type", "desktop");

        // 2. Document 설정
        Map<String, Object> document = createDocumentConfig(fileName, fileExtension, editorKey);
        config.put("document", document);

        // 3. Editor 설정
        Map<String, Object> editorConfig = createEditorSettings(fileName);
        config.put("editorConfig", editorConfig);

        // 4. JWT 토큰 생성
        String token = jwtManager.createToken(config);
        config.put("token", token);

        log.info("Editor config created for file: {}, key: {}, type: {}", fileName, editorKey, documentType);

        return config;
    }

    /**
     * 전체 응답 생성 (config + documentServerUrl)
     */
    public Map<String, Object> createEditorResponse(String fileName) {
        Map<String, Object> config = createEditorConfig(fileName);

        Map<String, Object> response = new HashMap<>();
        response.put("config", config);
        response.put("documentServerUrl", onlyofficeUrl);

        return response;
    }

    /**
     * Document 설정 생성
     */
    private Map<String, Object> createDocumentConfig(String fileName, String fileExtension, String editorKey) {
        Map<String, Object> document = new HashMap<>();
        document.put("title", fileName);
        document.put("url", serverBaseUrl + "/files/" + fileName);
        document.put("fileType", fileExtension);
        document.put("key", editorKey);

        // Permissions
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("edit", true);
        permissions.put("download", true);
        permissions.put("print", true);
        permissions.put("review", true);
        document.put("permissions", permissions);

        return document;
    }

    /**
     * Editor Settings 생성
     */
    private Map<String, Object> createEditorSettings(String fileName) {
        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("callbackUrl", serverBaseUrl + "/callback?fileName=" + fileName);
        editorConfig.put("lang", "ko");

        // User 정보 (향후 인증 시스템 통합 가능)
        Map<String, Object> user = new HashMap<>();
        user.put("id", "uid-1");
        user.put("name", "Demo User");
        editorConfig.put("user", user);

        // Customization (선택사항)
        Map<String, Object> customization = new HashMap<>();
        customization.put("autosave", true);
        customization.put("forcesave", false);
        editorConfig.put("customization", customization);

        return editorConfig;
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 문서 타입 결정
     * - word: 문서 편집
     * - cell: 스프레드시트
     * - slide: 프레젠테이션
     */
    private String getDocumentType(String extension) {
        return switch (extension) {
            case "docx", "doc", "odt", "txt", "rtf", "pdf", "djvu", "xps", "oxps" -> "word";
            case "xlsx", "xls", "ods", "csv" -> "cell";
            case "pptx", "ppt", "odp" -> "slide";
            default -> "word";
        };
    }
}
