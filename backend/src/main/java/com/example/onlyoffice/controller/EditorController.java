package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.DocumentService;
import com.example.onlyoffice.util.JwtManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class EditorController {

    private final DocumentService documentService;
    private final JwtManager jwtManager;

    @Value("${onlyoffice.url}")
    private String onlyofficeUrl;

    @GetMapping("/api/config")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> getEditorConfig(@RequestParam("fileName") String fileName) {
        File file = documentService.getFile(fileName);

        String serverUrl = documentService.getServerUrl();
        String fileExtension = getFileExtension(fileName);
        String documentType = getDocumentType(fileExtension);

        Map<String, Object> config = new HashMap<>();
        config.put("documentType", documentType);
        config.put("type", "desktop");

        Map<String, Object> document = new HashMap<>();
        document.put("title", fileName);
        document.put("url", serverUrl + "/files/" + fileName);
        document.put("fileType", fileExtension);
        document.put("key", fileName + "_" + file.lastModified());

        Map<String, Object> permissions = new HashMap<>();
        permissions.put("edit", true);
        permissions.put("download", true);
        document.put("permissions", permissions);

        config.put("document", document);

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("callbackUrl", serverUrl + "/callback?fileName=" + fileName);

        Map<String, Object> user = new HashMap<>();
        user.put("id", "uid-1");
        user.put("name", "John Doe");
        editorConfig.put("user", user);

        config.put("editorConfig", editorConfig);

        String token = jwtManager.createToken(config);
        config.put("token", token);

        Map<String, Object> response = new HashMap<>();
        response.put("config", config);
        response.put("documentServerUrl", onlyofficeUrl);

        return response;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    private String getDocumentType(String extension) {
        return switch (extension) {
            case "docx", "doc", "odt", "txt", "rtf", "pdf", "djvu", "xps", "oxps" -> "word";
            case "xlsx", "xls", "ods", "csv" -> "cell";
            case "pptx", "ppt", "odp" -> "slide";
            default -> "word";
        };
    }
}
