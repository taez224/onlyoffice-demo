package com.example.onlyoffice.repository;

import com.example.onlyoffice.entity.Document;
import com.example.onlyoffice.entity.DocumentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Document entity 작업을 위한 Repository 인터페이스.
 * CRUD 작업과 soft delete 지원을 위한 커스텀 쿼리를 제공합니다.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {


    /**
     * 파일명으로 문서 조회 (soft delete된 문서 제외)
     * EditorController에서 에디터 설정 생성 시 사용
     *
     * @param fileName 파일명
     * @return 문서가 존재하고 삭제되지 않았으면 해당 문서를 포함한 Optional
     */
    Optional<Document> findByFileNameAndDeletedAtIsNull(String fileName);

    // ==================== 기본 조회 메서드 ====================

    /**
     * 고유한 file key (ONLYOFFICE 문서 키)로 문서 조회
     * 주로 callback 처리에서 사용
     *
     * @param fileKey 고유한 ONLYOFFICE 문서 키
     * @return 문서가 존재하면 해당 문서를 포함한 Optional
     */
    Optional<Document> findByFileKey(String fileKey);

    /**
     * ID로 문서 조회 (soft delete된 문서 제외)
     * 단일 활성 문서를 조회하는 주요 메서드
     *
     * @param id 문서 ID
     * @return 문서가 존재하고 삭제되지 않았으면 해당 문서를 포함한 Optional
     */
    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    /**
     * file key로 문서 조회 (soft delete된 문서 제외)
     *
     * @param fileKey 고유한 ONLYOFFICE 문서 키
     * @return 문서가 존재하고 삭제되지 않았으면 해당 문서를 포함한 Optional
     */
    Optional<Document> findByFileKeyAndDeletedAtIsNull(String fileKey);

    /**
     * 비관적 락으로 문서를 조회 (Saga delete 흐름에서 사용)
     *
     * @param id 문서 ID
     * @return 잠금이 걸린 문서 Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Document> findWithLockById(Long id);

    // ==================== 목록 조회 메서드 (Soft Delete 필터) ====================

    /**
     * 삭제되지 않은 모든 문서를 정렬하여 조회
     *
     * @param sort 정렬 조건
     * @return 삭제되지 않은 문서 목록
     */
    List<Document> findAllByDeletedAtIsNull(Sort sort);

    /**
     * 삭제되지 않은 모든 문서를 페이지네이션하여 조회
     *
     * @param pageable 페이지네이션 조건
     * @return 삭제되지 않은 문서 페이지
     */
    Page<Document> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * 상태별로 문서를 조회 (soft delete된 문서 제외)
     *
     * @param status 필터링할 문서 상태
     * @param sort   정렬 조건
     * @return 조건에 맞는 문서 목록
     */
    List<Document> findByStatusAndDeletedAtIsNull(DocumentStatus status, Sort sort);

    /**
     * 상태별로 문서를 페이지네이션하여 조회 (soft delete된 문서 제외)
     *
     * @param status   필터링할 문서 상태
     * @param pageable 페이지네이션 조건
     * @return 조건에 맞는 문서 페이지
     */
    Page<Document> findByStatusAndDeletedAtIsNull(DocumentStatus status, Pageable pageable);

    // ==================== 존재 여부 확인 ====================

    /**
     * file key가 이미 존재하는지 확인 (고유성 검증용)
     *
     * @param fileKey 확인할 file key
     * @return file key가 존재하면 true
     */
    boolean existsByFileKey(String fileKey);

    /**
     * 문서가 존재하고 soft delete되지 않았는지 확인
     *
     * @param id 문서 ID
     * @return 문서가 존재하고 활성 상태면 true
     */
    boolean existsByIdAndDeletedAtIsNull(Long id);

    // ==================== Soft Delete 작업 ====================

    /**
     * deletedAt 타임스탬프를 설정하여 문서를 soft delete
     * 벌크 업데이트 효율성을 위해 JPQL 사용
     *
     * @param id        문서 ID
     * @param deletedAt 삭제 시각
     * @return 업데이트된 행 수 (0 또는 1)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Document d SET d.deletedAt = :deletedAt, d.status = 'DELETED' WHERE d.id = :id AND d.deletedAt IS NULL")
    int softDelete(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * soft delete된 문서 복원
     *
     * @param id 문서 ID
     * @return 업데이트된 행 수 (0 또는 1)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Document d SET d.deletedAt = NULL, d.status = 'ACTIVE' WHERE d.id = :id AND d.deletedAt IS NOT NULL")
    int restore(@Param("id") Long id);

    // ==================== 통계 쿼리 ====================

    /**
     * 상태별 문서 수 카운트 (soft delete 제외)
     * 대시보드 통계에 유용
     *
     * @param status 카운트할 상태
     * @return 해당 상태의 문서 수
     */
    long countByStatusAndDeletedAtIsNull(DocumentStatus status);

    /**
     * 삭제되지 않은 모든 문서 수 카운트
     *
     * @return 활성 문서 수
     */
    long countByDeletedAtIsNull();

    // ==================== 검색 메서드 ====================

    /**
     * 파일명 패턴으로 문서 검색 (대소문자 구분 없음)
     *
     * @param fileNamePattern 검색 패턴 (와일드카드는 % 사용)
     * @param pageable        페이지네이션 조건
     * @return 조건에 맞는 문서 페이지
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.fileName) LIKE LOWER(:pattern) AND d.deletedAt IS NULL")
    Page<Document> searchByFileName(@Param("pattern") String fileNamePattern, Pageable pageable);
}
