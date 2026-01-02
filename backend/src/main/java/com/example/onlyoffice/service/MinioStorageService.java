package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.StorageException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final long DEFAULT_MULTIPART_SIZE = 10 * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-url-expiry}")
    private int presignedUrlExpiry;

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
                log.info("MinIO bucket created: {}", bucket);
            } else {
                log.info("MinIO bucket already exists: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", bucket, e);
            throw new StorageException("Failed to initialize MinIO bucket", e);
        }
    }

    /**
     * 파일을 MinIO에 업로드
     *
     * @param file       MultipartFile from HTTP request
     * @param objectName The object key/path in MinIO (e.g., "documents/doc-123.docx")
     * @return The object name (can be used as storagePath in Document entity)
     * @throws StorageException if upload fails
     */
    public String uploadFile(MultipartFile file, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            uploadStream(inputStream, file.getSize(), file.getContentType(), objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", objectName, e);
            throw new StorageException("Failed to upload file: " + objectName, e);
        }
    }

    /**
     * InputStream을 MinIO에 업로드 (크기가 불명확한 스트림도 지원)
     * 
     * <p>재시도 정책: 네트워크 장애 등으로 실패 시 최대 3회 재시도 (1초 간격)</p>
     *
     * @param inputStream 업로드할 InputStream
     * @param size        총 바이트 수 (모를 경우 -1)
     * @param contentType MIME 타입
     * @param objectName  MinIO object key
     * @throws StorageException 재시도 후에도 실패 시
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public void uploadStream(InputStream inputStream, long size, String contentType, String objectName) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName);

            if (size >= 0) {
                builder.stream(inputStream, size, -1);
            } else {
                builder.stream(inputStream, -1, DEFAULT_MULTIPART_SIZE);
            }

            if (StringUtils.hasText(contentType)) {
                builder.contentType(contentType);
            }

            minioClient.putObject(builder.build());
            log.info("File uploaded to MinIO: {}/{}", bucket, objectName);
        } catch (Exception e) {
            log.error("Failed to upload stream to MinIO: {}", objectName, e);
            throw new StorageException("Failed to upload file: " + objectName, e);
        }
    }

    /**
     * MinIO에서 파일을 다운로드
     *
     * @param objectName The object key/path in MinIO
     * @return InputStream of the file content (caller must close)
     * @throws StorageException if download fails or object not found
     */
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new StorageException("File not found: " + objectName);
            }
            throw new StorageException("Failed to download file: " + objectName, e);
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", objectName, e);
            throw new StorageException("Failed to download file: " + objectName, e);
        }
    }

    /**
     * MinIO에서 파일을 삭제
     * 
     * <p>재시도 정책: 네트워크 장애 등으로 실패 시 최대 3회 재시도 (1초 간격)</p>
     *
     * @param objectName The object key/path in MinIO
     * @throws StorageException 재시도 후에도 실패 시
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            log.info("File deleted from MinIO: {}/{}", bucket, objectName);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectName, e);
            throw new StorageException("Failed to delete file: " + objectName, e);
        }
    }

    /**
     * Presigned URL 생성 (임시 접근 URL)
     *
     * @param objectName The object key/path in MinIO
     * @return Presigned URL valid for configured duration (default 1 hour)
     * @throws StorageException if URL generation fails
     */
    public String generatePresignedUrl(String objectName) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(presignedUrlExpiry, TimeUnit.SECONDS)
                            .build()
            );
            log.debug("Generated presigned URL for {}, expiry: {} seconds", objectName, presignedUrlExpiry);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", objectName, e);
            throw new StorageException("Failed to generate presigned URL: " + objectName, e);
        }
    }

    /**
     * 객체 존재 여부 확인
     *
     * @param objectName The object key/path in MinIO
     * @return true if object exists, false otherwise
     */
    public boolean objectExists(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw new StorageException("Failed to check object existence: " + objectName, e);
        } catch (Exception e) {
            log.error("Failed to check object existence in MinIO: {}", objectName, e);
            throw new StorageException("Failed to check object existence: " + objectName, e);
        }
    }
}
