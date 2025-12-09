package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.StorageException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.ServerException;
import io.minio.http.Method;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioStorageService 단위 테스트")
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private MinioStorageService storageService;

    private final String TEST_BUCKET = "test-bucket";
    private final int TEST_EXPIRY = 3600;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucket", TEST_BUCKET);
        ReflectionTestUtils.setField(storageService, "presignedUrlExpiry", TEST_EXPIRY);
    }

    @Nested
    @DisplayName("버킷 초기화 테스트")
    class InitTests {

        @Test
        @DisplayName("버킷이 존재하지 않으면 새로 생성한다")
        void init_CreatesBucket_WhenNotExists() throws Exception {
            // given
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

            // when
            storageService.init();

            // then
            verify(minioClient).bucketExists(any(BucketExistsArgs.class));
            verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("버킷이 이미 존재하면 생성하지 않는다")
        void init_SkipsBucketCreation_WhenExists() throws Exception {
            // given
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

            // when
            storageService.init();

            // then
            verify(minioClient).bucketExists(any(BucketExistsArgs.class));
            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("버킷 생성 실패 시 StorageException을 던진다")
        void init_ThrowsStorageException_OnFailure() throws Exception {
            // given
            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("MinIO connection error"));

            // when & then
            assertThatThrownBy(() -> storageService.init())
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to initialize MinIO bucket");
        }
    }

    @Nested
    @DisplayName("파일 업로드 테스트")
    class UploadTests {

        @Test
        @DisplayName("MultipartFile을 MinIO에 업로드할 수 있다")
        void uploadFile_Success() throws Exception {
            // given
            String objectName = "documents/test.docx";
            byte[] content = "test content".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "test.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    content
            );

            ObjectWriteResponse mockResponse = mock(ObjectWriteResponse.class);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mockResponse);

            // when
            String result = storageService.uploadFile(file, objectName);

            // then
            assertThat(result).isEqualTo(objectName);
            verify(minioClient).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("업로드 실패 시 StorageException을 던진다")
        void uploadFile_ThrowsStorageException_OnFailure() throws Exception {
            // given
            String objectName = "documents/test.docx";
            MultipartFile file = new MockMultipartFile("file", "test.docx", "text/plain", "test".getBytes());

            doThrow(new RuntimeException("Upload failed"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            // when & then
            assertThatThrownBy(() -> storageService.uploadFile(file, objectName))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to upload file");
        }
    }

    @Nested
    @DisplayName("파일 다운로드 테스트")
    class DownloadTests {

        @Test
        @DisplayName("MinIO에서 파일을 다운로드할 수 있다")
        void downloadFile_Success() throws Exception {
            // given
            String objectName = "documents/test.docx";
            GetObjectResponse mockResponse = mock(GetObjectResponse.class);

            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

            // when
            InputStream result = storageService.downloadFile(objectName);

            // then
            assertThat(result).isNotNull();
            verify(minioClient).getObject(any(GetObjectArgs.class));
        }

        @Test
        @DisplayName("존재하지 않는 파일 다운로드 시 StorageException을 던진다")
        void downloadFile_ThrowsStorageException_WhenFileNotFound() throws Exception {
            // given
            String objectName = "documents/nonexistent.docx";
            ErrorResponse errorResponse = new ErrorResponse(
                    "NoSuchKey",
                    "The specified key does not exist",
                    TEST_BUCKET,
                    objectName,
                    "",
                    "",
                    ""
            );
            ErrorResponseException exception = new ErrorResponseException(
                    errorResponse,
                    null,
                    "test-method"
            );

            when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> storageService.downloadFile(objectName))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("File not found");
        }
    }

    @Nested
    @DisplayName("파일 삭제 테스트")
    class DeleteTests {

        @Test
        @DisplayName("MinIO에서 파일을 삭제할 수 있다")
        void deleteFile_Success() throws Exception {
            // given
            String objectName = "documents/test.docx";
            // removeObject는 void를 반환하므로 아무것도 mock하지 않음

            // when
            storageService.deleteFile(objectName);

            // then
            verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        }

        @Test
        @DisplayName("삭제 실패 시 StorageException을 던진다")
        void deleteFile_ThrowsStorageException_OnFailure() throws Exception {
            // given
            String objectName = "documents/test.docx";
            doThrow(new RuntimeException("Delete failed"))
                    .when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // when & then
            assertThatThrownBy(() -> storageService.deleteFile(objectName))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to delete file");
        }
    }

    @Nested
    @DisplayName("Presigned URL 생성 테스트")
    class PresignedUrlTests {

        @Test
        @DisplayName("1시간 유효한 presigned URL을 생성할 수 있다")
        void generatePresignedUrl_Success() throws Exception {
            // given
            String objectName = "documents/test.docx";
            String expectedUrl = "http://localhost:9000/test-bucket/documents/test.docx?X-Amz-Expires=3600";

            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn(expectedUrl);

            // when
            String result = storageService.generatePresignedUrl(objectName);

            // then
            assertThat(result).isEqualTo(expectedUrl);
            verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        }

        @Test
        @DisplayName("Presigned URL 생성 실패 시 StorageException을 던진다")
        void generatePresignedUrl_ThrowsStorageException_OnFailure() throws Exception {
            // given
            String objectName = "documents/test.docx";
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenThrow(new RuntimeException("URL generation failed"));

            // when & then
            assertThatThrownBy(() -> storageService.generatePresignedUrl(objectName))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Failed to generate presigned URL");
        }
    }

    @Nested
    @DisplayName("객체 존재 여부 확인 테스트")
    class ObjectExistsTests {

        @Test
        @DisplayName("객체가 존재하면 true를 반환한다")
        void objectExists_ReturnsTrue_WhenObjectExists() throws Exception {
            // given
            String objectName = "documents/test.docx";
            when(minioClient.statObject(any(StatObjectArgs.class)))
                    .thenReturn(null); // statObject returns StatObjectResponse when object exists

            // when
            boolean result = storageService.objectExists(objectName);

            // then
            assertThat(result).isTrue();
            verify(minioClient).statObject(any(StatObjectArgs.class));
        }

        @Test
        @DisplayName("객체가 존재하지 않으면 false를 반환한다")
        void objectExists_ReturnsFalse_WhenObjectNotFound() throws Exception {
            // given
            String objectName = "documents/nonexistent.docx";
            ErrorResponse errorResponse = new ErrorResponse(
                    "NoSuchKey",
                    "The specified key does not exist",
                    TEST_BUCKET,
                    objectName,
                    "",
                    "",
                    ""
            );
            ErrorResponseException exception = new ErrorResponseException(
                    errorResponse,
                    null,
                    "test-method"
            );

            when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(exception);

            // when
            boolean result = storageService.objectExists(objectName);

            // then
            assertThat(result).isFalse();
        }
    }
}
