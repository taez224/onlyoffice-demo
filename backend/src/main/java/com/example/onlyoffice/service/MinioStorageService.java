package com.example.onlyoffice.service;

import com.example.onlyoffice.exception.StorageException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

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
     * @param file MultipartFile from HTTP request
     * @param objectName The object key/path in MinIO (e.g., "documents/doc-123.docx")
     * @return The object name (can be used as storagePath in Document entity)
     * @throws StorageException if upload fails
     */
    public String uploadFile(MultipartFile file, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("File uploaded to MinIO: {}/{}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", objectName, e);
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
     * @param objectName The object key/path in MinIO
     * @throws StorageException if deletion fails
     */
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
