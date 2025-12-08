# PRD v2.0 토론: Critical PM vs Optimistic PM (20 Rounds)

> **작성일**: 2025-12-04
> **문서 버전**: 1.0
> **관련 문서**: prd-v2.md

---

## 📋 참여자 소개

| PM | 역할 | 관점 |
|---|---|---|
| **🔴 Critical PM** | 보안/리스크 중심 | 보안 취약점, 프로덕션 안정성, 기술 부채 우선 |
| **🟢 Optimistic PM** | 비즈니스 가치 중심 | 빠른 딜리버리, MVP 스코프, 사용자 가치 우선 |

---

## 🎯 토론 라운드

### Round 1: JWT Callback 검증 필수화

**🔴 Critical PM:**
> PRD v2.0에서 JWT Callback 검증을 2편 필수로 승격한 것은 절대적으로 올바른 결정입니다. [ONLYOFFICE 공식 문서](https://helpcenter.onlyoffice.com/docs/installation/docs-configure-jwt.aspx)에 따르면 JWT는 inbox, outbox, browser 세 섹션 모두에서 동일한 시크릿으로 검증되어야 합니다. 3편으로 미룬다면 악의적인 Callback 요청으로 문서가 위변조될 수 있습니다.

**🟢 Optimistic PM:**
> 동의합니다. 하지만 구현 복잡도를 고려해야 합니다. ONLYOFFICE SDK에서 `JwtManager`와 `RequestManager` Bean을 제공하므로 실제 구현은 간단합니다. 핵심은 java-jwt 4.4.0 라이브러리를 활용하여 HS256 검증만 구현하면 됩니다.

**✅ 합의**: JWT Callback 검증 2편 필수 - 양측 완전 동의

---

### Round 2: Object Storage 선택 (S3 Ninja → MinIO)

**🟢 Optimistic PM:**
> MinIO로 변경하는 것에 찬성합니다. [MinIO 공식 문서](https://min.io/)에 따르면 READ/WRITE 183GB/s, 171GB/s의 성능을 제공하며, AWS S3 API와 완벽히 호환됩니다. S3 Ninja는 개발/테스트용으로만 적합합니다.

**🔴 Critical PM:**
> 동의하지만 주의점이 있습니다. MinIO는 2022년부터 AGPLv3 + 상용 라이선스로 변경되었습니다. SaaS 환경에서 상용 라이선스 없이 사용하면 법적 리스크가 있습니다. 하지만 이 프로젝트는 데모이므로 문제없습니다. 볼륨 마운트와 헬스체크 추가는 필수입니다.

**✅ 합의**: MinIO 전환 승인 - 볼륨 마운트 및 헬스체크 필수

---

### Round 3: Router 변경 (TanStack Router → Next.js App Router)

**🔴 Critical PM:**
> [TanStack vs Next.js 비교](https://tanstack.com/router/v1/docs/framework/react/comparison)에 따르면 TanStack Router는 타입 추론과 validated search params에서 우수합니다. 하지만 Next.js App Router를 선택한 이유가 SSR 호환성이라면... 왜 SSR이 필요한지 명확히 해야 합니다.

**🟢 Optimistic PM:**
> 이 프로젝트에서 SSR은 SEO보다는 초기 로딩 성능이 목적입니다. [Next.js App Router](https://nextjs.org/docs/app)는 React Server Components를 지원하여 번들 사이즈를 줄입니다. 더 중요한 것은, Next.js 16 + React 19 환경에서 TanStack Router와 충돌 가능성이 있습니다. 안정성을 위해 Next.js 네이티브 라우터를 선택한 것입니다.

**🔴 Critical PM:**
> 하지만 [TanStack Start](https://blog.logrocket.com/tanstack-start-vs-next-js-choosing-the-right-full-stack-react-framework/)는 Vite 기반으로 개발 서버 시작이 훨씬 빠릅니다. Next.js dev 서버는 30초까지 걸릴 수 있습니다.

**🟢 Optimistic PM:**
> 맞지만, 이 프로젝트는 Turbopack을 사용합니다. Next.js 16의 기본 빌드 도구입니다. 개발 경험 차이는 미미할 것입니다.

**✅ 합의**: Next.js App Router 사용 - 안정성 우선, Turbopack으로 성능 보완

---

### Round 4: Saga 패턴 도입

**🔴 Critical PM:**
> [Saga 패턴](https://microservices.io/patterns/data/saga.html)은 분산 트랜잭션에 필수이지만, 이 데모에서는 과도할 수 있습니다. 단일 Backend 인스턴스에서 DB + MinIO만 사용하는데, 2PC(Two-Phase Commit)가 아닌 Saga가 정말 필요한가요?

**🟢 Optimistic PM:**
> 좋은 지적입니다. 하지만 PRD의 Saga는 간단한 보상 트랜잭션(Compensating Transaction)입니다:
> 1. DB PENDING 저장 → 2. MinIO 업로드 → 3. DB ACTIVE 변경
> 실패 시: MinIO 삭제 + DB 삭제

> 이건 진정한 Saga Orchestrator가 아니라 try-catch 기반 보상 패턴입니다. [Baeldung](https://www.baeldung.com/cs/saga-pattern-microservices)에서도 이를 단순 Saga로 분류합니다. 블로그 교육 목적으로 충분합니다.

**✅ 합의**: 단순 보상 트랜잭션 패턴으로 구현 - 교육 목적 충분

---

### Round 5: 파일 업로드 보안 (Apache Tika)

**🔴 Critical PM:**
> [Apache Tika](https://tika.apache.org/3.2.3/detection)의 `DefaultDetector`를 사용한 MIME 타입 검증은 필수입니다. 확장자만 검증하면 `.docx` 확장자에 실행 파일을 숨길 수 있습니다.

```java
TikaConfig tika = new TikaConfig();
String mimetype = tika.getDetector().detect(TikaInputStream.get(file), metadata);
```

**🟢 Optimistic PM:**
> 동의합니다. 추가로 압축 폭탄(Zip Bomb) 검증도 필수입니다. Office 파일은 ZIP 포맷이므로 압축 해제 시 1GB 제한을 두어야 합니다.

**🔴 Critical PM:**
> PRD에 `validateZipBomb(file)` 함수가 명시되어 있지만, 구체적인 구현 가이드가 없습니다. 테스트 케이스도 명시해야 합니다.

**✅ 합의**: Apache Tika MIME 검증 + 압축 폭탄 검증 필수, 테스트 케이스 명시 필요

---

### Round 6: 일정 (2주 → 3주)

**🟢 Optimistic PM:**
> 3주는 현실적입니다. 보안 작업 5일, 통합 테스트 4일이 추가되었습니다. 하지만 4주(버퍼 1주 포함)로 계획되어 있어 충분히 여유롭습니다.

**🔴 Critical PM:**
> 하지만 Week 3에 Frontend + 통합 테스트 + 보안 테스트 + 문서화 + 블로그 작성이 모두 몰려 있습니다. 이건 병목이 될 수 있습니다. Frontend와 Backend 개발을 더 병렬화해야 합니다.

**🟢 Optimistic PM:**
> 맞습니다. Week 1-2에 API 스펙이 확정되면 Frontend 개발자가 Mock 기반으로 병렬 작업 가능합니다. Swagger/OpenAPI 문서를 Week 1에 확정하면 됩니다.

**✅ 합의**: 3주 일정 승인 - Week 1에 OpenAPI 스펙 확정으로 병렬 개발 권장

---

### Round 7: 문서 상태 관리 (PENDING/ACTIVE/DELETED)

**🔴 Critical PM:**
> Soft Delete 패턴은 좋지만, `DELETED` 상태 문서의 MinIO 파일은 언제 실제 삭제하나요? PRD에 정리 정책(Retention Policy)이 없습니다.

**🟢 Optimistic PM:**
> 이건 3편 범위입니다. 2편에서는 soft delete만 구현하고, 실제 파일 삭제는 수동 또는 스케줄러로 처리합니다. 데모 프로젝트이므로 괜찮습니다.

**🔴 Critical PM:**
> 하지만 MinIO 스토리지가 계속 증가합니다. 적어도 Lifecycle Policy 문서화는 해야 합니다.

**✅ 합의**: Soft Delete만 2편 범위, Lifecycle Policy는 3편으로 연기하되 문서화 필요

---

### Round 8: Callback 큐 처리

**🔴 Critical PM:**
> `ExecutorService.newSingleThreadExecutor()`로 큐 처리하는 것은 단일 인스턴스에서만 작동합니다. 수평 확장 시 여러 인스턴스가 동일 Callback을 처리할 수 있습니다.

**🟢 Optimistic PM:**
> PRD 5.2절에서 "Backend 무상태(stateless) 설계로 다중 인스턴스 배포 가능"이라고 했지만, 이 큐 설계와 모순됩니다. 하지만 2편 범위에서는 단일 인스턴스만 테스트하므로 충분합니다.

**🔴 Critical PM:**
> 그렇다면 문서에 "단일 인스턴스 전용"이라고 명시해야 합니다. 3편에서 Redis 또는 Kafka 기반으로 개선할 수 있습니다.

**✅ 합의**: "단일 인스턴스 전용" 명시 필수, 3편에서 분산 큐 개선

---

### Round 9: 동시성 제어

**🔴 Critical PM:**
> 비관적 락(`findByFileKeyWithLock`)을 사용하지만, 데드락 위험이 있습니다. 락 순서를 문서화하고 타임아웃을 설정해야 합니다.

**🟢 Optimistic PM:**
> 동의합니다. JPA의 `@Lock(LockModeType.PESSIMISTIC_WRITE)`에 `@QueryHints(value = @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))`를 추가하면 됩니다.

**✅ 합의**: 비관적 락 타임아웃 3초 설정 필수

---

### Round 10: Monitoring (Spring Actuator)

**🟢 Optimistic PM:**
> Spring Boot Actuator 추가는 훌륭합니다. `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` 엔드포인트로 운영 가시성을 확보합니다.

**🔴 Critical PM:**
> 하지만 Actuator 엔드포인트 보안은? 프로덕션에서 `/actuator/**`가 공개되면 정보 유출 위험이 있습니다.

**🟢 Optimistic PM:**
> 2편은 데모이므로 괜찮지만, 문서에 경고를 추가해야 합니다. 프로덕션에서는 Spring Security로 보호해야 한다고요.

**✅ 합의**: Actuator 사용 승인 + 프로덕션 보안 경고 문서화 필수

---

### Round 11: 환경 변수 관리

**🔴 Critical PM:**
> `.env.example` 템플릿은 좋지만, JWT Secret이 최소 32자여야 한다는 것이 명확하지 않습니다. `ONLYOFFICE_JWT_SECRET=change-me-to-random-32-chars-minimum`보다는 유효성 검증 로직이 필요합니다.

**🟢 Optimistic PM:**
> 애플리케이션 시작 시 시크릿 길이 검증을 추가할 수 있습니다:

```java
@PostConstruct
void validateSecrets() {
    if (jwtSecret.length() < 32) {
        throw new IllegalStateException("JWT secret must be at least 32 characters");
    }
}
```

**✅ 합의**: 애플리케이션 시작 시 시크릿 길이 검증 로직 추가

---

### Round 12: 테스트 전략

**🔴 Critical PM:**
> 70% 커버리지 목표는 좋지만, 어떤 코드를 측정하나요? 전체 코드? 비즈니스 로직만? Service 레이어만?

**🟢 Optimistic PM:**
> Service 레이어와 Security 레이어에 집중해야 합니다. Controller, Entity는 단순하므로 낮은 커버리지도 괜찮습니다.

**🔴 Critical PM:**
> PRD에 명시해야 합니다. "Service 및 Security 패키지 80% 커버리지"처럼요.

**✅ 합의**: "Service 및 Security 패키지 80% 커버리지" 명시

---

### Round 13: Presigned URL 보안

**🔴 Critical PM:**
> [MinIO Presigned URL](https://github.com/minio/docs)은 1시간 만료로 설정되어 있습니다. ONLYOFFICE 편집 세션이 1시간을 초과하면 어떻게 되나요?

**🟢 Optimistic PM:**
> ONLYOFFICE는 처음 Config 요청 시 URL을 가져오고, 이후에는 Callback URL만 사용합니다. 편집 중에는 ONLYOFFICE 서버 캐시를 사용하므로 괜찮습니다.

**🔴 Critical PM:**
> 하지만 저장 후 다시 열기 시에는? 새 Config 요청으로 새 Presigned URL이 발급됩니다. 맞습니다, 문제없습니다.

**✅ 합의**: Presigned URL 1시간 만료 유지 - 문제없음 확인

---

### Round 14: 에러 응답 표준화

**🟢 Optimistic PM:**
> PRD에 에러 응답 형식이 명시되어 있어 좋습니다:
```json
{ "error": "INVALID_FILE_TYPE", "message": "허용되지 않은 파일 형식입니다" }
```

**🔴 Critical PM:**
> 하지만 에러 코드가 일관성이 없습니다. `INVALID_FILE_TYPE`, `FILE_TOO_LARGE`, `SECURITY_VIOLATION`... 네이밍 컨벤션을 정의해야 합니다. UPPER_SNAKE_CASE로 통일하고 있지만, 목록이 필요합니다.

**✅ 합의**: 에러 코드 목록 정의 필요 (UPPER_SNAKE_CASE 통일)

---

### Round 15: Frontend 상태 관리

**🟢 Optimistic PM:**
> TanStack Query의 Optimistic Update는 UX를 크게 개선합니다. 업로드 완료 전에 목록에 표시되면 사용자가 기다리지 않아도 됩니다.

**🔴 Critical PM:**
> 하지만 PENDING 상태 문서는 회색으로 표시된다고 했는데, 이게 Optimistic Update와 어떻게 연동되나요? 클라이언트에서 PENDING 상태를 어떻게 알죠?

**🟢 Optimistic PM:**
> 업로드 API 응답에 `status: "PENDING"`이 포함됩니다. TanStack Query의 `onMutate`에서 임시 데이터를 추가하고, `onSuccess`에서 실제 응답으로 교체합니다.

**✅ 합의**: Optimistic Update + PENDING 상태 UI 연동 방식 확정

---

### Round 16: OWASP Top 10 테스트

**🔴 Critical PM:**
> "보안 테스트 (OWASP Top 10)" 항목이 있지만, 어떤 도구를 사용하나요? OWASP ZAP? Burp Suite? 수동 테스트?

**🟢 Optimistic PM:**
> 이 데모에서는 OWASP ZAP 자동 스캔으로 충분합니다. 수동 펜테스팅은 과도합니다.

**🔴 Critical PM:**
> 동의합니다. 하지만 최소한 다음은 수동 확인해야 합니다:
> 1. Path Traversal (`../../../etc/passwd`)
> 2. MIME 우회 (실행 파일을 .docx로 위장)
> 3. JWT 없는 Callback 요청

**✅ 합의**: OWASP ZAP 자동 스캔 + 3개 핵심 시나리오 수동 테스트

---

### Round 17: Docker Compose 볼륨

**🟢 Optimistic PM:**
> PostgreSQL, MinIO, ONLYOFFICE 모두 볼륨 마운트가 추가되어 데이터 영속성이 보장됩니다.

**🔴 Critical PM:**
> 하지만 ONLYOFFICE 볼륨은 PRD에 명시되지 않았습니다. 어떤 디렉토리를 마운트하나요?

**🟢 Optimistic PM:**
> `/var/lib/onlyoffice`를 마운트해야 합니다. 캐시와 설정이 저장됩니다. PRD에 추가해야 합니다.

**✅ 합의**: ONLYOFFICE 볼륨 `/var/lib/onlyoffice` 마운트 PRD에 추가

---

### Round 18: API 버전관리

**🔴 Critical PM:**
> `/api/documents/{id}/config`는 RESTful 개선이지만, API 버전이 없습니다. `/api/v1/documents/...`로 해야 향후 호환성이 보장됩니다.

**🟢 Optimistic PM:**
> 데모 프로젝트이므로 버저닝은 과도합니다. 3편에서 인증 API 추가 시 고려할 수 있습니다.

**🔴 Critical PM:**
> 하지만 블로그 시리즈라면 베스트 프랙티스를 보여줘야 합니다. v1 접두사 추가는 1분 작업입니다.

**🟢 Optimistic PM:**
> 합리적입니다. 추가하겠습니다.

**✅ 합의**: API 버전 접두사 `/api/v1/` 추가

---

### Round 19: 문서화 기준

**🟢 Optimistic PM:**
> README, API 명세서, 아키텍처 다이어그램, 보안 가이드, 블로그가 모두 계획되어 있습니다.

**🔴 Critical PM:**
> 하지만 "보안 가이드"의 범위가 불명확합니다. 개발자용 보안 코딩 가이드? 운영자용 배포 가이드? 둘 다 필요합니다.

**🟢 Optimistic PM:**
> 2편에서는 개발자용만, 3편에서 운영자용을 추가하겠습니다.

**✅ 합의**: 2편 "개발자용 보안 코딩 가이드", 3편 "운영자용 배포 가이드"

---

### Round 20: 최종 합의

**🔴 Critical PM:**
> PRD v2.0은 v1.0 대비 크게 개선되었습니다. 핵심 보안 이슈(JWT, 파일 검증)가 해결되었고, 프로덕션 준비도가 높아졌습니다. 다음 조건으로 승인합니다:
> 1. 통합 테스트 필수 완료 (Callback, Saga)
> 2. OWASP ZAP 스캔 통과
> 3. 시크릿 환경 변수화 검증
> 4. API 버전 접두사 추가
> 5. Callback 큐 "단일 인스턴스 전용" 명시

**🟢 Optimistic PM:**
> 모든 조건에 동의합니다. 추가로 다음을 제안합니다:
> 1. Week 1에 OpenAPI 스펙 확정으로 Frontend 병렬 개발
> 2. ONLYOFFICE 볼륨 마운트 경로 명시
> 3. Actuator 엔드포인트 보안 경고 문서화

**✅ 최종 합의 완료**

---

## 📊 토론 결과 요약

### 합의된 변경사항

| 항목 | 원안 | 합의안 |
|------|------|--------|
| API 버전 | `/api/documents` | `/api/v1/documents` |
| Callback 큐 | 범용 설계 | "단일 인스턴스 전용" 명시 |
| ONLYOFFICE 볼륨 | 미명시 | `/var/lib/onlyoffice` 마운트 추가 |
| Actuator 보안 | 미언급 | 프로덕션 보안 경고 추가 |
| 테스트 커버리지 | 70% 전체 | Service/Security 80% 명시 |
| 보안 가이드 | 범위 불명확 | "개발자용 보안 코딩 가이드" 명시 |
| 비관적 락 | 타임아웃 미명시 | 3초 타임아웃 설정 |
| 시크릿 검증 | 문서만 | 애플리케이션 시작 시 검증 로직 추가 |
| 보안 테스트 | OWASP Top 10 | OWASP ZAP + 3개 수동 테스트 |

### 승인 상태

| PM | 결정 | 조건 |
|---|---|---|
| 🔴 Critical PM | ✅ **조건부 승인** | 5개 조건 충족 시 |
| 🟢 Optimistic PM | ✅ **승인** | 3개 추가 제안 반영 시 |

### Critical PM 승인 조건 (필수)

1. ✅ 통합 테스트 필수 완료 (Callback, Saga)
2. ✅ OWASP ZAP 스캔 통과
3. ✅ 시크릿 환경 변수화 검증
4. ✅ API 버전 접두사 `/api/v1/` 추가
5. ✅ Callback 큐 "단일 인스턴스 전용" 명시

### Optimistic PM 추가 제안 (권장)

1. ✅ Week 1에 OpenAPI 스펙 확정으로 Frontend 병렬 개발
2. ✅ ONLYOFFICE 볼륨 마운트 경로 `/var/lib/onlyoffice` 명시
3. ✅ Actuator 엔드포인트 프로덕션 보안 경고 문서화

---

## 🔧 PRD v2.0 수정 권고사항

### 즉시 반영 (2편 필수)

```diff
# 4.1 Backend API Specifications

- **Endpoint:** `GET /api/documents`
+ **Endpoint:** `GET /api/v1/documents`

- **Endpoint:** `POST /api/documents`
+ **Endpoint:** `POST /api/v1/documents`

- **Endpoint:** `DELETE /api/documents/{id}`
+ **Endpoint:** `DELETE /api/v1/documents/{id}`

- **Endpoint:** `GET /api/documents/{id}/config`
+ **Endpoint:** `GET /api/v1/documents/{id}/config`

- **Endpoint:** `POST /api/callback`
+ **Endpoint:** `POST /api/v1/callback`
```

### 문서 추가 사항

```markdown
## 주의사항

### Callback 큐 제한
현재 Callback 큐 처리는 **단일 인스턴스 전용**입니다.
수평 확장 시 Redis 또는 Kafka 기반 분산 큐로 개선이 필요합니다. (3편 예정)

### Actuator 보안 경고
⚠️ 프로덕션 환경에서는 `/actuator/**` 엔드포인트를 Spring Security로 보호해야 합니다.
```

### Docker Compose 추가

```yaml
services:
  onlyoffice:
    # ... 기존 설정
    volumes:
      - onlyoffice_data:/var/lib/onlyoffice

volumes:
  onlyoffice_data:
    driver: local
```

### 시크릿 검증 코드 추가

```java
@Configuration
public class SecurityConfig {

    @Value("${onlyoffice.secret}")
    private String jwtSecret;

    @PostConstruct
    void validateSecrets() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters. " +
                "Current length: " + (jwtSecret != null ? jwtSecret.length() : 0)
            );
        }
    }
}
```

---

## 📚 핵심 기술 참조 (Sources)

### ONLYOFFICE
- [ONLYOFFICE JWT Configuration](https://helpcenter.onlyoffice.com/docs/installation/docs-configure-jwt.aspx)
- [ONLYOFFICE Security FAQ](https://api.onlyoffice.com/editors/faq/security)
- [ONLYOFFICE Security Guide](https://helpcenter.onlyoffice.com/docs/installation/docs-securityguide.aspx)

### MinIO
- [MinIO Official Documentation](https://min.io/)
- [MinIO Presigned URL Guide](https://github.com/minio/docs)
- [MinIO Docker Setup](https://hub.docker.com/r/minio/minio)

### Saga Pattern
- [Saga Pattern - microservices.io](https://microservices.io/patterns/data/saga.html)
- [Saga Pattern Implementation - Baeldung](https://www.baeldung.com/cs/saga-pattern-microservices)
- [Saga Pattern in Microservices - JavaGuides](https://www.javaguides.net/2025/02/saga-pattern-in-microservices.html)

### File Security
- [Apache Tika Detection](https://tika.apache.org/3.2.3/detection)

### Frontend
- [Next.js App Router](https://nextjs.org/docs/app)
- [TanStack vs Next.js Comparison](https://tanstack.com/router/v1/docs/framework/react/comparison)
- [TanStack Start vs Next.js](https://blog.logrocket.com/tanstack-start-vs-next-js-choosing-the-right-full-stack-react-framework/)

### Security Best Practices
- [JWT Security Best Practices 2025](https://jwt.app/blog/jwt-best-practices/)
- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/reference/actuator/index.html)

---

## 📝 문서 이력

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| 1.0 | 2025-12-04 | AI Assistant | 초안 작성 - 20라운드 토론 결과 |

---

**문서 버전**: 1.0
**최종 수정일**: 2025-12-04
**다음 문서**: 구현 가이드 (Implementation Guide)
