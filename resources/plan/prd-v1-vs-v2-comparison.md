# ONLYOFFICE 연동 PRD v1 vs v2 비교 분석

## 📊 개요

| 항목 | v1 | v2 |
|------|----|----|
| **작성일** | 2025-11-23 | 2025-12-02 |
| **버전** | 1.0 | 2.1 (PM 토론 반영) |
| **초점** | 기본 기능 구현 | **보안 강화 + 프로덕션 준비** |
| **일정** | 2주 | 3주 (+1주) |

---

## 1️⃣ 핵심 변경사항 요약

### 1.1 기술 스택 변경

#### Object Storage
- **v1.0**: S3 Ninja (로컬 개발용)
- **v2.1**: **MinIO** (프로덕션 준비도 높음)
  - 이유: 안정성, 관리 콘솔(9001), 볼륨 마운트 지원

#### Router
- **v1.0**: TanStack Router
- **v2.1**: **Next.js App Router** (변경)
  - 이유: SSR 호환성, TanStack Router와 Next.js 16의 충돌 해결

#### JWT 검증
- **v1.0**: 3편에서 구현 예정
- **v2.1**: **2편에 포함** (필수)
  - 이유: Callback 위변조 방지 (Critical 보안 이슈)

#### 트랜잭션 처리
- **v1.0**: JPA만 사용
- **v2.1**: **Saga 패턴** 추가
  - 이유: 분산 트랜잭션 원자성 보장, 보상 트랜잭션

---

## 2️⃣ 보안 강화 (v2.1 신규)

### 2.1 Callback 보안

| 항목 | v1.0 | v2.1 |
|------|------|------|
| **JWT 검증** | ❌ | ✅ HS256 서명 검증 |
| **큐 기반 처리** | ❌ | ✅ ExecutorService 직렬 처리 |
| **비관적 락** | ❌ | ✅ 타임아웃 3초로 설정 |
| **버전 관리** | ❌ | ✅ 성공 시 자동 증가 |

### 2.2 파일 업로드 보안

| 항목 | v1.0 | v2.1 |
|------|------|------|
| **확장자 검증** | ✅ | ✅ |
| **MIME 타입 검증** | ❌ | ✅ Apache Tika 2.9.1 |
| **파일명 새니타이징** | ❌ | ✅ Path Traversal 방지 |
| **압축 폭탄 방어** | ❌ | ✅ ZIP 크기 제한 (1GB) |
| **파일 크기 제한** | 50MB | **100MB** |

### 2.3 기타 보안

| 항목 | v1.0 | v2.1 |
|------|------|------|
| **시크릿 관리** | 평문 | ✅ 환경 변수 + 시작 시 검증 (32자 이상) |
| **Presigned URL** | ✅ 1시간 | ✅ 1시간 (동일) |

---

## 3️⃣ API 명세 변경

### 3.1 에디터 설정 API

**v1.0**:
```
GET /api/config?documentId={id}
```

**v2.1**:
```
GET /api/documents/{id}/config
```
- 이유: RESTful 컨벤션 준수

### 3.2 응답 필드 추가 (v2.1)

```json
{
  "config": { ... },
  "token": "JWT토큰_추가"  // v2.1 신규
}
```

---

## 4️⃣ 데이터 모델 변경

### 4.1 documents 테이블

| 컬럼명 | v1.0 | v2.1 | 설명 |
|--------|------|------|------|
| `id` | ✅ | ✅ | PRIMARY KEY |
| `file_name` | ✅ | ✅ | 원본 파일명 |
| `file_key` | ✅ | ✅ | ONLYOFFICE 문서 키 (불변) |
| `file_type` | ✅ | ✅ | 확장자 |
| `document_type` | ✅ | ✅ | word/cell/slide |
| `file_size` | ✅ | ✅ | 파일 크기 |
| `storage_path` | ✅ | ✅ | S3/MinIO Object Key |
| **`status`** | ❌ | ✅ | **PENDING/ACTIVE/DELETED** |
| **`version`** | ❌ | ✅ | **동시성 제어용** |
| `created_at` | ✅ | ✅ | 생성 시각 |
| `updated_at` | ✅ | ✅ | 수정 시각 |
| **`deleted_at`** | ❌ | ✅ | **Soft delete** |
| `created_by` | ✅ | ✅ | 업로더 ID |

