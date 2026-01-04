package com.example.onlyoffice.dto;

import java.util.Map;

/**
 * 에디터 설정 응답 DTO.
 * ONLYOFFICE 에디터 초기화에 필요한 설정을 감싸서 반환합니다.
 */
public record EditorConfigResponse(
        Object config,
        String documentServerUrl
) {
    public static EditorConfigResponse from(Map<String, Object> editorResponse) {
        return new EditorConfigResponse(
                editorResponse.get("config"),
                (String) editorResponse.get("documentServerUrl")
        );
    }
}
