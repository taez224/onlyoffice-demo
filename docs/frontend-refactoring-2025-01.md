# Frontend Architecture Refactoring (2025-01)

## Overview

Next.js 16 App Router 프론트엔드의 아키텍처 개선 작업 기록.

**작업 기간**: 2025-01-07  
**주요 목표**:
1. 모놀리식 `page.tsx` (566줄) 컴포넌트 분리
2. Server Component + SSR Prefetch 적용
3. 공유 유틸리티 추출 및 중앙화
4. Streaming SSR 적용 (Header 즉시 표시 + 테이블 점진적 로딩)

---

## 1. 컴포넌트 분리 리팩토링

### Before

```
app/page.tsx (566 lines)
├── 상수 정의 (MAX_FILE_SIZE, ALLOWED_EXTENSIONS, ...)
├── 유틸 함수 (validateFile, formatFileSize, formatDateTime, ...)
├── 파일 아이콘 함수 (getFileIcon, getTypeBadgeClass)
├── 테이블 헬퍼 컴포넌트 (SortableHeader, SelectCheckbox, ...)
├── 컬럼 정의 (columns)
├── 메인 컴포넌트 (HomePage)
│   ├── 상태 관리 (selectedIds, sorting, deleteDialogOpen)
│   ├── 데이터 페칭 (useDocuments, useUploadDocument, useDeleteDocuments)
│   ├── 이벤트 핸들러
│   └── UI 렌더링 (헤더, 테이블, BulkActionBar, DeleteDialog)
└── 모든 것이 하나의 파일에...
```

### After

```
frontend/src/
├── app/
│   ├── page.tsx                     # 5줄 (Server Component, Streaming)
│   ├── loading.tsx                  # 루트 로딩 UI
│   ├── error.tsx                    # 루트 에러 UI
│   └── editor/[fileKey]/
│       └── loading.tsx              # 에디터 로딩 UI
│
├── components/documents/
│   ├── index.ts                     # barrel export
│   ├── documents-page.tsx           # 메인 Client Component + Suspense
│   ├── document-list.tsx            # useSuspenseQuery 사용 (신규)
│   ├── document-table.tsx           # 테이블 컴포넌트 (~320줄)
│   ├── documents-error-boundary.tsx # QueryErrorResetBoundary (신규)
│   ├── table-skeleton.tsx           # 테이블 스켈레톤 UI (신규)
│   ├── bulk-action-bar.tsx          # 선택 액션 바 (~60줄)
│   ├── delete-confirm-dialog.tsx    # 삭제 확인 다이얼로그 (~45줄)
│   └── upload-button.tsx            # 업로드 버튼 (~55줄)
│
├── hooks/
│   └── use-documents.ts             # useDocuments + useDocumentsSuspense
│
└── lib/
    ├── format.ts                    # formatFileSize, formatDateTime
    ├── query-client.ts              # SSR-safe QueryClient
    └── validation/
        ├── index.ts                 # barrel export
        └── file.ts                  # UUID_REGEX, validateFile, etc.
```

### 결과 메트릭

| 항목 | Before | After | 변화 |
|------|--------|-------|------|
| `app/page.tsx` | 566줄 | 5줄 | **-99%** |
| 컴포넌트 수 | 1개 (모놀리식) | 8개 (분리됨) | - |
| Route segments | 0개 | 3개 | - |
| Shared utils | 인라인 | 중앙화 | - |
| Streaming SSR | ❌ | ✅ | - |

---

## 2. SSR Prefetch 적용

### 문제점 (Before)

- 전체 페이지가 `'use client'`로 클라이언트 렌더링
- 첫 로드 시 빈 화면 + 스피너 표시
- SSR의 이점 (빠른 FCP, SEO) 활용 못함

### 해결책 (After)

TanStack Query v5 + Next.js App Router의 공식 SSR 패턴 적용.

> **참고**: 이후 Streaming SSR로 변경됨 (섹션 10 참조). 아래는 초기 Prefetch 접근법 기록.