### 4.2 신규 인덱스 (v2.1)

- `idx_status` on `status` (상태 필터링)
- `idx_deleted_at` on `deleted_at` (soft delete 쿼리)

---

## 5️⃣ 기능 요구사항 변경

### 5.1 User Stories (US) 변경

#### US-001: 문서 목록 조회

**v1.0**:
```
- 파일명, 타입, 크기, 생성일이 표시됨
```

**v2.1** (추가):
```
- 상태(status) 표시 추가
- PENDING 상태 문서는 회색으로 표시
```

#### US-002: 문서 업로드

**v1.0**:
```
- 파일 선택 및 업로드만
```

**v2.1** (추가):
```
- 악의적 파일은 업로드 거부 (매직 바이트 검증)
- 업로드 진행률 표시 추가
```

#### US-003: 문서 편집

**v1.0**:
```
- S3 Ninja에 저장
```

**v2.1** (변경):
```
- MinIO에 저장
- JWT 검증으로 안전한 Callback 처리
```

#### US-004: 문서 삭제

**v1.0**:
```
- DB + S3 파일 삭제
```

**v2.1** (추가):
```
- Saga 패턴으로 트랜잭션 보장
```

### 5.2 API 흐름 변경

#### 문서 업로드 API (v2.1)

```
1. DB에 PENDING 상태로 저장
2. MinIO에 파일 업로드
3. DB 상태를 ACTIVE로 변경 (Saga 패턴)
4. 실패 시 보상 트랜잭션 실행
```

#### Callback API (v2.1 보안 강화)

```
1. JWT 검증 (필수) - HS256 서명 검증
2. Payload 검증
3. 큐 기반 직렬 처리 (ExecutorService.newSingleThreadExecutor())
4. 비관적 락 (타임아웃 3초)
5. 버전 증가
```

⚠️ **주의**: Callback 큐는 단일 인스턴스 전용. 수평 확장 시 Redis/Kafka 필요 (3편 예정)

---

## 6️⃣ 비기능 요구사항 변경

### 6.1 성능 요구사항

| 항목 | v1.0 | v2.1 | 변경 |
|------|------|------|------|
| 문서 목록 로딩 | < 500ms | < 500ms (P95) | 측정 방법 명확화 |
| 파일 업로드 (10MB) | < 3초 | < 3초 (P95) | 측정 방법 명확화 |
| 에디터 초기 렌더링 | < 2초 | < 2초 (P95) | 측정 방법 명확화 |
| Callback 처리 | < 1초 | **< 2초 (P95)** | 1초→2초 (현실적) |

### 6.2 보안 요구사항 (v2.1 강화)

**v2.1 보안 체크리스트**:
- [x] JWT 검증 구현
- [x] MIME 타입 검증
- [x] Path Traversal 방어
- [x] 압축 폭탄 방어
- [x] 시크릿 환경 변수화
- [x] 시크릿 시작 시 검증
- [ ] HTTPS 적용 (프로덕션)
- [ ] Rate Limiting (3편)

### 6.3 관찰성 (Observability) - v2.1 신규

**Spring Boot Actuator 엔드포인트**:
- `/actuator/health`: 서비스 헬스체크
- `/actuator/metrics`: 메트릭 조회
- `/actuator/prometheus`: Prometheus 포맷

**커스텀 메트릭**:
- 문서 업로드: 카운트, 파일 크기, 처리 시간
- Callback 처리: 카운트, 처리 시간, 큐 크기
- MinIO 연동: 실패 횟수, 다운로드 시간

---

## 7️⃣ 기술 스택 변경

### 7.1 Backend

| 기술 | v1.0 | v2.1 | 변경 |
|------|------|------|------|
| Spring Boot | 3.2.x | 3.2.x | - |
| Java | 17 | 17 | - |
| Spring Data JPA | 3.2.x | 3.2.x | - |
| PostgreSQL | 16 | 16 | - |
| ONLYOFFICE SDK | 1.0.0 | **1.5.0** | 업그레이드 |
| **java-jwt** | ❌ | ✅ **4.4.0** | 신규 (JWT) |
| **Apache Tika** | ❌ | ✅ **2.9.1** | 신규 (파일 검증) |
| **Spring Actuator** | ❌ | ✅ **3.2.x** | 신규 (모니터링) |
| **Micrometer** | ❌ | ✅ **1.12.x** | 신규 (메트릭) |
| Object Storage | S3 Ninja | **MinIO** | 변경 |

