package com.example.onlyoffice.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 마이그레이션 결과 리포트
 */
@Data
public class MigrationReport {
    private List<MigrationSuccess> successes = new ArrayList<>();
    private List<String> skipped = new ArrayList<>();
    private List<MigrationFailure> failures = new ArrayList<>();

    public void addSuccess(String fileName, String fileKey) {
        successes.add(new MigrationSuccess(fileName, fileKey));
    }

    public void addSkipped(String fileName) {
        skipped.add(fileName);
    }

    public void addFailure(String fileName, String error) {
        failures.add(new MigrationFailure(fileName, error));
    }

    @Override
    public String toString() {
        return String.format("MigrationReport[success=%d, skipped=%d, failed=%d]",
            successes.size(), skipped.size(), failures.size());
    }
}

/**
 * 마이그레이션 성공 항목
 */
@Data
@AllArgsConstructor
class MigrationSuccess {
    private String fileName;
    private String fileKey;
}

/**
 * 마이그레이션 실패 항목
 */
@Data
@AllArgsConstructor
class MigrationFailure {
    private String fileName;
    private String error;
}
