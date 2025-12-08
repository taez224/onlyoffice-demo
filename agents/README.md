# PM Agents

두 가지 관점을 가진 PM 에이전트들입니다.

## 🚀 Optimistic PM (낙관적 속도 중심)

**철학**: Move fast and iterate

```bash
./agents/optimistic-pm.sh
```

### 특징
- 빠른 실행과 배포 우선
- MVP 접근법
- 반복을 통한 개선
- 사용자 피드백 중심

### 언제 활용?
- 프로토타입 단계
- 새로운 기능 아이디어 검증
- 시장 진입 속도가 중요할 때
- 불확실성이 높은 초기 단계

---

## ⚠️ Critical PM (비판적 위험 중심)

**철학**: Measure twice, cut once

```bash
./agents/critical-pm.sh
```

### 특징
- 철저한 리스크 분석
- 장기적 관점
- 기술 부채 관리
- 보안과 안정성 우선

### 언제 활용?
- 프로덕션 배포 전
- 보안이 중요한 기능
- 스케일링 계획 수립
- 아키텍처 의사결정

---

## 사용법

### 1. 단독 실행
```bash
# 낙관적 관점 확인
./agents/optimistic-pm.sh

# 비판적 관점 확인
./agents/critical-pm.sh
```

### 2. 의사결정 시 둘 다 활용
```bash
# 두 관점을 모두 보고 균형잡힌 결정
./agents/optimistic-pm.sh > optimistic-view.txt
./agents/critical-pm.sh > critical-view.txt
diff optimistic-view.txt critical-view.txt
```

### 3. PRD 리뷰
```bash
# PRD 문서와 함께 검토
cat your-prd.md | grep -E "(feature|requirement)" > features.txt
./agents/optimistic-pm.sh  # 빠른 실행 계획
./agents/critical-pm.sh     # 위험 요소 점검
```

---

## 균형잡기

최고의 의사결정은 두 관점의 균형에서 나옵니다:

| 상황 | Optimistic 가중치 | Critical 가중치 |
|------|------------------|----------------|
| MVP/프로토타입 | 80% | 20% |
| 베타 출시 | 60% | 40% |
| 프로덕션 출시 | 40% | 60% |
| 엔터프라이즈/금융 | 20% | 80% |

---

## 현재 프로젝트 컨텍스트

**ONLYOFFICE Demo**: Spring Boot + Next.js + ONLYOFFICE

### Optimistic PM 제안
- 빠르게 데모 배포하고 사용자 피드백 수집
- 실시간 협업 기능으로 차별화
- 간단한 UI로 시작, 반복 개선

### Critical PM 우려사항
- JWT 시크릿 관리 보안
- 파일 업로드 검증
- 동시 편집 충돌 처리
- 프로덕션 모니터링 부재

### 권장 접근법
1. Critical PM의 보안 체크리스트 완료
2. Optimistic PM의 속도로 MVP 구현
3. 점진적으로 Critical PM의 우려사항 해결