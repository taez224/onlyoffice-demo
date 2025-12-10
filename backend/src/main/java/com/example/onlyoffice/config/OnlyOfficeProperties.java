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
}
