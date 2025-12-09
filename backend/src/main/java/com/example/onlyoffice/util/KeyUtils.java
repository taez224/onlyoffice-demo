package com.example.onlyoffice.util;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * ONLYOFFICE document.key 생성 및 검증 유틸리티.
 *
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
     * 파일명과 타임스탬프로 fileKey 생성 (신규 문서용)
     *
     * @param fileName 파일명
     * @param timestamp 생성 시각 (밀리초)
     * @return 고유한 fileKey
     */
    public static String generateFileKey(String fileName, long timestamp) {
        String base = sanitize(fileName) + "_" + timestamp;

        if (base.length() > MAX_KEY_LENGTH - 10) {
            // 버전 접미사 공간 확보
            String hash = DigestUtils.md5DigestAsHex(
                fileName.getBytes(StandardCharsets.UTF_8)
            ).substring(0, HASH_LENGTH);
            return hash + "_" + timestamp;
        }

        return base;
    }
}