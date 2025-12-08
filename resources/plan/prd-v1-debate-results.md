# PM 에이전트 토론: ONLYOFFICE Demo PRD 분석
## 20 라운드 심층 토론

**날짜**: 2025-12-02
**대상 문서**: prd-v1.md
**참여자**:
- 🚀 **Optimistic PM** (속도 중심, 낙관적)
- ⚠️ **Critical PM** (위험 중심, 비판적)

---

## 🔍 사전 조사 결과

### Optimistic PM의 조사
- ONLYOFFICE Java SDK 1.5.0 사용 가능, Maven Central 배포 완료
- TanStack 생태계 성숙도 높음 (Query, Router, Table 모두 안정)
- Spring Boot + PostgreSQL + S3 조합은 검증된 아키텍처
- Zero-downtime deployment 지원으로 무중단 배포 가능

### Critical PM의 조사
- **보안 취약점**: ONLYOFFICE에 JWT 기본키 사용 시 인증 우회 취약점 발견 (CVE 등록됨)
- **S3 Ninja 위험**: 프로덕션용이 아님, MinIO도 최근 데이터 손실 이슈 보고됨
- **TanStack Router 한계**: Next.js와 함께 사용 시 SSR 이점 상실
- **SDK 문서 부족**: 공식 문서에서 "테스트용이며 프로덕션에서 사용 금지" 경고

---

## 🥊 Round 1: 전체 아키텍처 방향성

### 🚀 Optimistic PM
"PRD의 As-Is → To-Be 전환은 완벽합니다! 수동 Config 생성에서 SDK 기반으로 가는 건 당연한 수순이죠. 1편의 한계를 정확히 파악하고 있고, Object Storage + DB 조합으로 확장성을 확보하는 전략이 탁월합니다. 이 아키텍처면 10명 동시 편집은 물론, 향후 100명까지도 무리 없어요."

