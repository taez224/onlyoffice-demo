package com.example.onlyoffice.sdk;

import com.example.onlyoffice.service.CallbackQueueService;
import com.example.onlyoffice.service.DocumentService;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private CallbackQueueService callbackQueueService;

    private CustomCallbackService customCallbackService;

    private static final String FILE_KEY = "sampledocx";
    private static final String DOCUMENT_KEY = "sampledocx_v1";
    private static final String DOWNLOAD_URL = "http://localhost:9980/download/file123";

    @BeforeEach
    void setUp() throws Exception {
        customCallbackService = new CustomCallbackService(
                jwtManager,
                settingsManager,
                documentService,
                callbackQueueService
        );

        // CallbackQueueService가 즉시 작업을 실행하도록 설정 (lenient로 사용되지 않는 경우도 허용)
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(callbackQueueService).submitAndWait(anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("handlerSave: 정상 케이스 - 큐를 통해 문서 저장 및 버전 증가")
    void shouldSaveDocumentAndIncrementVersionThroughQueue() throws Exception {
        // given
        Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);

        // when
        customCallbackService.handlerSave(callback, FILE_KEY);

        // then
        verify(callbackQueueService).submitAndWait(eq(FILE_KEY), any(Runnable.class));
        verify(documentService).processCallbackSave(DOWNLOAD_URL, FILE_KEY);
    }

    @Test
    @DisplayName("handlerSave: Download URL이 null이면 예외 발생 (큐 제출 전)")
    void shouldThrowExceptionWhenSaveUrlIsNull() throws Exception {
        // given
        Callback callback = createCallback(Status.SAVE, null);

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerSave(callback, FILE_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Download URL is required");

        verify(callbackQueueService, never()).submitAndWait(anyString(), any(Runnable.class));
        verify(documentService, never()).processCallbackSave(anyString(), anyString());
    }

    @Test
    @DisplayName("handlerForcesave: 정상 케이스 - 큐를 통해 문서 저장만 (버전 증가 없음)")
    void shouldSaveDocumentWithoutIncrementingVersionThroughQueue() throws Exception {
        // given
        Callback callback = createCallback(Status.FORCESAVE, DOWNLOAD_URL);

        // when
        customCallbackService.handlerForcesave(callback, FILE_KEY);

        // then
        verify(callbackQueueService).submitAndWait(eq(FILE_KEY), any(Runnable.class));
        verify(documentService).processCallbackForceSave(DOWNLOAD_URL, FILE_KEY);
        // processCallbackSave는 호출되지 않음 (FORCESAVE는 버전 증가 없음)
        verify(documentService, never()).processCallbackSave(anyString(), anyString());
    }

    @Test
    @DisplayName("handlerForcesave: Download URL이 null이면 예외 발생 (큐 제출 전)")
    void shouldThrowExceptionWhenForcesaveUrlIsNull() throws Exception {
        // given
        Callback callback = createCallback(Status.FORCESAVE, null);

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerForcesave(callback, FILE_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Download URL is required");

        verify(callbackQueueService, never()).submitAndWait(anyString(), any(Runnable.class));
        verify(documentService, never()).processCallbackForceSave(anyString(), anyString());
    }

    @Test
    @DisplayName("로깅만 하는 핸들러 (EDITING, CLOSED) - 큐 및 DocumentService 호출 없음")
    void shouldOnlyLogForEditingAndClosedStatus() throws Exception {
        // given
        Callback editingCallback = createCallback(Status.EDITING, null);
        Callback closedCallback = createCallback(Status.CLOSED, null);

        // when
        customCallbackService.handlerEditing(editingCallback, FILE_KEY);
        customCallbackService.handlerClosed(closedCallback, FILE_KEY);

        // then: 큐 및 DocumentService 메서드 호출되지 않음
        verify(callbackQueueService, never()).submitAndWait(anyString(), any(Runnable.class));
        verify(documentService, never()).processCallbackSave(anyString(), anyString());
        verify(documentService, never()).processCallbackForceSave(anyString(), anyString());

        // 예외도 발생하지 않음
        assertThatCode(() -> {
            customCallbackService.handlerEditing(editingCallback, FILE_KEY);
            customCallbackService.handlerClosed(closedCallback, FILE_KEY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("에러 핸들러 (SAVE_CORRUPTED, FORCESAVE_CORRUPTED) - 큐 및 DocumentService 호출 없음")
    void shouldOnlyLogForCorruptedStatus() throws Exception {
        // given
        Callback saveCorruptedCallback = createCallback(Status.SAVE_CORRUPTED, null);
        Callback forcesaveCorruptedCallback = createCallback(Status.FORCESAVE_CORRUPTED, null);

        // when
        customCallbackService.handlerSaveCorrupted(saveCorruptedCallback, FILE_KEY);
        customCallbackService.handlerForcesaveCurrupted(forcesaveCorruptedCallback, FILE_KEY);

        // then: 큐 및 DocumentService 메서드 호출되지 않음
        verify(callbackQueueService, never()).submitAndWait(anyString(), any(Runnable.class));
        verify(documentService, never()).processCallbackSave(anyString(), anyString());
        verify(documentService, never()).processCallbackForceSave(anyString(), anyString());

        // 예외도 발생하지 않음
        assertThatCode(() -> {
            customCallbackService.handlerSaveCorrupted(saveCorruptedCallback, FILE_KEY);
            customCallbackService.handlerForcesaveCurrupted(forcesaveCorruptedCallback, FILE_KEY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("handlerSave: 큐에서 예외 발생 시 전파됨")
    void shouldPropagateExceptionFromQueueOnSave() throws Exception {
        // given
        Callback callback = createCallback(Status.SAVE, DOWNLOAD_URL);
        doThrow(new RuntimeException("Queue execution failed"))
                .when(callbackQueueService).submitAndWait(anyString(), any(Runnable.class));

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerSave(callback, FILE_KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Queue execution failed");
    }

    @Test
    @DisplayName("handlerForcesave: 큐에서 예외 발생 시 전파됨")
    void shouldPropagateExceptionFromQueueOnForcesave() throws Exception {
        // given
        Callback callback = createCallback(Status.FORCESAVE, DOWNLOAD_URL);
        doThrow(new RuntimeException("Queue execution failed"))
                .when(callbackQueueService).submitAndWait(anyString(), any(Runnable.class));

        // when & then
        assertThatThrownBy(() -> customCallbackService.handlerForcesave(callback, FILE_KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Queue execution failed");
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