```
┌─────────────────────────────────────────────────────────────┐
│  Server Component (app/page.tsx) - 초기 버전               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  await prefetchQuery(documentsQueryOptions())         │  │
│  │  ↓                                                    │  │
│  │  <HydrationBoundary state={dehydrate(queryClient)}>   │  │
│  │    ┌─────────────────────────────────────────────┐    │  │
│  │    │  Client Component (DocumentsPage)           │    │  │
│  │    │  - useQuery() ← 이미 hydrated 데이터 사용   │    │  │
│  │    │  - useState, onClick 등 인터랙션            │    │  │
│  │    └─────────────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 핵심 코드 변경

**`lib/query-client.ts`** (신규)
```typescript
import { QueryClient } from '@tanstack/react-query';
import { cache } from 'react';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
      },
    },
  });
}

// Server: cache()로 request-scoped (data leakage 방지)
// Client: singleton pattern
let browserQueryClient: QueryClient | undefined;

export const getQueryClient = cache(() => {
  if (typeof window === 'undefined') {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
});
```

**`app/page.tsx`** (Server Component)
```typescript
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getQueryClient } from '@/lib/query-client';
import { documentsQueryOptions } from '@/lib/queries/documents';
import { DocumentsPage } from '@/components/documents';

export default async function HomePage() {
  const queryClient = getQueryClient();
  await queryClient.prefetchQuery(documentsQueryOptions());

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
```

**`lib/api-client.ts`** (서버 호환성 추가)
```typescript
function getBaseUrl() {
  if (typeof window === 'undefined') {
    return process.env.INTERNAL_API_URL || 'http://localhost:8080/api';
  }
  return '/api';
}

export const apiClient = axios.create({
  baseURL: getBaseUrl(),
  // ...
});
```

### 예상 성능 개선

| 메트릭 | Before | After |
|--------|--------|-------|
| 첫 렌더 | 빈 화면 + 스피너 | 데이터 포함 HTML |
| FCP (First Contentful Paint) | ~1.5s | ~0.5s |
| LCP (Largest Contentful Paint) | ~2s | ~1s |
| 클라이언트 JS | 전체 페이지 | 인터랙션만 |

---

## 3. 공유 유틸리티 추출

### `lib/validation/file.ts`

```typescript
export const MAX_FILE_SIZE_MB = 100;
export const MAX_FILE_SIZE = MAX_FILE_SIZE_MB * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ['docx', 'xlsx', 'pptx', 'pdf'] as const;

// RFC 4122 UUID pattern
export const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isValidUUID(value: string): boolean { ... }
export function getFileExtension(fileName: string): string | undefined { ... }
export function isAllowedExtension(extension: string): boolean { ... }
export function validateFile(file: File, formatFileSize: fn): string | null { ... }
```

### `lib/format.ts`

```typescript
export function formatFileSize(bytes: number): string { ... }
export function formatDateTime(isoString: string): string { ... }
```

### 사용처

| 유틸리티 | 사용 위치 |
|---------|----------|
| `isValidUUID` | `app/editor/[fileKey]/page.tsx` |
| `validateFile` | `components/documents/upload-button.tsx` |
| `formatFileSize` | `components/documents/document-table.tsx` |
| `formatDateTime` | `components/documents/document-table.tsx` |

---

## 4. Route Segments 추가

### `app/loading.tsx`
```typescript
export default function Loading() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <Loader2 size={32} className="animate-spin text-muted-foreground" />
    </div>
  );
}
```

### `app/error.tsx`
```typescript
'use client';

export default function Error({ error, reset }) {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <AlertCircle size={28} className="text-red-600" />
        <h2>오류가 발생했습니다</h2>
        <Button onClick={reset}>다시 시도</Button>
      </div>
    </div>
  );
}
```

### `app/editor/[fileKey]/loading.tsx`
에디터 전용 로딩 UI (ONLYOFFICE 초기화 대기 상태).

---

## 5. 참고 자료

### TanStack Query v5 SSR (2024-2025 Best Practice)

- `HydrationBoundary` 사용 (v5에서 `Hydrate` 대체)
- React `cache()` 함수로 request-scoped QueryClient
- `queryOptions()` 헬퍼로 쿼리 정의 재사용
- `staleTime > 0` 설정으로 즉시 refetch 방지

### Next.js 15+ 변경사항

- `params`가 `Promise`로 변경 - `await params` 필요
- Server Component가 기본, `'use client'`는 인터랙션에만 사용
- `loading.tsx`, `error.tsx` route segments 활용

---

## 6. 환경변수

서버 사이드 API 호출을 위한 환경변수 (선택):

```bash
# .env.local
INTERNAL_API_URL=http://localhost:8080/api
```

프로덕션에서는 내부 네트워크 URL 사용 가능 (예: `http://backend:8080/api`).

