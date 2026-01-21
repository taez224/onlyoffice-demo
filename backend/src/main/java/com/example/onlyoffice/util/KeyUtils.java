package com.example.onlyoffice.util;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ONLYOFFICE document.key 생성 및 검증 유틸리티.
 * <p>
 * OnlyOffice 스펙:
 * - key 최대 길이: 128자
 * - 특수문자 사용 불가 (영문, 숫자, _, - 만 허용)
 * - 저장 시마다 새로운 key 생성 필요
 */
public final class KeyUtils {

    /**
     * OnlyOffice document.key 최대 길이
     */
    public static final int MAX_KEY_LENGTH = 128;

    /**
     * 허용되는 문자 패턴 (영문, 숫자, _, -)
     */
    private static final Pattern SAFE_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");

    /**
     * 해시 길이 (MD5 일부 사용)
     */
    private static final int HASH_LENGTH = 16;

    /**
     * UUID 정규식 (소문자만 허용)
     * UUID.randomUUID()는 소문자를 생성하므로 소문자만 허용합니다.
     * 다른 클래스에서 Bean Validation @Pattern 등에 사용 가능.
     */
    public static final String UUID_REGEX = "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$";

    /**
     * UUID 패턴 (내부 검증용)
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX);

    private KeyUtils() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 안전한 document.key 생성
     *
     * @param fileKey 파일 고유 식별자 (referenceData.fileKey)
     * @param version 편집 세션 버전
     * @return OnlyOffice 스펙에 맞는 안전한 key
     */
    public static String generateEditorKey(String fileKey, int version) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey cannot be null or blank");
        }

        String raw = fileKey + "_v" + version;
        String safe = sanitize(raw);

        if (safe.length() > MAX_KEY_LENGTH) {
            // 길이 초과 시 해시 기반 축약
            return generateHashBasedKey(fileKey, version);
        }

        return safe;
    }

    /**
     * 특수문자 제거
     *
     * @param input 원본 문자열
     * @return 안전한 문자만 포함된 문자열
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return SAFE_CHARS_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * key가 유효한지 검증
     *
     * @param key 검증할 key
     * @return 유효하면 true
     */
    public static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        // 허용되지 않는 문자가 있는지 확인
        return !SAFE_CHARS_PATTERN.matcher(key).find();
    }

    /**
     * 해시 기반 축약 key 생성
     * 긴 fileKey를 MD5 해시로 축약
     *
     * @param fileKey 파일 고유 식별자
     * @param version 편집 세션 버전
     * @return 해시 기반 축약 key
     */
    private static String generateHashBasedKey(String fileKey, int version) {
        String hash = DigestUtils.md5DigestAsHex(
                fileKey.getBytes(StandardCharsets.UTF_8)
        ).substring(0, HASH_LENGTH);

        return hash + "_v" + version;
    }

    /**
     * UUID 기반 fileKey 생성 (신규 문서용)
     * <p>
     * UUID는 충돌 위험이 없고 보안적으로 예측 불가능하여
     * 문서의 고유 식별자로 적합합니다.
     *
     * @return UUID 기반 고유한 fileKey (예: "550e8400-e29b-41d4-a716-446655440000")
     */
    public static String generateFileKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * fileKey가 유효한 UUID 형식인지 검증.
     * <p>
     * ONLYOFFICE 연동에서 fileKey는 UUID 형식이어야 합니다.
     * 소문자 UUID만 허용합니다 (UUID.randomUUID()가 소문자 생성).
     *
     * @param fileKey 검증할 fileKey
     * @return 유효한 UUID 형식이면 true
     */
    public static boolean isValidFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return false;
        }
        return UUID_PATTERN.matcher(fileKey).matches();
    }
}