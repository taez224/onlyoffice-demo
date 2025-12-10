package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.EditorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * ONLYOFFICE 에디터 설정 컨트롤러
 * - ONLYOFFICE SDK를 사용하여 Type-safe Config 생성
 * - 에디터 Config JSON 반환
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class EditorController {

    private final EditorConfigService editorConfigService;

    /**
     * ONLYOFFICE 에디터 설정 반환
     *
     * @param fileName 편집할 파일명
     * @return ONLYOFFICE 에디터 설정 (config + documentServerUrl)
     */
    @GetMapping("/api/config")
    @ResponseBody
    public Map<String, Object> getEditorConfig(@RequestParam("fileName") String fileName) {
        log.info("Editor config requested for file: {}", fileName);
        return editorConfigService.createEditorResponse(fileName);
    }
}
