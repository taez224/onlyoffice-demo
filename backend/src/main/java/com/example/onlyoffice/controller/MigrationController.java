package com.example.onlyoffice.controller;

import com.example.onlyoffice.service.FileMigrationService;
import com.example.onlyoffice.service.MigrationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Migration Controller
 * 기존 파일들을 Document 레코드로 마이그레이션하는 관리자용 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final FileMigrationService migrationService;

    /**
     * 기존 storage/ 디렉토리의 파일들을 스캔하여 Document 레코드 생성
     *
     * POST /api/admin/migration/files
     *
     * @return 마이그레이션 결과 리포트
     */
    @PostMapping("/files")
    public ResponseEntity<MigrationReport> migrateFiles() {
        log.info("File migration triggered via API");

        try {
            MigrationReport report = migrationService.migrateExistingFiles();
            log.info("Migration completed successfully: {}", report);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Migration failed", e);
            throw new RuntimeException("Migration failed: " + e.getMessage(), e);
        }
    }
}
