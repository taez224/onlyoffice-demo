package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallbackController.class)
@DisplayName("CallbackController")
class CallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private JwtManager jwtManager;

    private static final String CALLBACK_URL = "/callback";
    private static final String FILE_NAME = "sample.docx";
    private static final String DOCUMENT_KEY = "sampledocx_v1";
    private static final String DOWNLOAD_URL = "http://localhost:9980/download/file123";
    private static final String JWT_TOKEN = "Bearer valid.jwt.token";

    @Nested
    @DisplayName("callback - SAVE (status=2)")
    class CallbackSave {

        @Test
        @DisplayName("SAVE 상태일 때 파일 저장 및 버전 증가")
        void shouldSaveFileAndIncrementVersionOnSaveStatus() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService).saveDocumentFromUrl(DOWNLOAD_URL, FILE_NAME);
            verify(documentService).incrementEditorVersion(FILE_NAME);
        }

        @Test
        @DisplayName("SAVE 상태일 때 fileName 파라미터 없으면 key에서 추출")
        void shouldExtractFileNameFromKeyWhenParamMissing() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk());

            verify(documentService).saveDocumentFromUrl(eq(DOWNLOAD_URL), anyString());
        }
    }

    @Nested
    @DisplayName("callback - FORCESAVE (status=6)")
    class CallbackForceSave {

        @Test
        @DisplayName("FORCESAVE 상태일 때 파일만 저장 (버전 증가 없음)")
        void shouldSaveFileWithoutIncrementingVersionOnForceSave() throws Exception {
            // given
            Callback callback = createCallback(Status.FORCESAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService).saveDocumentFromUrl(DOWNLOAD_URL, FILE_NAME);
            verify(documentService, never()).incrementEditorVersion(anyString());
        }
    }

    @Nested
    @DisplayName("callback - EDITING (status=1)")
    class CallbackEditing {

        @Test
        @DisplayName("EDITING 상태일 때 파일 저장 없음")
        void shouldNotSaveFileOnEditingStatus() throws Exception {
            // given
            Callback callback = createCallback(Status.EDITING, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
            verify(documentService, never()).incrementEditorVersion(anyString());
        }
    }

    @Nested
    @DisplayName("callback - CLOSED (status=4)")
    class CallbackClosed {

        @Test
        @DisplayName("CLOSED 상태일 때 파일 저장 없음")
        void shouldNotSaveFileOnClosedStatus() throws Exception {
            // given
            Callback callback = createCallback(Status.CLOSED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("callback - Error Status")
    class CallbackErrorStatus {

        @Test
        @DisplayName("SAVE_CORRUPTED 상태일 때 에러 로그만 기록")
        void shouldLogErrorOnSaveCorruptedStatus() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE_CORRUPTED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }

        @Test
        @DisplayName("FORCESAVE_CORRUPTED 상태일 때 에러 로그만 기록")
        void shouldLogErrorOnForceSaveCorruptedStatus() throws Exception {
            // given
            Callback callback = createCallback(Status.FORCESAVE_CORRUPTED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("valid-payload");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("JWT Validation")
    class JwtValidation {

        @Test
        @DisplayName("유효하지 않은 JWT 토큰은 에러 반환")
        void shouldReturnErrorForInvalidJwt() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify("Bearer invalid.token")).thenThrow(new RuntimeException("Invalid JWT"));

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", "Bearer invalid.token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(1));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }

        @Test
        @DisplayName("JWT payload가 null이면 에러 반환")
        void shouldReturnErrorForNullJwtPayload() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn(null);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(1));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }

        @Test
        @DisplayName("JWT payload가 빈 문자열이면 에러 반환")
        void shouldReturnErrorForBlankJwtPayload() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(jwtManager.verify(JWT_TOKEN)).thenReturn("  ");

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                    .param("fileName", FILE_NAME)
                    .header("Authorization", JWT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(callbackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(1));

            verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        }
    }

    /**
     * Helper method to create Callback object
     */
    private Callback createCallback(Status status, String url) {
        Callback callback = new Callback();
        callback.setStatus(status);
        callback.setKey(DOCUMENT_KEY);
        callback.setUrl(url);
        return callback;
    }
}