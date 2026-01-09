# ONLYOFFICE Demo - Frontend

Next.js 16 + React 19 기반 문서 편집 애플리케이션

## 기술 스택

- Next.js 16 (App Router)
- React 19 + TypeScript 5
- TanStack Query (서버 상태 관리)
- TanStack Table (데이터 테이블)
- shadcn/ui + Tailwind CSS

## 시작하기

### 설치

```bash
pnpm install
```

### 개발 서버

```bash
pnpm dev
```

http://localhost:3000 에서 확인

### 빌드

```bash
pnpm build
pnpm start
```

## 프로젝트 구조

```
src/
├── app/           # App Router 페이지
│   ├── page.tsx           # 문서 목록
│   └── editor/[fileKey]/  # 문서 편집기
├── components/    # React 컴포넌트
│   ├── documents/         # 문서 관련 컴포넌트
│   ├── providers/         # Context Provider
│   └── ui/                # shadcn/ui 컴포넌트
├── hooks/         # 커스텀 훅
├── lib/           # 유틸리티
├── api/           # API 호출 함수
└── types/         # TypeScript 타입
```

## 스크립트

| 명령어 | 설명 |
|--------|------|
| `pnpm dev` | 개발 서버 실행 |
| `pnpm build` | 프로덕션 빌드 |
| `pnpm start` | 프로덕션 서버 실행 |
| `pnpm lint` | ESLint 검사 |

## 환경 변수

`next.config.ts`에서 `/api` 요청을 백엔드로 프록시함
기본값: `http://localhost:8080`

## 문제 해결

- **API 호출 실패**: 백엔드 서버 실행 확인
- **에디터 로딩 실패**: Document Server 상태 및 JWT 설정 확인
