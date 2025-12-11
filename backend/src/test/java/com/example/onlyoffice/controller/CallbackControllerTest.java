package com.example.onlyoffice.controller;

import com.example.onlyoffice.sdk.CustomSettingsManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Status;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for CallbackController leveraging SDK CallbackService
 */
@WebMvcTest(CallbackController.class)
@DisplayName("CallbackController")
class CallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CallbackService callbackService;

    @MockBean
    private CustomSettingsManager settingsManager;

    private static final String CALLBACK_URL = "/callback";
    private static final String FILE_NAME = "sample.docx";
    private static final String DOCUMENT_KEY = "sampledocx_v1";
    private static final String DOWNLOAD_URL = "http://localhost:9980/download/file123";
    private static final String JWT_TOKEN = "Bearer valid.jwt.token";

    @Nested
    @DisplayName("callback - SAVE (status=2)")
    class CallbackSave {

        @Test
        @DisplayName("SAVE 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnSave() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(settingsManager).getSecurityHeader();
            verify(callbackService).verifyCallback(any(Callback.class), eq(JWT_TOKEN));
            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }

        @Test
        @DisplayName("CallbackService에서 예외 발생 시 에러 반환")
        void shouldReturnErrorWhenCallbackServiceThrowsException() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);
            doThrow(new IllegalArgumentException("Download URL is required"))
                    .when(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(1));

            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }

        @Test
        @DisplayName("fileName 파라미터 없으면 fallback 파일명 사용")
        void shouldUseFallbackFileNameWhenParamMissing() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk());

            verify(callbackService).processCallback(any(Callback.class), anyString());
        }
    }

    @Nested
    @DisplayName("callback - FORCESAVE (status=6)")
    class CallbackForceSave {

        @Test
        @DisplayName("FORCESAVE 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnForceSave() throws Exception {
            // given
            Callback callback = createCallback(Status.FORCESAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(callbackService).verifyCallback(any(Callback.class), eq(JWT_TOKEN));
            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }
    }

    @Nested
    @DisplayName("callback - EDITING (status=1)")
    class CallbackEditing {

        @Test
        @DisplayName("EDITING 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnEditing() throws Exception {
            // given
            Callback callback = createCallback(Status.EDITING, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }
    }

    @Nested
    @DisplayName("callback - CLOSED (status=4)")
    class CallbackClosed {

        @Test
        @DisplayName("CLOSED 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnClosed() throws Exception {
            // given
            Callback callback = createCallback(Status.CLOSED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }
    }

    @Nested
    @DisplayName("callback - Error Status")
    class CallbackErrorStatus {

        @Test
        @DisplayName("SAVE_CORRUPTED 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnSaveCorrupted() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE_CORRUPTED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }

        @Test
        @DisplayName("FORCESAVE_CORRUPTED 상태일 때 SDK CallbackService 호출")
        void shouldCallCallbackServiceOnForceSaveCorrupted() throws Exception {
            // given
            Callback callback = createCallback(Status.FORCESAVE_CORRUPTED, null);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq(JWT_TOKEN)))
                    .thenReturn(callback);

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", JWT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(0));

            verify(callbackService).processCallback(any(Callback.class), eq(FILE_NAME));
        }
    }

    @Nested
    @DisplayName("JWT Validation")
    class JwtValidation {

        @Test
        @DisplayName("JWT 검증 실패 시 에러 반환")
        void shouldReturnErrorWhenJwtVerificationFails() throws Exception {
            // given
            Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
            String callbackJson = objectMapper.writeValueAsString(callback);

            when(settingsManager.getSecurityHeader()).thenReturn("Authorization");
            when(callbackService.verifyCallback(any(Callback.class), eq("Bearer invalid.token")))
                    .thenThrow(new RuntimeException("Invalid JWT"));

            // when & then
            mockMvc.perform(post(CALLBACK_URL)
                            .param("fileName", FILE_NAME)
                            .header("Authorization", "Bearer invalid.token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(1));

            verify(callbackService).verifyCallback(any(Callback.class), eq("Bearer invalid.token"));
            verify(callbackService, never()).processCallback(any(Callback.class), anyString());
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