---

## 7. 빌드 결과

```
▲ Next.js 16.1.1 (Turbopack)

✓ Compiled successfully
✓ Generating static pages (4/4)

Route (app)
├ ○ /                    # Static (SSR at runtime with prefetch)
├ ○ /_not-found          # Static
└ ƒ /editor/[fileKey]    # Dynamic
```

---

## 8. SSR Prefetch 테스트 방법

### 8.1 서버 실행

```bash
# 터미널 1: 백엔드
cd backend
./gradlew bootRun

# 터미널 2: 프론트엔드
cd frontend
pnpm dev
```

### 8.2 SSR 확인 방법

#### 방법 A: 페이지 소스 보기

1. `http://localhost:3000` 접속
2. 우클릭 → **"페이지 소스 보기"** (Ctrl+U / Cmd+U)
3. **확인할 것**: HTML에 문서 데이터가 포함되어 있는지

**Before (CSR)**: 빈 HTML + JS가 데이터 fetch
```html
<div id="__next"></div>
<script>/* 데이터 없음, JS가 나중에 fetch */</script>
```

**After (SSR)**: HTML에 데이터 포함
```html
<script id="__NEXT_DATA__" type="application/json">
  {"props":{"pageProps":{"dehydratedState":{"queries":[{"state":{"data":[
    {"id":1,"fileName":"sample.docx",...},
    {"id":2,"fileName":"sample.xlsx",...}
  ]}}]}}}}
</script>
```

#### 방법 B: Network 탭 확인

1. 개발자 도구 → Network 탭
2. "Disable cache" 체크
3. 페이지 새로고침
4. **확인할 것**:
   - 첫 HTML 응답에 데이터 포함
   - `/api/documents` 요청이 **서버에서** 발생 (클라이언트 아님)

#### 방법 C: JavaScript 비활성화 테스트

1. 개발자 도구 → Settings (F1)
2. "Disable JavaScript" 체크
3. 페이지 새로고침
4. **확인할 것**: JS 없이도 문서 목록이 보임 (SSR 성공)

### 8.3 터미널 테스트

```bash
# HTML 응답에서 문서 데이터 확인
curl -s http://localhost:3000 | grep -o '"fileName":"[^"]*"' | head -5
```

데이터가 출력되면 SSR 성공:
```
"fileName":"sample.docx"
"fileName":"sample.xlsx"
"fileName":"sample.pptx"
```

### 8.4 React Query Devtools 확인

1. 페이지 하단 React Query 아이콘 클릭
2. **확인할 것**:
   - `documents` 쿼리가 `fresh` 상태
   - `fetchStatus`가 `idle` (이미 데이터 있음)
   - **데이터가 있는데 loading 없음** = hydration 성공

### 8.5 Before/After 비교 (선택)

SSR 효과를 직접 비교하려면:

```tsx
// app/page.tsx - SSR 끄기 (테스트용)
export default async function HomePage() {
  const queryClient = getQueryClient();
  
  // await queryClient.prefetchQuery(documentsQueryOptions());  // 주석 처리
  
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
```

- **prefetch 주석 처리 후**: 스피너가 먼저 보임
- **prefetch 활성화 후**: 즉시 데이터 표시

### 8.6 Lighthouse 성능 측정

```bash
npx lighthouse http://localhost:3000 --view
```

또는 Chrome DevTools → Lighthouse 탭 사용.

| 메트릭 | Before (CSR) | After (SSR) |
|--------|-------------|-------------|
| FCP | ~1.5s | ~0.5s |
| LCP | ~2.0s | ~1.0s |
| CLS | 높음 (레이아웃 이동) | 낮음 |

### 8.7 테스트 체크리스트

| 테스트 | 확인 방법 | 성공 기준 |
|--------|----------|----------|
| HTML 소스 | Ctrl+U | 문서 데이터 JSON 포함 |
| JS 비활성화 | DevTools 설정 | 문서 목록 표시됨 |
| Network | DevTools | 클라이언트 `/api/documents` 요청 없음 |
| React Query | Devtools | 쿼리가 fresh 상태로 시작 |
| curl | 터미널 | fileName 출력됨 |

---

## 9. React `cache()` 함수 설명

### 정의

React 18.3+에서 제공하는 **요청 단위 메모이제이션** 함수.
동일한 인자로 호출되면 캐시된 결과를 반환.