**근거**:
- Spring Boot + PostgreSQL은 수백만 레코드 처리 검증됨 ([출처](https://thamizhelango.medium.com/building-scalable-applications-with-postgresql-and-spring-boot-a-comprehensive-guide-82ee6d5b3049))
- S3 기반 파일 저장소는 무한 확장 가능 ([출처](https://reflectoring.io/spring-boot-s3/))

### ⚠️ Critical PM
"아키텍처 '방향'은 맞지만, 세부 선택에 치명적 결함이 있습니다. **S3 Ninja는 프로덕션용이 아닙니다.** 2024년 현재 프로덕션 S3 대안 논의에서 S3 Ninja는 언급조차 안 됩니다. MinIO, Garage, SeaweedFS가 대세인데, PRD에는 S3 Ninja → AWS S3 전환만 언급하고 있어요. 로컬 개발용으로만 쓰고 실제로는 처음부터 MinIO를 써야 합니다."

**근거**:
- S3 Ninja는 2024년 프로덕션 논의에서 제외됨 ([출처](https://lowendtalk.com/discussion/210203/minio-vs-garage-vs-seaweedfs-vs-others-are-you-using-any-in-production))
- MinIO도 데이터 손실 이슈 보고됨 ([출처](https://dev.to/oumnya/minio-the-open-source-s3-alternative-that-cuts-costs-and-boosts-flexibility-348g))

**판정**: Critical PM 우세 - S3 Ninja 선택은 재고 필요

---

## 🥊 Round 2: ONLYOFFICE SDK 도입 시기

### 🚀 Optimistic PM
"SDK를 2편에서 도입하는 건 최적의 타이밍입니다! 1편에서 수동 구현의 한계를 체험했으니, 2편에서 표준화하는 게 자연스러운 진화죠. SDK 사용법도 간단하고 (Maven Central에 있음), ConfigService와 CallbackManager만 잘 쓰면 됩니다. 3일이면 충분히 적용 가능해요."

**장점**:
- 베스트 프랙티스 자동 적용
- JWT 서명 로직 내장
- 유지보수 부담 감소

### ⚠️ Critical PM
"SDK 도입은 찬성하지만, **공식 문서의 경고를 무시하고 있습니다**. ONLYOFFICE 자체가 '이 예제를 프로덕션에서 쓰지 말라'고 명시했어요. SDK를 쓰더라도 다음을 추가 구현해야 합니다:

1. **Storage 보호**: 기본 예제는 인증 없음
2. **파일 저장 요청 검증**: Callback 데이터 무결성 체크 없음
3. **커스터마이징**: 기본 Manager들을 그대로 쓰면 안 됨"

**근거**: [ONLYOFFICE 공식 경고](https://api.onlyoffice.com/editors/example/javaspring)

**판정**: 무승부 - SDK 도입은 맞지만 추가 보안 구현 필수

---

## 🥊 Round 3: 데이터베이스 설계

### 🚀 Optimistic PM
"`documents` 테이블 설계가 깔끔합니다! 필요한 필드만 담고, 인덱스도 적절해요. 특히 `file_key`를 UNIQUE로 잡아서 중복 방지하고, `created_at` 인덱스로 정렬 최적화한 건 훌륭합니다. JPA로 자동 생성하면 테이블 하나 만드는 데 10분이면 끝나요."

**설계 장점**:
- 정규화 적절
- 필수 필드만 포함
- 확장 가능 (3편에서 버전 테이블 추가 예정)

### ⚠️ Critical PM
"기본 설계는 양호하지만, **누락된 필드들이 있습니다**:

1. **deleted_at (soft delete)**: 실수로 삭제한 문서 복구 불가
2. **version**: 동시성 제어 (Optimistic Locking)
3. **mime_type**: file_type만으로 MIME 타입 추론 시 보안 취약점
4. **file_hash (SHA-256)**: 파일 무결성 검증
5. **access_level**: 권한 관리 (3편이 아니라 2편에 기초는 필요)

그리고 `file_size BIGINT`는 좋은데, **50MB 제한**은 너무 작습니다. 요즘 PPT 파일은 이미지 많으면 100MB 넘어요."

**판정**: Critical PM 우세 - 추가 필드 검토 필요

---

## 🥊 Round 4: 파일 업로드 보안

### 🚀 Optimistic PM
"PRD의 파일 검증 로직은 실용적입니다:
- 확장자 검증 (docx, xlsx, pptx, pdf)
- 크기 제한 (50MB)
- MIME 타입 백엔드 검증

이 정도면 일반적인 악의적 업로드는 막을 수 있어요. 바이러스 스캔까지 하자면 ClamAV 같은 걸 붙여야 하는데, 2편에서는 과도합니다. 3편이나 실제 프로덕션 배포 시에 추가하면 돼요."

### ⚠️ Critical PM
"**현재 검증은 우회 가능합니다**. 구체적으로:

1. **매직 바이트 검증 없음**: 파일 확장자만 체크하면 `virus.exe.docx`로 우회 가능
2. **파일명 새니타이징 없음**: Path Traversal 공격 가능 (`../../etc/passwd.docx`)
3. **압축 폭탄 방어 없음**: 1KB 압축 파일이 1GB로 팽창하면 서버 다운
4. **Content-Type 검증 누락**: Multipart 요청의 Content-Type도 검증해야 함

최소한 **Apache Tika로 실제 MIME 타입 검증**은 2편에서 해야 합니다."

**근거**: Spring Boot 파일 업로드 보안 베스트 프랙티스 ([출처](https://reflectoring.io/spring-boot-s3/))

**판정**: Critical PM 승 - 업로드 검증 강화 필수

---

## 🥊 Round 5: TanStack 스택 선택

### 🚀 Optimistic PM
"TanStack Query + Table 조합은 2024년 최신 베스트 프랙티스입니다! 특히:

- **TanStack Query**: 서버 상태 관리의 정석, 자동 캐싱/리페칭
- **TanStack Table**: 헤드리스 테이블로 완전한 커스터마이징 가능
- **React 19 호환**: 최신 React와 완벽 동작

문서 목록에서 CRUD 하려면 이보다 좋은 조합이 없어요. 개발 속도도 빠릅니다."

**근거**: TanStack 생태계 가이드 ([출처](https://void.ma/en/publications/tanstack-react-query-table-router-guide-complet-2025/))

### ⚠️ Critical PM
"TanStack Query와 Table은 좋지만, **TanStack Router는 문제가 있습니다**. PRD가 Next.js 16을 쓴다고 했는데, Next.js와 TanStack Router는 충돌합니다:

> TanStack Router is not designed to be used with full-stack frameworks and is best suited for client-side only apps. You can use client-side routers inside of Next and other full-stack frameworks, but you are essentially opting out of the SSR patterns and benefits you get from their routers.

**Next.js App Router를 버리고 TanStack Router를 쓰면 SSR 이점이 사라집니다.** 그냥 Next.js의 기본 `useRouter`를 쓰세요."

**근거**: [TanStack Router 공식 문서](https://github.com/TanStack/router/discussions/346)

**판정**: Critical PM 승 - TanStack Router 제거 필요

---

## 🥊 Round 6: JWT 보안 (3편 연기 여부)

### 🚀 Optimistic PM
"JWT Callback 검증을 3편으로 미루는 건 합리적입니다. 2편은 SDK 도입과 DB/S3 연동에 집중하고, 보안 강화는 3편에서 하면 돼요. 로컬 개발 환경에서는 JWT 없이도 충분히 테스트 가능하고, 어차피 외부에 노출 안 할 거잖아요?"

**장점**:
- 2편 범위 축소로 일정 준수
- 핵심 기능에 집중
- 점진적 개선

### ⚠️ Critical PM
"**절대 안 됩니다.** JWT 검증 누락은 치명적입니다. 실제 취약점 사례를 보세요:

> ONLYOFFICE versions as of 2021-11-08 were affected by Incorrect Access Control, where an attacker could authenticate with the web socket service of the document editor protected by JWT auth by using a default JWT signing key.

**공격 시나리오**:
1. 공격자가 Callback URL에 직접 POST 요청
2. 가짜 `status: 2`와 악의적 파일 URL 전송
3. 백엔드가 검증 없이 파일 다운로드 후 S3에 저장
4. 원본 문서가 멀웨어로 교체됨

**JWT 검증은 2편에 반드시 포함해야 합니다.** SDK에 이미 구현되어 있어요!"

**근거**: [ONLYOFFICE 취약점](https://www.cvedetails.com/vulnerability-list/vendor_id-22843/Onlyoffice.html)

**판정**: Critical PM 압승 - JWT 검증은 2편에 필수

---

## 🥊 Round 7: 성능 요구사항 현실성

### 🚀 Optimistic PM
"PRD의 성능 목표는 도전적이지만 달성 가능합니다:

| 항목 | 목표 | 달성 가능성 |
|------|------|------------|
| 문서 목록 로딩 | < 500ms | ✅ DB 쿼리만, 인덱스 있음 |
| 파일 업로드 (10MB) | < 3초 | ✅ 로컬 S3면 충분 |
| 에디터 초기 렌더링 | < 2초 | ✅ ONLYOFFICE 자체 성능 |
| Callback 처리 | < 1초 | ✅ 단순 파일 복사 |

특히 로컬 환경에서는 네트워크 레이턴시가 없어서 더 빨라요!"

### ⚠️ Critical PM
"목표는 좋지만 **측정 방법이 모호합니다**:

1. **'에디터 로드 완료'의 정의는?**
   - DOM 렌더링 완료?
   - 문서 내용 표시 완료?
   - 편집 가능 상태?

2. **Callback 처리 1초는 비현실적**:
   - ONLYOFFICE에서 편집 파일 다운로드: 네트워크 I/O
   - S3에 업로드: 또 네트워크 I/O
   - DB 업데이트: 트랜잭션
   - 10MB 파일이면 1초 안에 불가능

3. **동시 사용자 고려 없음**: 10명이 동시에 저장하면?

**제안**: 목표를 **백분위수(P95, P99)**로 정의하고, **부하 테스트 결과**를 포함하세요."

**판정**: Critical PM 우세 - 성능 측정 기준 구체화 필요

---

## 🥊 Round 8: 프론트엔드 상태 관리

### 🚀 Optimistic PM
"TanStack Query의 캐시 키 전략이 심플하고 효과적입니다:

```typescript
['documents']              // 전체 목록
['editorConfig', id]       // 문서별 설정
```

Mutation 후 자동 invalidation으로 UI가 항상 최신 상태를 유지해요. 특히 `uploadDocument` 후 `['documents']` 무효화는 클래식한 패턴이죠. 이거면 상태 동기화 버그 걱정 없습니다!"

### ⚠️ Critical PM
"기본 전략은 맞지만 **Optimistic Update가 없습니다**. 현재 흐름:

1. 사용자가 삭제 버튼 클릭
2. API 요청 전송 (500ms)
3. 응답 대기
4. `invalidateQueries` 실행
5. 목록 다시 fetch (500ms)
6. **총 1초 후 UI 업데이트**

**Optimistic Update 적용 시**:
1. 사용자가 삭제 버튼 클릭
2. **즉시 UI에서 제거** (0ms)
3. API 요청 백그라운드 실행
4. 실패 시 롤백

TanStack Query의 `onMutate`를 활용해서 즉각적인 피드백을 줘야 UX가 좋아요."

**근거**: [TanStack Query Optimistic Updates](https://tanstack.com/query/v5/docs/framework/react/guides/optimistic-updates)

**판정**: Critical PM 우세 - Optimistic Update 추가 권장

---

## 🥊 Round 9: Docker Compose 인프라 설계

### 🚀 Optimistic PM
"PRD의 Docker Compose 구성이 완벽합니다:

- **ONLYOFFICE Docs**: 8000번 포트
- **PostgreSQL**: 5432번 포트
- **S3 Ninja**: 9444번 포트

모든 게 로컬에서 재현 가능하고, 개발자 온보딩도 쉬워요. `docker-compose up -d` 한 줄이면 전체 인프라 구동! 이게 바로 DX(Developer Experience)죠."

### ⚠️ Critical PM
"**볼륨 마운트와 데이터 영속성 설정이 PRD에 명시되지 않았습니다.** 현재 상태로 배포하면:

```yaml
# 잘못된 예 (볼륨 없음)
services:
  postgres:
    image: postgres:16
    # volumes가 없으면 컨테이너 재시작 시 데이터 소실!
```

**필수 추가 사항**:

```yaml
services:
  postgres:
    volumes:
      - postgres_data:/var/lib/postgresql/data
  s3ninja:
    volumes:
      - s3_data:/home/sirius/data
  onlyoffice:
    volumes:
      - onlyoffice_data:/var/www/onlyoffice/Data

volumes:
  postgres_data:
  s3_data:
  onlyoffice_data:
```

그리고 **헬스체크**도 없어서 서비스가 준비되기 전에 백엔드가 실행되면 연결 실패해요."

**판정**: Critical PM 승 - Docker Compose 설정 보완 필요

---

## 🥊 Round 10: API 설계 RESTful 준수

### 🚀 Optimistic PM
"API 엔드포인트가 깔끔하고 직관적입니다:

- `GET /api/documents` - 목록 조회
- `POST /api/documents` - 업로드
- `DELETE /api/documents/{id}` - 삭제
- `GET /api/config?documentId={id}` - 설정 조회

RESTful 원칙을 잘 따르고 있어요. 특히 HTTP 메서드를 의미에 맞게 쓰고, 204 No Content도 제대로 반환하네요!"

### ⚠️ Critical PM
"**일관성 문제가 있습니다**:

1. **`GET /api/config?documentId={id}` 는 RESTful하지 않음**
   - 올바른 형태: `GET /api/documents/{id}/config`
   - Query parameter는 필터링용이지 리소스 식별용이 아님

2. **PUT vs POST 혼동**:
   - 문서 업데이트는 `PUT /api/documents/{id}` 또는 `PATCH`를 써야 함
   - 현재는 업데이트 API가 아예 없음 (Callback으로만 처리)

3. **에러 응답 형식 미정의**:
   - 400, 404, 500 응답의 JSON 스키마가 없음
   - `{\"error\": \"message\"}` 형태 표준화 필요

4. **페이지네이션 없음**:
   - 문서가 1000개면? 전부 다 불러옴?"

**판정**: Critical PM 우세 - API 설계 개선 필요

---

## 🥊 Round 11: 파일 키 생성 전략

### 🚀 Optimistic PM
"PRD의 `fileKey` 생성 로직이 훌륭합니다:

```
doc_{timestamp}_{uuid}.{extension}
```

- **고유성 보장**: timestamp + UUID 조합
- **충돌 방지**: 나노초 단위 + 128bit UUID
- **가독성**: 파일명에서 생성 시점 추론 가능

이 방식은 Twitter Snowflake ID와 비슷한 아이디어로, 분산 시스템에서도 안전해요!"

### ⚠️ Critical PM
"로직은 좋지만 **ONLYOFFICE의 Document Key 요구사항을 간과했습니다**:

> The document.key is used to identify the file when checking for changes. If the same file is opened by the editor with the same key, it will be considered as the same file. **If the key changes, the editor will treat it as a new file.**

**문제점**:
1. 파일을 편집할 때마다 새 key를 생성하면 안 됨
2. 같은 파일은 같은 key를 유지해야 버전 관리 가능
3. PRD는 업로드 시 key 생성하는데, **수정 후 재업로드 시 어떻게 되나요?**

**제안**:
- DB의 `file_key`는 **고정** (문서 생애 동안 불변)
- S3의 `storage_path`는 버전별로 변경 가능
- 수정 시 `updated_at`만 갱신, key는 유지"

**판정**: Critical PM 승 - 파일 키 전략 재설계 필요

---

## 🥊 Round 12: 트랜잭션 관리

### 🚀 Optimistic PM
"PRD에서 '트랜잭션으로 원자성 보장'이라고 명시한 건 좋습니다! Spring의 `@Transactional`만 붙이면 S3 업로드 실패 시 DB 롤백이 자동으로 되겠죠?"

### ⚠️ Critical PM
"**큰 오해입니다.** S3는 외부 시스템이라 JPA 트랜잭션 범위에 포함되지 않아요:

```java
@Transactional
public void uploadDocument(File file) {
    // 1. S3에 업로드 (외부 API 호출)
    s3Client.putObject(...);

    // 2. DB에 저장 (JPA 관리)
    documentRepository.save(document);

    // 만약 2번에서 예외 발생하면?
    // -> DB는 롤백되지만 S3는 그대로! (고아 파일 생성)
}
```

**올바른 처리**:

```java
public void uploadDocument(File file) {
    Document document = null;
    String s3Path = null;

    try {
        // 1. DB에 먼저 저장 (트랜잭션 내)
        document = documentRepository.save(new Document(...));

        // 2. S3에 업로드
        s3Path = s3Client.putObject(...);

        // 3. DB 업데이트
        document.setStoragePath(s3Path);
        documentRepository.save(document);

    } catch (Exception e) {
        // 4. 롤백: S3 삭제 + DB 삭제
        if (s3Path != null) s3Client.deleteObject(s3Path);
        if (document != null) documentRepository.delete(document);
        throw e;
    }
}
```

또는 **Saga 패턴**을 써야 합니다."

**근거**: 분산 트랜잭션 패턴 ([Spring Boot S3 통합](https://reflectoring.io/spring-boot-s3/))

**판정**: Critical PM 압승 - 보상 트랜잭션 구현 필수

---

## 🥊 Round 13: 동시 편집 시나리오

### 🚀 Optimistic PM
"ONLYOFFICE가 동시 편집을 알아서 처리해줍니다! 사용자 A와 B가 같은 문서를 열면:

1. 동일한 `document.key`로 에디터 초기화
2. ONLYOFFICE가 WebSocket으로 실시간 동기화
3. Callback은 마지막 저장 시 한 번만 발생

우리는 그냥 같은 key를 주기만 하면 돼요. 복잡한 OT(Operational Transformation) 알고리즘은 ONLYOFFICE가 다 해줘요!"

### ⚠️ Critical PM
"맞지만 **동시 저장 시 Race Condition**은 우리가 처리해야 합니다:

**시나리오**:
- 10:00:00 - 사용자 A가 저장 → Callback 1 전송
- 10:00:01 - 사용자 B가 저장 → Callback 2 전송
- 10:00:02 - Callback 1 처리 시작 (파일 다운로드 3초 소요)
- 10:00:03 - Callback 2 처리 시작 (파일 다운로드 3초 소요)
- 10:00:05 - Callback 1이 S3에 저장 (버전 1)
- 10:00:06 - **Callback 2가 S3에 저장 (버전 1을 덮어씀!)**

**해결책**:
1. Callback 처리를 **큐(Queue)**로 직렬화
2. DB에 `version` 컬럼 추가하고 **Optimistic Locking**
3. S3에 버전별로 저장 (`doc_xxx_v1.docx`, `doc_xxx_v2.docx`)

PRD의 3편에서 '버전 히스토리'를 언급했는데, 사실 2편부터 필요합니다."

**판정**: Critical PM 승 - 동시성 제어 로직 추가 필요

---

## 🥊 Round 14: 프론트엔드 에러 처리

### 🚀 Optimistic PM
"TanStack Query가 에러 처리를 자동으로 해줍니다:

```typescript
const { data, error, isLoading } = useDocuments();

if (isLoading) return <Spinner />;
if (error) return <ErrorMessage error={error} />;
```

심플하고 효과적이죠! 재시도도 자동이고, 에러 바운더리도 설정하면 끝이에요."

### ⚠️ Critical PM
"기본 에러 처리는 되지만 **사용자 경험이 형편없습니다**:

1. **네트워크 에러 vs 서버 에러 구분 없음**
   - \"네트워크 연결을 확인하세요\" vs \"파일 크기가 너무 큽니다\"

2. **재시도 로직 과도**
   - 400 Bad Request는 재시도해도 소용없는데 3번 재시도?

3. **에러 복구 액션 없음**
   - 업로드 실패 시 어떻게 하나요? 새로고침?

**제안**:
```typescript
const uploadMutation = useMutation({
  mutationFn: uploadDocument,
  retry: (failureCount, error) => {
    // 400번대 에러는 재시도 안 함
    if (error.response?.status >= 400 && error.response?.status < 500) {
      return false;
    }
    return failureCount < 3;
  },
  onError: (error) => {
    if (error.response?.status === 413) {
      toast.error('파일 크기는 50MB 이하여야 합니다.');
    } else if (error.code === 'ERR_NETWORK') {
      toast.error('네트워크 연결을 확인하세요.');
    } else {
      toast.error('업로드 실패. 다시 시도해주세요.');
    }
  }
});
```

**판정**: Critical PM 우세 - 에러 처리 세분화 필요

---

## 🥊 Round 15: 테스트 전략

### 🚀 Optimistic PM
"PRD에 'Backend Unit Test 커버리지 > 70%'라고 명시된 건 현실적입니다. 2편에서는 핵심 비즈니스 로직만 테스트하고, E2E는 3편에서 해도 돼요:

- DocumentService 로직 테스트
- S3 연동은 Testcontainers로 모킹
- Controller는 WebMvcTest

이 정도면 충분해요!"

### ⚠️ Critical PM
"**통합 테스트가 없습니다.** 특히 ONLYOFFICE Callback 시나리오는 반드시 테스트해야 합니다:

1. **Callback Signature 검증**: JWT가 올바르게 검증되는가?
2. **파일 다운로드 실패 시**: 재시도 로직 작동?
3. **S3 업로드 실패 시**: DB 롤백?
4. **동시 Callback 처리**: Race condition 없는가?

**제안 테스트 구조**:
```
backend/
├── src/test/java/
│   ├── unit/              # 단위 테스트 (70%)
│   │   ├── DocumentServiceTest
│   │   └── FileKeyGeneratorTest
│   ├── integration/       # 통합 테스트 (필수 시나리오)
│   │   ├── CallbackIntegrationTest
│   │   ├── S3IntegrationTest
│   │   └── DocumentFlowIntegrationTest
│   └── e2e/              # E2E (3편)
│       └── EditorE2ETest
```

**특히 Callback 테스트를 건너뛰면 프로덕션에서 대참사입니다.**"

**판정**: Critical PM 승 - 통합 테스트 추가 필수

---

## 🥊 Round 16: 로깅 및 모니터링

### 🚀 Optimistic PM
"로깅은 Spring Boot 기본 설정만으로도 충분해요:

```java
@Slf4j
@Service
public class DocumentService {
    public void uploadDocument(File file) {
        log.info(\"Uploading file: {}\", file.getName());
        // ...
        log.info(\"Upload completed: {}\", documentId);
    }
}
```

개발 단계에서는 이 정도면 됩니다. 모니터링은 실제 프로덕션 배포 시에 추가하면 돼요."

### ⚠️ Critical PM
"**PRD의 'Out of Scope'에 모니터링이 없습니다.** 즉, 2편에 포함해야 한다는 뜻인데 아무것도 없어요!

**필수 모니터링**:
1. **애플리케이션 메트릭**: Micrometer + Prometheus
2. **로그 집계**: Logback → JSON 포맷
3. **헬스체크**: `/actuator/health`
4. **커스텀 메트릭**:
   - `document.upload.count`
   - `document.upload.size.bytes`
   - `callback.processing.time`
   - `s3.upload.failures`

**로깅 레벨 전략**:
- INFO: 비즈니스 이벤트 (업로드, 삭제)
- WARN: S3 재시도, 느린 쿼리
- ERROR: 예외, Callback 실패

Spring Boot Actuator만 추가하면 30분이면 구현 가능한데, 왜 빠졌나요?"

**근거**: [Spring Boot 프로덕션 준비](https://docs.spring.io/spring-boot/reference/actuator/index.html)

**판정**: Critical PM 승 - 모니터링 추가 필수

---

## 🥊 Round 17: 설정 관리 (application.yml)

### 🚀 Optimistic PM
"PRD의 설정 구조가 명확합니다:

```yaml
server:
  baseUrl: http://host.docker.internal:8080

onlyoffice:
  secret: <32-chars-secret>

s3:
  endpoint: http://localhost:9444
  bucket: documents
```

각 환경별로 프로파일 분리하면 완벽해요!"

### ⚠️ Critical PM
"**시크릿이 평문으로 저장됩니다.** Git에 커밋되면 보안 사고입니다:

```yaml
# 절대 이러면 안 됨!
onlyoffice:
  secret: my-super-secret-key-12345678  # ❌

s3:
  access-key: AKIAIOSFODNN7EXAMPLE      # ❌
  secret-key: wJalrXUtnFEMI/...         # ❌
```

**올바른 방법**:

1. **환경 변수 사용**:
```yaml
onlyoffice:
  secret: ${ONLYOFFICE_SECRET}
s3:
  access-key: ${S3_ACCESS_KEY}
  secret-key: ${S3_SECRET_KEY}
```

2. **Spring Cloud Config** 또는 **Vault** (프로덕션)

3. **`.env` 파일** (로컬 개발):
```bash
# .env (gitignore에 추가!)
ONLYOFFICE_SECRET=my-secret
S3_ACCESS_KEY=my-key
```

그리고 **application-dev.yml, application-prod.yml 분리**는 필수입니다."

**판정**: Critical PM 압승 - 시크릿 관리 개선 필수

---

## 🥊 Round 18: UI/UX 상세 요구사항

### 🚀 Optimistic PM
"PRD의 UI 요구사항이 실용적입니다:

- 테이블 형태 문서 목록 ✅
- 업로드 버튼 ✅
- 로딩 스피너 ✅
- 빈 상태 메시지 ✅

shadcn/ui + Tailwind로 이쁘게 만들면 끝이에요. 디자인 시스템까지 만들 필요 없습니다!"

### ⚠️ Critical PM
"**접근성(Accessibility)이 완전히 누락되었습니다**:

1. **키보드 네비게이션**: 마우스 없이 조작 가능한가?
2. **스크린 리더**: ARIA 레이블 있나?
3. **포커스 관리**: 파일 업로드 후 포커스는?
4. **에러 안내**: 스크린 리더가 에러를 읽어주나?

**UX 누락 사항**:
- 업로드 진행률 표시 (0% ~ 100%)
- 드래그 앤 드롭 업로드
- 파일 미리보기 (썸네일)
- 검색 기능 (파일명)
- 정렬 (이름, 크기, 날짜)
- 벌크 삭제 (체크박스)

**최소한 shadcn/ui를 쓴다면 접근성은 자동으로 보장되지만, 커스텀 컴포넌트는 직접 구현해야 합니다.**"

**판정**: Critical PM 우세 - 접근성 및 UX 개선 권장

---

## 🥊 Round 19: 일정 및 범위 관리

### 🚀 Optimistic PM
"PRD의 범위가 적절합니다! 2편에서:

✅ **포함**:
- SDK 도입
- DB + S3 연동
- 문서 CRUD
- 기본 UI

❌ **제외 (3편으로 연기)**:
- 사용자 인증
- 협업 기능
- 버전 히스토리 UI
- Redis 캐싱

이 정도면 **2주 내 완료 가능**합니다. 범위를 더 늘리면 일정 지연 위험이 커져요!"

### ⚠️ Critical PM
"범위는 맞지만 **숨겨진 작업들이 있습니다**:

**PRD에 없지만 필수인 작업**:
1. Docker Compose 볼륨 설정 (0.5일)
2. JWT Callback 검증 구현 (1일)
3. 파일 업로드 보안 강화 (1일)
4. 동시성 제어 로직 (1일)
5. 보상 트랜잭션 구현 (1일)
6. 통합 테스트 작성 (2일)
7. 모니터링 설정 (0.5일)
8. 시크릿 관리 개선 (0.5일)

**추가 작업: 7.5일**

**실제 일정**:
- PRD 명시 작업: 10일
- 숨겨진 필수 작업: 7.5일
- 버퍼 (20%): 3.5일
- **총 21일 (3주)**

**제안**: 3주로 일정을 조정하거나, 일부 작업을 3편으로 연기하되 우선순위를 명확히 하세요."

**판정**: Critical PM 승 - 일정 재조정 필요

---

## 🥊 Round 20: 최종 의사결정 - 2편 PRD 승인 여부

### 🚀 Optimistic PM
"**조건부 승인**을 제안합니다!

PRD의 전체 방향성과 아키텍처는 훌륭합니다. 몇 가지만 보완하면 바로 시작 가능해요:

**즉시 시작 가능한 부분 (80%)**:
1. ✅ SDK 도입
2. ✅ PostgreSQL 연동
3. ✅ 기본 CRUD API
4. ✅ TanStack Query/Table
5. ✅ 문서 목록 UI

**병렬로 보완할 부분 (20%)**:
1. S3 Ninja → MinIO 변경 (docker-compose만 수정)
2. JWT 검증 추가 (SDK에 있으니 쉬움)
3. 파일 업로드 검증 강화 (Apache Tika 추가)

**전략**: 핵심 기능 먼저 구현하고, 보안/안정성은 점진적으로 개선하세요. 완벽함보다 진행이 중요합니다!"

### ⚠️ Critical PM
"**보류 및 수정 요청**입니다.

PRD의 비전은 훌륭하지만, **프로덕션을 고려하지 않은 부분들이 너무 많습니다**. 블로그 2편이면 독자들이 실제로 따라 할 텐데, 보안 취약점을 그대로 배포하게 만들 순 없어요.

**필수 수정 사항 (2편 시작 전)**:

| 우선순위 | 항목 | 소요 |
|---------|------|------|
| 🔴 Critical | JWT Callback 검증 추가 | 1일 |
| 🔴 Critical | S3 Ninja → MinIO 변경 + 볼륨 설정 | 0.5일 |
| 🔴 Critical | 파일 업로드 보안 강화 (매직 바이트, 새니타이징) | 1일 |
| 🟡 High | 보상 트랜잭션 설계 | 1일 |
| 🟡 High | TanStack Router 제거, Next.js Router 사용 | 0.5일 |
| 🟢 Medium | 동시성 제어 (Queue 또는 Locking) | 1일 |
| 🟢 Medium | 모니터링 추가 (Actuator) | 0.5일 |

**총 5.5일 추가 작업**

**조건부 승인안**:
1. 🔴 Critical 항목 3개를 PRD에 추가
2. 일정을 2주 → 3주로 조정
3. '성공 메트릭'에 보안 체크리스트 추가

**이렇게 하면 블로그 독자들이 안전하게 따라 할 수 있는 실용적인 가이드가 됩니다.**"

---

## 📊 최종 집계

### 라운드별 판정

| Round | 주제 | 승자 |
|-------|------|------|
| 1 | 전체 아키텍처 | ⚠️ Critical PM |
| 2 | SDK 도입 시기 | 무승부 |
| 3 | DB 설계 | ⚠️ Critical PM |
| 4 | 파일 업로드 보안 | ⚠️ Critical PM |
| 5 | TanStack 스택 | ⚠️ Critical PM |
| 6 | JWT 보안 | ⚠️ Critical PM |
| 7 | 성능 요구사항 | ⚠️ Critical PM |
| 8 | 상태 관리 | ⚠️ Critical PM |
| 9 | Docker Compose | ⚠️ Critical PM |
| 10 | API 설계 | ⚠️ Critical PM |
| 11 | 파일 키 전략 | ⚠️ Critical PM |
| 12 | 트랜잭션 관리 | ⚠️ Critical PM |
| 13 | 동시 편집 | ⚠️ Critical PM |
| 14 | 에러 처리 | ⚠️ Critical PM |
| 15 | 테스트 전략 | ⚠️ Critical PM |
| 16 | 모니터링 | ⚠️ Critical PM |
| 17 | 설정 관리 | ⚠️ Critical PM |
| 18 | UI/UX | ⚠️ Critical PM |
| 19 | 일정 관리 | ⚠️ Critical PM |
| 20 | 최종 결정 | **협상 필요** |

**최종 스코어**:
- 🚀 Optimistic PM: 0승
- ⚠️ Critical PM: 18승
- 무승부: 1회
- 협상: 1회

---

## 🎯 종합 권고사항

### 즉시 수정 필요 (2편 시작 전)

#### 1. 보안 (Critical)
- [ ] JWT Callback 검증을 2편에 포함 (3편 연기 불가)
- [ ] 파일 업로드 검증 강화 (매직 바이트, Path Traversal 방어)
- [ ] 시크릿 관리 개선 (환경 변수화)

#### 2. 인프라 (Critical)
- [ ] S3 Ninja → MinIO로 변경
- [ ] Docker Compose 볼륨 설정 추가
- [ ] 헬스체크 설정

#### 3. 아키텍처 (High)
- [ ] TanStack Router 제거, Next.js 기본 Router 사용
- [ ] 보상 트랜잭션 패턴 적용 (S3 + DB 원자성)
- [ ] 파일 키 생성 전략 재설계 (불변 key)

#### 4. 개발 프랙티스 (High)
- [ ] 통합 테스트 추가 (특히 Callback 시나리오)
- [ ] 모니터링 설정 (Spring Boot Actuator)
- [ ] API 설계 RESTful 개선

### 선택적 개선 (2편 또는 3편)

#### 5. UX 향상 (Medium)
- [ ] Optimistic Update 적용
- [ ] 업로드 진행률 표시
- [ ] 드래그 앤 드롭

#### 6. 성능 (Medium)
- [ ] 동시성 제어 (큐 또는 락킹)
- [ ] 페이지네이션
- [ ] DB 인덱스 최적화

#### 7. 접근성 (Low)
- [ ] ARIA 레이블
- [ ] 키보드 네비게이션

---

## 📌 두 에이전트의 합의점

### ✅ 동의하는 부분

1. **PRD의 전체 방향성**: 1편 → 2편 진화는 올바름
2. **SDK 도입**: 표준화를 위해 필수
3. **PostgreSQL + S3 아키텍처**: 확장성 확보
4. **TanStack Query/Table**: 프론트엔드 상태 관리 최적

### ⚠️ 의견 차이

| 항목 | Optimistic PM | Critical PM | 절충안 |
|------|---------------|-------------|--------|
| **보안** | 3편 연기 가능 | 2편 필수 | **2편 필수** (실제 취약점 존재) |
| **S3 스토리지** | S3 Ninja OK | MinIO 필수 | **MinIO 사용** (프로덕션 고려) |
| **일정** | 2주 가능 | 3주 필요 | **2.5주** (핵심만 2편) |
| **테스트** | 단위 테스트만 | 통합 테스트 필수 | **Callback 통합 테스트 추가** |

---

## 🏆 최종 결론

**Critical PM의 지적이 대부분 타당합니다.** 특히 보안 관련 사항은 블로그 독자들이 실제로 따라 할 것을 고려하면 반드시 포함되어야 합니다.

**수정된 2편 범위 제안**:

### Core Features (변경 없음)
- ONLYOFFICE SDK 도입
- PostgreSQL 문서 메타데이터 관리
- MinIO Object Storage 연동
- 문서 목록 UI (TanStack Query/Table)
- Next.js 기본 Router 사용

### Security & Stability (추가)
- JWT Callback 검증
- 파일 업로드 보안 강화
- 보상 트랜잭션 패턴
- Spring Boot Actuator 모니터링
- Callback 통합 테스트

### 3편으로 연기
- 사용자 인증/인가
- 버전 히스토리 UI
- Redis 캐싱
- 협업 기능
- 고급 UX (드래그앤드롭 등)

**예상 일정**: 3주 (보안 강화로 인한 1주 추가)

**기대효과**:
- ✅ 안전하게 따라 할 수 있는 튜토리얼
- ✅ 프로덕션 준비도 향상
- ✅ 기술 부채 최소화
- ✅ 3편으로 자연스럽게 확장 가능

---

## 📚 참고 자료

### ONLYOFFICE
- [ONLYOFFICE Java SDK GitHub](https://github.com/ONLYOFFICE/docs-integration-sdk-java)
- [ONLYOFFICE 보안 가이드](https://helpcenter.onlyoffice.com/docs/installation/docs-securityguide.aspx)
- [ONLYOFFICE 취약점 데이터베이스](https://www.cvedetails.com/vulnerability-list/vendor_id-22843/Onlyoffice.html)

### Spring Boot & S3
- [Spring Boot S3 통합 가이드](https://reflectoring.io/spring-boot-s3/)
- [Spring Boot 확장성 가이드](https://thamizhelango.medium.com/building-scalable-applications-with-postgresql-and-spring-boot-a-comprehensive-guide-82ee6d5b3049)
- [PostgreSQL S3 데이터 플로우](https://pravin.dev/posts/effortless-postgres-s3-data-flow-spring-boot/)

### TanStack
- [TanStack Query 공식 문서](https://tanstack.com/query/latest)
- [TanStack Router vs Next.js](https://github.com/TanStack/router/discussions/346)
- [TanStack 생태계 가이드 2025](https://void.ma/en/publications/tanstack-react-query-table-router-guide-complet-2025/)

### Object Storage
- [MinIO vs 대안들 비교](https://lowendtalk.com/discussion/210203/minio-vs-garage-vs-seaweedfs-vs-others-are-you-using-any-in-production)
- [S3 모킹 도구 비교](https://blog.localstack.cloud/2024-04-08-exploring-s3-mocking-tools-a-comparative-analysis-of-s3mock-minio-and-localstack/)

---

**작성**: 🚀 Optimistic PM & ⚠️ Critical PM
**검토**: 2025-12-02
**다음 단계**: PRD 수정안 작성
