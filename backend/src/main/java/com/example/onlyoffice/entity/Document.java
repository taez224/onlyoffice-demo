package com.example.onlyoffice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 시스템에 저장된 문서를 나타내는 JPA Entity.
 * deletedAt 필드를 통한 soft delete와 version을 통한 낙관적 락을 지원합니다.
 */
@Entity
@Table(
    name = "documents",
    indexes = {
        @Index(name = "idx_file_key", columnList = "file_key"),
        @Index(name = "idx_file_name", columnList = "file_name"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_deleted_at", columnList = "deleted_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    /**
     * Primary key (PostgreSQL BIGSERIAL로 자동 생성)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자가 업로드한 원본 파일명
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * ONLYOFFICE referenceData용 파일 식별자 (불변)
     * 문서 생성 시 한 번 생성되며 변경되지 않음
     * 외부 데이터 참조, 파일 링크 등에 사용
     */
    @Column(name = "file_key", nullable = false, unique = true, length = 255)
    private String fileKey;

    /**
     * ONLYOFFICE 편집 세션 버전 (가변)
     * 문서가 편집되고 저장될 때마다 증가
     * document.key 생성에 사용: fileKey + "_v" + editorVersion
     */
    @Column(name = "editor_version", nullable = false)
    @Builder.Default
    private Integer editorVersion = 0;

    /**
     * 파일 확장자 (예: docx, xlsx, pptx, pdf)
     */
    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    /**
     * ONLYOFFICE 문서 타입 (word, cell, slide)
     * 어떤 에디터 컴포넌트를 사용할지 결정
     */
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    /**
     * 파일 크기 (bytes)
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MinIO object key (버킷 내 경로)
     */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /**
     * 문서 생명주기 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    /**
     * 낙관적 락을 위한 버전 번호
     * JPA에 의해 업데이트 시 자동 증가
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * 문서 생성 시각
     * Hibernate에 의해 자동 설정
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 문서 최종 수정 시각
     * Hibernate에 의해 변경 시마다 자동 업데이트
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Soft delete 시각 - NULL이면 삭제되지 않은 문서
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 문서를 생성한 사용자 식별자
     */
    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "anonymous";

    /**
     * ONLYOFFICE document.key 생성
     * 편집 세션 식별에 사용되며, 저장 후에는 editorVersion이 증가하여 새 key가 생성됨
     * 
     * @return fileKey + "_v" + editorVersion 형식의 편집 세션 key
     */
    public String getEditorKey() {
        return fileKey + "_v" + editorVersion;
    }

    /**
     * 편집 세션 버전 증가
     * 문서가 성공적으로 저장된 후 호출하여 다음 편집 세션을 위한 새 key 생성
     */
    public void incrementEditorVersion() {
        this.editorVersion++;
    }
}