```tsx
import { cache } from 'react';

const getUser = cache(async (id: string) => {
  const res = await fetch(`/api/users/${id}`);
  return res.json();
});
```

### 핵심 특징

| 특징 | 설명 |
|------|------|
| **Request-scoped** | 서버 요청(렌더링) 단위로 캐시가 격리됨 |
| **자동 초기화** | 요청이 끝나면 캐시가 자동으로 사라짐 |
| **인자 기반 캐싱** | 동일한 인자 → 동일한 결과 반환 |
| **Server Component 전용** | 주로 SSR에서 사용 |

### QueryClient에서 사용하는 이유

```tsx
export const getQueryClient = cache(() => new QueryClient());
```

**목적**: 각 서버 요청마다 **독립적인 QueryClient 인스턴스** 생성

| 시나리오 | `cache()` 없음 | `cache()` 있음 |
|---------|---------------|---------------|
| 요청 A의 데이터 | 공유됨 (위험!) | 격리됨 ✓ |
| 요청 B의 데이터 | 공유됨 (위험!) | 격리됨 ✓ |
| 같은 요청 내 여러 컴포넌트 | 매번 새 인스턴스 | 동일 인스턴스 재사용 ✓ |

**보안**: 사용자 A의 prefetch 데이터가 사용자 B에게 노출되는 것 방지.

### `cache()` vs `useMemo()` vs `React.memo()`

| 함수 | 스코프 | 용도 |
|------|--------|------|
| `cache()` | **서버 요청 단위** | 서버 데이터 fetch dedupe |
| `useMemo()` | 컴포넌트 라이프사이클 | 클라이언트 계산 메모이제이션 |
| `React.memo()` | 컴포넌트 props | 컴포넌트 리렌더링 방지 |

---

## 10. Streaming SSR 개선

### 10.1 문제 상황

SSR Prefetch 적용 후 예상과 다른 동작 발생:

| 예상 동작 | 실제 동작 |
|----------|----------|
| Header 즉시 표시 → 테이블 스켈레톤 → 데이터 스트리밍 | 전체 페이지 로딩 스피너 → Header + 테이블 동시 표시 |

**원인**: `await prefetchQuery()`가 Server Component 전체를 **블로킹**

```tsx
// 문제 코드 (app/page.tsx)
export default async function HomePage() {
  const queryClient = getQueryClient();
  
  await queryClient.prefetchQuery(documentsQueryOptions());  // ← 블로킹!
  
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsPage />
    </HydrationBoundary>
  );
}
```

**흐름**:
1. 서버가 `prefetchQuery()` 완료까지 대기 (블로킹)
2. 그 동안 `loading.tsx` 표시 (전체 페이지 스피너)
3. 데이터 준비되면 전체 페이지 한번에 전송

### 10.2 Streaming SSR 컴포넌트 구현

클라이언트에서 Suspense가 작동하도록 다음 컴포넌트 추가:

**`components/documents/table-skeleton.tsx`** (신규)
```tsx
export function TableSkeleton() {
  return (
    <div className="p-4 space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center space-x-4">
          <Skeleton className="h-4 w-4" />
          <Skeleton className="h-4 w-8" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-32" />
        </div>
      ))}
    </div>
  );
}
```

**`components/documents/document-list.tsx`** (신규)
```tsx
'use client';

import { useDocumentsSuspense } from '@/hooks/use-documents';
import { DocumentTable } from './document-table';

export function DocumentList({ ... }: DocumentListProps) {
  const { data: documents } = useDocumentsSuspense();  // ← Suspense 트리거
  return <DocumentTable documents={documents} {...props} />;
}
```

**`hooks/use-documents.ts`** (useSuspenseQuery 추가)
```tsx
import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { documentsQueryOptions } from '@/lib/queries/documents';

export function useDocuments() {
  return useQuery(documentsQueryOptions());
}

export function useDocumentsSuspense() {
  return useSuspenseQuery(documentsQueryOptions());
}
```

**`components/documents/documents-error-boundary.tsx`** (신규)
```tsx
'use client';

import { QueryErrorResetBoundary } from '@tanstack/react-query';
import { ErrorBoundary } from 'react-error-boundary';

export function DocumentsErrorBoundary({ children }: { children: React.ReactNode }) {
  return (
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary onReset={reset} fallbackRender={({ resetErrorBoundary }) => (
          <div className="p-8 text-center">
            <p>데이터를 불러오는 중 오류가 발생했습니다.</p>
            <Button onClick={resetErrorBoundary}>다시 시도</Button>
          </div>
        )}>
          {children}
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
```

