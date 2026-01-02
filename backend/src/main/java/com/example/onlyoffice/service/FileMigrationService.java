package com.example.onlyoffice.service;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import com.example.onlyoffice.repository.DocumentRepository;
import com.example.onlyoffice.util.KeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * File Migration Service
 * 기존 storage/ 디렉토리의 파일들을 스캔하여 Document 레코드 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMigrationService {

    private final DocumentRepository documentRepository;

    @Value("${storage.path}")
    private String storagePath;

    /**
     * 기존 파일들을 스캔하여 Document 레코드 생성 (마이그레이션)
     *
     * @return 마이그레이션 결과 리포트
     */
    @Transactional
    public MigrationReport migrateExistingFiles() {
        log.info("Starting file migration from storage directory: {}", storagePath);

        Path storageDir = Paths.get(storagePath);
        MigrationReport report = new MigrationReport();

        if (!Files.exists(storageDir)) {
            log.warn("Storage directory does not exist: {}", storageDir);
            return report;
        }

        try (Stream<Path> files = Files.walk(storageDir, 1)) {
            files.filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .forEach(path -> {
                        try {
                            migrateFile(path, report);
                        } catch (Exception e) {
                            log.error("Failed to migrate file: {}", path, e);
                            report.addFailure(path.getFileName().toString(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan storage directory", e);
            throw new RuntimeException("Migration failed: Unable to scan directory", e);
        }

        log.info("Migration completed: {}", report);
        return report;
    }

    /**
     * 단일 파일을 마이그레이션
     *
     * @param filePath 파일 경로
     * @param report   마이그레이션 리포트
     */
    private void migrateFile(Path filePath, MigrationReport report) {
        String fileName = filePath.getFileName().toString();

        // 이미 DB에 존재하는 파일은 스킵
        if (documentRepository.findByFileNameAndDeletedAtIsNull(fileName).isPresent()) {
            log.debug("Document already exists for fileName: {}, skipping", fileName);
            report.addSkipped(fileName);
            return;
        }

        try {
            // UUID 기반 fileKey 생성
            String fileKey = KeyUtils.generateFileKey();

            // 파일 정보 추출
            String extension = getFileExtension(fileName);
            String documentType = determineDocumentType(extension);
            long fileSize = Files.size(filePath);

            // Document 엔티티 생성
            Document document = Document.builder()
                    .fileName(fileName)
                    .fileKey(fileKey)
                    .fileType(extension)
                    .documentType(documentType)
                    .fileSize(fileSize)
                    .storagePath(fileName)  // 파일시스템에서 fileName 그대로 유지
                    .status(DocumentStatus.ACTIVE)
                    .editorVersion(0)  // 초기 버전
                    .createdBy("migration")  // 마이그레이션에 의해 생성됨을 표시
                    .build();

            documentRepository.save(document);
            log.info("Successfully migrated: {} -> fileKey: {}", fileName, fileKey);
            report.addSuccess(fileName, fileKey);

        } catch (Exception e) {
            log.error("Error migrating file: {}", fileName, e);
            report.addFailure(fileName, e.getMessage());
        }
    }

    /**
     * 파일 확장자 추출
     *
     * @param fileName 파일명
     * @return 확장자 (소문자)
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 파일 확장자로부터 ONLYOFFICE document type 결정
     *
     * @param extension 파일 확장자
     * @return document type (word, cell, slide)
     */
    private String determineDocumentType(String extension) {
        return switch (extension) {
            case "docx", "doc", "odt", "txt", "hwp" -> "word";
            case "xlsx", "xls", "xlsm", "ods", "csv" -> "cell";
            case "pptx", "ppt", "odp" -> "slide";
            case "pdf" -> "pdf";
            default -> {
                log.warn("Unknown file extension: {}, defaulting to 'word'", extension);
                yield "word";
            }
        };
    }
}