### 7.2 Frontend

| 기술 | v1.0 | v2.1 | 변경 |
|------|------|------|------|
| Next.js | 16.x | 16.x | - |
| React | 19.x | 19.x | - |
| TypeScript | 5.x | 5.x | - |
| **Router** | TanStack Router | **Next.js App Router** | 변경 |
| TanStack Query | 최신 | 최신 | - |
| TanStack Table | 최신 | 최신 | - |
| shadcn/ui | 최신 | 최신 | - |

---

## 8️⃣ 인프라 변경

### 8.1 Docker Compose 변경

| 서비스 | v1.0 | v2.1 | 변경 |
|--------|------|------|------|
| ONLYOFFICE Docs | :8000 | :8000 | 볼륨 추가 |
| PostgreSQL | :5432 | :5432 | 볼륨 추가 |
| **S3 Ninja** | **:9444** | ❌ | 제거 |
| **MinIO** | ❌ | ✅ **:9000/9001** | 신규 |
| Backend | :8080 | :8080 | - |
| Frontend | :5173 | :3000 | - |

### 8.2 볼륨 마운트 (v2.1 신규)

- PostgreSQL: `/var/lib/postgresql/data`
- MinIO: `/data`
- ONLYOFFICE: `/var/lib/onlyoffice`

---

## 9️⃣ 범위 변경 (Out of Scope)

### v1.0 Out of Scope

- JWT 기반 Callback 위변조 방지
- 사용자 인증/인가
- 협업 기능
- 고급 문서 관리
- 성능 최적화

### v2.1 Out of Scope (변경)

**v1.0과 동일하지만 추가:**
- ~~JWT 기반 Callback 위변조 방지~~ → **2편에 포함**
- Rate Limiting (3편)
- 바이러스 스캔 (3편)
- 문서 버전 히스토리 UI (3편)
- Redis 캐싱 (3편)

---

## 🔟 성공 지표 (Success Metrics)

### 10.1 개발 완료 기준 (v2.1 추가)

**v1.0**:
- 7가지 완료 기준

**v2.1** (추가):
- JWT Callback 검증이 정상 작동 ✅
- 파일 업로드 보안 검증 통과 ✅
- Saga 패턴으로 트랜잭션 보장 ✅

### 10.2 품질 기준

| 항목 | v1.0 | v2.1 | 변경 |
|------|------|------|------|
| Backend Test 커버리지 | > 70% | **> 80%** | 상향 |
| 통합 테스트 | X | ✅ Callback 시나리오 | 신규 |
| 통합 테스트 | X | ✅ Saga 보상 | 신규 |
| 보안 스캔 | X | ✅ OWASP ZAP | 신규 |

### 10.3 문서화 기준 (v2.1 추가)

- 보안 가이드 작성 ✅

### 10.4 보안 체크리스트 (v2.1 신규)

```
- [ ] JWT 검증 로직 테스트 완료
- [ ] 파일 업로드 우회 시도 차단 확인
- [ ] Path Traversal 공격 차단 확인
- [ ] 압축 폭탄 차단 확인
- [ ] 시크릿이 환경 변수로 관리됨
- [ ] .env 파일이 .gitignore에 추가됨
- [ ] Presigned URL 만료 시간 검증
```

---

## 1️⃣1️⃣ 리스크 및 완화 방안

### 11.1 기술 리스크 (v2.1 추가)

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|----------|
| MinIO 데이터 손실 | 중 | 정기 백업, AWS S3 전환 준비 |
| JWT 구현 복잡도 | 중 | java-jwt 라이브러리, SDK 예제 |

### 11.2 일정 리스크 (v2.1 변경)

| 리스크 | v1.0 | v2.1 |
|--------|------|------|
| 보안 구현 시간 초과 | X | ✅ 신규 (중 영향도) |
| 통합 테스트 작성 지연 | X | ✅ 신규 (중 영향도) |

