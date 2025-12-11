package com.example.onlyoffice.sdk;

import com.example.onlyoffice.service.DocumentService;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("CustomCallbackService")
@ExtendWith(MockitoExtension.class)
class CustomCallbackServiceTest {

    @Mock
    private JwtManager jwtManager;

    @Mock
    private CustomSettingsManager settingsManager;

    @Mock
    private DocumentService documentService;

    private CustomCallbackService customCallbackService;

    private static final String FILE_NAME = "sample.docx";
    private static final String DOCUMENT_KEY = "sampledocx_v1";
    private static final String DOWNLOAD_URL = "http://localhost:9980/download/file123";

    @BeforeEach
    void setUp() {
        customCallbackService = new CustomCallbackService(
                jwtManager,
                settingsManager,
                documentService
        );
    }

    @Test
    @DisplayName("handlerSave: 정상 케이스 - 문서 저장 및 버전 증가")
    void shouldSaveDocumentAndIncrementVersion() throws Exception {
        // given
        Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);

        // when
        customCallbackService.handlerSave(callback, FILE_NAME);

        // then
        verify(documentService).saveDocumentFromUrl(DOWNLOAD_URL, FILE_NAME);
        verify(documentService).incrementEditorVersion(FILE_NAME);

        // 호출 순서 검증 (save → increment)
        InOrder inOrder = inOrder(documentService);
        inOrder.verify(documentService).saveDocumentFromUrl(DOWNLOAD_URL, FILE_NAME);
        inOrder.verify(documentService).incrementEditorVersion(FILE_NAME);
    }

    @Test
    @DisplayName("handlerSave: Download URL이 null이면 예외 발생")
    void shouldThrowExceptionWhenSaveUrlIsNull() {
        // given
        Callback callback = createCallback(Status.SAVE, null);

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerSave(callback, FILE_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Download URL is required");

        verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        verify(documentService, never()).incrementEditorVersion(anyString());
    }

    @Test
    @DisplayName("handlerForcesave: 정상 케이스 - 문서 저장만 (버전 증가 없음)")
    void shouldSaveDocumentWithoutIncrementingVersion() throws Exception {
        // given
        Callback callback = createCallback(Status.FORCESAVE, DOWNLOAD_URL);

        // when
        customCallbackService.handlerForcesave(callback, FILE_NAME);

        // then
        verify(documentService).saveDocumentFromUrl(DOWNLOAD_URL, FILE_NAME);
        // CRITICAL: 버전 증가 호출되지 않음 검증
        verify(documentService, never()).incrementEditorVersion(anyString());
    }

    @Test
    @DisplayName("handlerForcesave: Download URL이 null이면 예외 발생")
    void shouldThrowExceptionWhenForcesaveUrlIsNull() {
        // given
        Callback callback = createCallback(Status.FORCESAVE, null);

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerForcesave(callback, FILE_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Download URL is required");

        verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("로깅만 하는 핸들러 (EDITING, CLOSED) - DocumentService 호출 없음")
    void shouldOnlyLogForEditingAndClosedStatus() throws Exception {
        // given
        Callback editingCallback = createCallback(Status.EDITING, null);
        Callback closedCallback = createCallback(Status.CLOSED, null);

        // when
        customCallbackService.handlerEditing(editingCallback, FILE_NAME);
        customCallbackService.handlerClosed(closedCallback, FILE_NAME);

        // then: 어떤 DocumentService 메서드도 호출되지 않음
        verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        verify(documentService, never()).incrementEditorVersion(anyString());

        // 예외도 발생하지 않음
        assertThatCode(() -> {
            customCallbackService.handlerEditing(editingCallback, FILE_NAME);
            customCallbackService.handlerClosed(closedCallback, FILE_NAME);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("에러 핸들러 (SAVE_CORRUPTED, FORCESAVE_CORRUPTED) - DocumentService 호출 없음")
    void shouldOnlyLogForCorruptedStatus() throws Exception {
        // given
        Callback saveCorruptedCallback = createCallback(Status.SAVE_CORRUPTED, null);
        Callback forcesaveCorruptedCallback = createCallback(Status.FORCESAVE_CORRUPTED, null);

        // when
        customCallbackService.handlerSaveCorrupted(saveCorruptedCallback, FILE_NAME);
        customCallbackService.handlerForcesaveCurrupted(forcesaveCorruptedCallback, FILE_NAME);

        // then: 어떤 DocumentService 메서드도 호출되지 않음
        verify(documentService, never()).saveDocumentFromUrl(anyString(), anyString());
        verify(documentService, never()).incrementEditorVersion(anyString());

        // 예외도 발생하지 않음
        assertThatCode(() -> {
            customCallbackService.handlerSaveCorrupted(saveCorruptedCallback, FILE_NAME);
            customCallbackService.handlerForcesaveCurrupted(forcesaveCorruptedCallback, FILE_NAME);
        }).doesNotThrowAnyException();
    }

    /**
     * Helper method to create Callback object
     */
    private Callback createCallback(Status status, String downloadUrl) {
        Callback callback = new Callback();
        callback.setStatus(status);
        callback.setUrl(downloadUrl);
        callback.setKey(DOCUMENT_KEY);
        return callback;
    }
}