**`components/documents/documents-page.tsx`** (Suspense 추가)
```tsx
'use client';

import { Suspense } from 'react';
import { DocumentsErrorBoundary } from './documents-error-boundary';
import { DocumentList } from './document-list';
import { TableSkeleton } from './table-skeleton';

export function DocumentsPage() {
  return (
    <div className="...">
      {/* Header - 즉시 렌더링 */}
      <div className="...">
        <h1>Documents</h1>
        <UploadButton />
      </div>
      
      {/* Table - Suspense로 스트리밍 */}
      <DocumentsErrorBoundary>
        <Suspense fallback={<TableSkeleton />}>
          <DocumentList ... />
        </Suspense>
      </DocumentsErrorBoundary>
    </div>
  );
}
```

### 10.3 해결책: Prefetch 제거

Streaming이 작동하려면 서버가 블로킹하지 않아야 함:

**수정된 `app/page.tsx`**
```tsx
import { DocumentsPage } from '@/components/documents';

export default function HomePage() {
  return <DocumentsPage />;
}
```

**변경 내용**:
- `await prefetchQuery()` 제거 (블로킹 해제)
- `HydrationBoundary` 제거 (prefetch 없으면 불필요)
- Server Component → sync 함수로 변경

### 10.4 예상 동작 (After)

```
┌─────────────────────────────────────────────────────────────┐
│  1. 즉시 (0ms)                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Documents                            [📤 Upload]     │  │
│  │  Manage your secure documents.                        │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  ▓▓▓▓ ▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓  │  │ ← TableSkeleton
│  │  ▓▓▓▓ ▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓  │  │
│  │  ▓▓▓▓ ▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓▓▓▓▓▓  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  2. 데이터 로드 후 (~300ms)                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Documents                            [📤 Upload]     │  │
│  │  Manage your secure documents.                        │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  ☑ 📄 sample.docx        1.2 MB    2025-01-07 10:30  │  │ ← 실제 데이터
│  │  ☐ 📊 sample.xlsx        856 KB    2025-01-07 09:15  │  │
│  │  ☐ 📑 sample.pptx        2.4 MB    2025-01-06 14:20  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 10.5 Tradeoff 분석

| 접근법 | UX | SEO | 구현 복잡도 |
|--------|----|----|------------|
| **SSR Prefetch** (await) | ❌ 전체 로딩 | ✅ HTML에 데이터 포함 | 낮음 |
| **Streaming SSR** (no await) | ✅ 점진적 로딩 | ⚠️ 클라이언트 fetch | 중간 |
| **Hybrid** (선택적 prefetch) | ✅ 최적 | ✅ 최적 | 높음 |

**현재 선택: Streaming SSR**

이유:
- 문서 관리 앱은 SEO가 핵심 요구사항이 아님 (로그인 후 사용)
- 사용자 체감 속도(UX)가 더 중요
- Header가 먼저 보이면 앱이 빠르게 응답한다고 느낌

### 10.6 테스트 방법

```bash
cd frontend
pnpm dev
# http://localhost:3000 접속
```

**확인 사항**:
1. Header ("Documents") + Upload 버튼이 **즉시** 표시
2. 테이블 영역에 스켈레톤 표시
3. 약간의 딜레이 후 실제 데이터로 교체

**Network 탭 확인**:
- 첫 HTML 응답: Header만 포함 (데이터 없음)
- 클라이언트에서 `/api/documents` 요청 발생

---

## 11. 향후 개선 사항 (P1-P2)

| 우선순위 | 항목 | 설명 |
|---------|------|------|
| P1 | Editor config caching | `staleTime: 0` → 적절한 캐싱 적용 |
| P1 | Batch delete API | N개 병렬 요청 → 단일 batch endpoint |
| P2 | `fileType` 타입 강화 | `string` → `'docx' \| 'xlsx' \| ...` union |
| P2 | OnlyOffice config validation | Zod 등으로 런타임 검증 |

---

## 12. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2025-01-07 | 초기 문서 작성 (컴포넌트 분리, SSR Prefetch) |
| 2025-01-07 | Streaming SSR 개선 섹션 추가 |