### 11.3 보안 리스크 (v2.1 신규)

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|----------|
| JWT Secret 노출 | 🔴 Critical | 환경 변수, .gitignore |
| 파일 업로드 우회 | 🔴 Critical | Apache Tika MIME 검증 |
| Callback 위변조 | 🔴 Critical | JWT 서명 검증 필수 |
| 압축 폭탄 공격 | 🟡 High | ZIP 크기 제한 (1GB) |
| DoS (대용량 파일) | 🟡 High | 파일 크기 제한 (100MB) |

---

## 1️⃣2️⃣ 일정 변경

### v1.0: 2주

```
Week 1: 기반 구축
Week 2: API + Frontend
```

### v2.1: 3주 (+ 1주 보안)

```
Week 1: 기반 구축 + 보안
  - MinIO Docker Compose 설정
  - JWT Validation 구현
  - File Security Service 구현
  
Week 2: API + Saga 패턴
  - 문서 업로드 API + Saga 패턴
  - Callback API + JWT 검증
  - Spring Actuator 설정
  
Week 3: Frontend + 테스트
  - Next.js App Router 설정
  - 통합 테스트 (Callback, Saga)
  - 보안 테스트 (OWASP)
  - 문서화 + 블로그
```

---

## 1️⃣3️⃣ 테스트 전략 (v2.1 신규)

### 13.1 단위 테스트

**v2.0 신규 테스트**:
- FileSecurityService: 악의적 파일 형식 거부
- JwtValidator: 위변조된 JWT 토큰 거부
- DocumentService (Saga): MinIO 업로드 실패 시 DB 롤백

### 13.2 통합 테스트 (v2.0 신규)

**Testcontainers 사용**:
- PostgreSQL + MinIO 컨테이너 실행
- 유효한 JWT로 Callback 처리 검증
- 잘못된 JWT 거부 확인

### 13.3 E2E 테스트 (기본 시나리오)

**Playwright 사용** (v2.0 명확화):
1. 문서 목록 페이지 접속
2. 파일 업로드 및 진행률 확인
3. 업로드 완료 후 목록 자동 갱신
4. 문서 클릭 → 에디터 페이지 이동
5. ONLYOFFICE 에디터 정상 로드 확인

---

## 1️⃣4️⃣ 용어 정의 비교

### v1.0 용어

| 용어 | 설명 |
|------|------|
| Document Key | 문서 수정 여부를 판단하는 고유 식별자 (변경 시 강제 새로고침) |
| Callback | 문서 편집 완료 시 ONLYOFFICE Docs가 Backend로 전송하는 Webhook |
| Presigned URL | 임시 접근 권한이 부여된 S3 객체 URL |
| TanStack Query | React의 서버 상태 관리 라이브러리 |

### v2.1 신규 용어

| 용어 | 설명 |
|------|------|
| **Saga 패턴** | 분산 트랜잭션의 원자성을 보장하는 보상 트랜잭션 패턴 |
| **Optimistic Update** | 서버 응답 전에 UI를 먼저 업데이트하는 UX 패턴 |
| **Soft Delete** | 실제 삭제 대신 deleted_at 플래그로 논리 삭제 |

---

## 요약 표

| 구분 | v1.0 | v2.1 | 영향도 |
|------|------|------|--------|
| Object Storage | S3 Ninja | MinIO | 중 |
| Router | TanStack Router | Next.js App Router | 중 |
| JWT 검증 | 3편 연기 | 2편 필수 | 높음 |
| Saga 패턴 | ❌ | ✅ | 중 |
| 파일 검증 | 확장자만 | MIME + 매직 바이트 | 높음 |
| 모니터링 | ❌ | Actuator | 낮음 |
| 일정 | 2주 | 3주 | 높음 |
| Test 커버리지 | > 70% | > 80% | 중 |
| 보안 강화 | 기본 | Critical 방지 | 높음 |

---

**문서 작성일**: 2025-12-08  
**v1.0 기준**: 2025-11-23  
**v2.1 기준**: 2025-12-02  
**비교 기준**: PM 토론 및 보안 강화 검토
