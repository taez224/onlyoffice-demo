package com.example.onlyoffice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "documents",
        indexes = {
                // Sorting index
                @Index(name = "idx_created_at", columnList = "created_at"),
                // Composite indexes for soft delete query optimization
                @Index(name = "idx_file_key_deleted_at", columnList = "file_key, deleted_at"),
                @Index(name = "idx_file_name_deleted_at", columnList = "file_name, deleted_at"),
                @Index(name = "idx_status_deleted_at", columnList = "status, deleted_at")
        }
)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be less than 255 characters")
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NaturalId
    @NotBlank(message = "File key is required")
    @Size(max = 255, message = "File key must be less than 255 characters")
    @Column(name = "file_key", nullable = false, unique = true, length = 255)
    private String fileKey;

    @NotNull(message = "Editor version is required")
    @Column(name = "editor_version", nullable = false)
    @Builder.Default
    private Integer editorVersion = 0;

    @NotBlank(message = "File type is required")
    @Size(max = 50, message = "File type must be less than 50 characters")
    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @NotBlank(message = "Document type is required")
    @Size(max = 20, message = "Document type must be less than 20 characters")
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @NotBlank(message = "Storage path is required")
    @Size(max = 500, message = "Storage path must be less than 500 characters")
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @NotBlank(message = "Created by is required")
    @Size(max = 100, message = "Created by must be less than 100 characters")
    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "anonymous";

    public String getEditorKey() {
        return fileKey + "_v" + editorVersion;
    }

    public void incrementEditorVersion() {
        this.editorVersion++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document that)) return false;
        return fileKey != null && fileKey.equals(that.fileKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileKey);
    }
}
