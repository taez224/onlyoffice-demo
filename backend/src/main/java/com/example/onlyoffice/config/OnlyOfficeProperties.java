package com.example.onlyoffice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ONLYOFFICE 설정 프로퍼티
 * application.yml의 onlyoffice 설정을 바인딩
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    /**
     * ONLYOFFICE Document Server URL
     */
    @NotBlank(message = "ONLYOFFICE URL은 필수입니다")
    private String url;

    /**
     * JWT Secret (최소 32자)
     * HS256 알고리즘 요구사항
     */
    @NotBlank(message = "JWT Secret은 필수입니다")
    @Size(min = 32, message = "JWT Secret은 최소 32자 이상이어야 합니다")
    private String secret;

    /**
     * MinIO Presigned URL 사용 여부
     * true: Document Server가 MinIO에서 직접 파일 다운로드 (presigned URL 사용)
     * false: Document Server가 백엔드 프록시를 통해 파일 다운로드 (기본값)
     */
    private boolean usePresignedUrls = false;

    /**
     * MinIO External Endpoint
     * ONLYOFFICE Document Server에서 접근 가능한 MinIO 엔드포인트
     * Docker 환경: http://minio:9000 (내부 네트워크 호스트명)
     * 프로덕션: 외부 접근 가능한 MinIO URL
     */
    private String minioExternalEndpoint = "http://minio:9000";
}
