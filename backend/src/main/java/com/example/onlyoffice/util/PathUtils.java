package com.example.onlyoffice.util;

import java.nio.file.Path;

/**
 * Path Traversal 공격 방어를 위한 경로 유틸리티
 */
public final class PathUtils {

    private PathUtils() {
        // Utility class
    }

    /**
     * Path Traversal 공격 방어를 위한 경로 검증 및 해석
     *
     * @param rootLocation 기준 디렉토리 (절대 경로)
     * @param relativePath 검증할 상대 경로
     * @return 정규화된 안전한 절대 경로
     * @throws SecurityException Path Traversal 시도 감지 시
     */
    public static Path validateAndResolve(Path rootLocation, String relativePath) {
        Path resolvedPath = rootLocation.resolve(relativePath).normalize();

        if (!resolvedPath.startsWith(rootLocation.normalize())) {
            throw new SecurityException("Path traversal attempt detected: " + relativePath);
        }

        return resolvedPath;
    }
}
