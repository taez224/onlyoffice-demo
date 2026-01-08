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

import java.util.List;
import java.util.Optional;

/**
 * 문서 엔티티 저장소.
 *
 * <p>Hibernate 7의 {@code @SoftDelete} 어노테이션과 연동되어, 모든 조회 쿼리에서
 * 삭제된 문서(deleted_at IS NOT NULL)가 자동으로 필터링됩니다.</p>
 *
 * <h3>주요 특징</h3>
 * <ul>
 *   <li><b>Soft Delete 자동 필터링</b>: 별도 조건 없이도 삭제된 문서 제외</li>
 *   <li><b>비관적 락</b>: 동시성 제어를 위한 PESSIMISTIC_WRITE 락 지원</li>
 *   <li><b>복원 기능</b>: native query를 통한 삭제된 문서 복원</li>
 * </ul>
 *
 * @see com.example.onlyoffice.entity.Document
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 파일명으로 문서를 조회합니다.
     * 동일한 파일명이 여러 개 존재할 수 있으므로 첫 번째 일치 항목을 반환합니다.
     */
    Optional<Document> findByFileName(String fileName);

    /**
     * UUID 기반 fileKey로 문서를 조회합니다.
     * fileKey는 고유하므로 정확히 하나의 문서를 반환합니다.
     */
    Optional<Document> findByFileKey(String fileKey);

    /**
     * ID로 문서를 조회하면서 비관적 쓰기 락을 획득합니다.
     * 삭제 작업 시 동시성 충돌을 방지하기 위해 사용됩니다.
     *
     * @implNote 락 타임아웃: 3초. 타임아웃 초과 시 PessimisticLockException 발생
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Document> findWithLockById(Long id);

    /**
     * fileKey로 문서를 조회하면서 비관적 쓰기 락을 획득합니다.
     * ONLYOFFICE 콜백 처리 시 파일 덮어쓰기 경쟁 조건을 방지합니다.
     *
     * @implNote 동일 문서에 대한 SAVE/FORCESAVE 콜백이 동시에 도착할 때 순차 처리 보장
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Document> findWithLockByFileKey(String fileKey);

    /**
     * 특정 상태의 문서 목록을 정렬하여 조회합니다.
     * 주로 ACTIVE 상태 문서를 생성일 역순으로 조회하는 데 사용됩니다.
     */
    List<Document> findAllByStatus(DocumentStatus status, Sort sort);

    /**
     * 특정 상태의 문서 목록을 페이징하여 조회합니다.
     */
    Page<Document> findAllByStatus(DocumentStatus status, Pageable pageable);

    /**
     * 해당 fileKey를 가진 문서가 존재하는지 확인합니다.
     * 중복 키 검사에 사용됩니다.
     */
    boolean existsByFileKey(String fileKey);

    /**
     * 특정 상태의 문서 개수를 반환합니다.
     * 대시보드 통계 등에 활용됩니다.
     */
    long countByStatus(DocumentStatus status);

    /**
     * 파일명 패턴으로 문서를 검색합니다 (대소문자 무시).
     *
     * @param fileNamePattern LIKE 패턴 (예: "%report%")
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.fileName) LIKE LOWER(:pattern)")
    Page<Document> searchByFileName(@Param("pattern") String fileNamePattern, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE documents SET deleted_at = NULL, status = :status WHERE id = :id", nativeQuery = true)
    int restoreWithStatusInternal(@Param("id") Long id, @Param("status") String status);

    default int restoreWithStatus(Long id, DocumentStatus status) {
        return restoreWithStatusInternal(id, status.name());
    }
}
