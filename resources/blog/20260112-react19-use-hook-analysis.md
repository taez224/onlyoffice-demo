# React 19 use Hook 분석: 언제 써야 하고, 언제 쓰지 말아야 할까?

React 19에서 새롭게 도입된 `use` Hook은 Promise나 Context를 컴포넌트 내에서 직접 읽을 수 있게 해주는 강력한 도구입니다. 하지만 모든 새로운 기능이 그렇듯, **언제 사용해야 하는지**와 **언제 사용하지 말아야 하는지**를 아는 것이 더 중요합니다.

이번 글에서는 `onlyoffice-demo` 프로젝트의 프론트엔드를 분석하면서, `use` Hook의 적용 가능성과 실제 적용 시 주의점을 정리해보겠습니다.

---

## 1. use Hook이란?

`use`는 React 19에서 도입된 새로운 Hook으로, 두 가지 리소스 타입을 지원합니다:

### Promise 읽기
```tsx
import { use, Suspense } from 'react';

function MessageComponent({ messagePromise }) {
  // Promise가 resolve될 때까지 컴포넌트가 suspend됨
  const message = use(messagePromise);
  return <p>{message}</p>;
}

function App() {
  return (
    <Suspense fallback={<Loading />}>
      <MessageComponent messagePromise={fetchMessage()} />
    </Suspense>
  );
}
```

### Context 읽기
```tsx
import { use, createContext } from 'react';

const ThemeContext = createContext('light');

function ThemedButton({ show }) {
  // 조건부로 Context 읽기 가능! (useContext와의 차이점)
  if (show) {
    const theme = use(ThemeContext);
    return <button className={theme}>Themed</button>;
  }
  return <button>Default</button>;
}
```

### use의 핵심 차별점: 조건부 호출 가능

기존 Hook들과 달리, `use`는 **조건문이나 반복문 내에서 호출**할 수 있습니다.

```tsx
// ❌ useContext는 조건부 호출 불가
function Component({ show }) {
  if (show) {
    const theme = useContext(ThemeContext); // Rules of Hooks 위반!
  }
}

// ✅ use는 조건부 호출 가능
function Component({ show }) {
  if (show) {
    const theme = use(ThemeContext); // OK!
  }
}
```

---

## 2. 프로젝트 분석: use를 적용할 수 있는 곳은?

`onlyoffice-demo` 프로젝트의 프론트엔드(React 19 + Next.js 16 + TanStack Query)를 분석한 결과입니다.

### 현재 아키텍처 요약

| 영역 | 현재 패턴 | use 적용 가능성 |
|------|----------|-----------------|
| 문서 목록 페이지 | `useSuspenseQuery` + `Suspense` | ✅ 이미 최적 |
| 에디터 페이지 | `useQuery` + `isLoading` 체크 | ⚠️ 개선 여지 있음 |
| Context 사용 | 없음 (Props drilling) | ➖ 해당 없음 |
| Server Component | `prefetchQuery` + `HydrationBoundary` | ✅ 이미 최적 |

### 발견사항

1. **Context 미사용**: 프로젝트 전체에서 `useContext`가 사용되지 않음
2. **TanStack Query 활용**: 서버 상태는 `useSuspenseQuery`로 관리
3. **에디터 페이지 불일치**: 문서 목록은 Suspense 패턴, 에디터는 명령형 패턴

---

## 3. use vs TanStack Query: 무엇을 선택해야 할까?

### TanStack Query의 장점

```tsx
// TanStack Query 방식
const { data } = useSuspenseQuery({
  queryKey: ['documents'],
  queryFn: fetchDocuments,
  staleTime: 60 * 1000,      // 1분간 fresh
  gcTime: 5 * 60 * 1000,     // 5분간 캐시 유지
});
```

- **자동 캐싱**: 동일한 쿼리 재사용
- **자동 재검증**: staleTime 경과 후 백그라운드 갱신
- **낙관적 업데이트**: 뮤테이션 시 즉각 UI 반영
- **에러 재시도**: 네트워크 오류 시 자동 재시도

### use의 장점

```tsx
// use 방식 (Server → Client Promise 전달)
function ClientComponent({ dataPromise }) {
  const data = use(dataPromise);
  return <div>{data.title}</div>;
}
```

- **심플함**: 추가 라이브러리 불필요
- **조건부 호출**: 동적 Context 읽기 가능
- **Server Component 연동**: Promise를 props로 전달

### 결론: 상황에 따라 선택

| 상황 | 권장 도구 |
|------|----------|
| 서버 상태 관리 (CRUD) | TanStack Query |
| 단순 일회성 데이터 | `use` |
| 조건부 Context 읽기 | `use` |
| 캐싱/재검증 필요 | TanStack Query |

**우리 프로젝트**: TanStack Query가 이미 잘 활용되고 있으므로, `use`를 무리하게 도입할 필요 없음.

---

## 4. 에디터 Config API: 왜 Server Component에서 Prefetch하면 안 될까?

분석 과정에서 흥미로운 발견이 있었습니다. 에디터 설정 API(`/api/documents/{fileKey}/config`)는 **서버 컴포넌트에서 prefetch하면 안 되는** 대표적인 사례입니다.

### EditorConfigResponse 구조

```typescript
interface EditorConfigResponse {
  documentServerUrl: string;
  config: {
    document: {
      key: string;     // ⚠️ "{fileKey}_v{version}" - 버전 민감!
      title: string;
      url: string;
    };
    token?: string;    // ⚠️ JWT 토큰
  };
}
```

### 현재 Query Options 설정

```typescript
export function editorConfigQueryOptions(fileKey: string) {
  return queryOptions({
    queryKey: documentKeys.config(fileKey),
    queryFn: () => getEditorConfig(fileKey),
    staleTime: 0,   // ← 항상 stale
    gcTime: 0,      // ← 캐시 안 함
  });
}
```

개발자가 의도적으로 캐싱을 비활성화했습니다. 왜일까요?

### 문제 1: document.key 버전 불일치

ONLYOFFICE는 `document.key`로 편집 세션을 식별합니다.

```
Timeline:
─────────────────────────────────────────────────────────
  SSR 시점          다른 유저 저장       클라이언트 렌더링
  key: abc_v1    →   version++      →   key: abc_v1 (stale!)
                     (실제: abc_v2)
```

**결과**: 이전 세션과 충돌하여 편집 내용 손실 가능

### 문제 2: HydrationBoundary 캐싱

```tsx
// ❌ 위험한 패턴
export default function EditorPage({ params }) {
  const queryClient = getQueryClient();
  queryClient.prefetchQuery(editorConfigQueryOptions(params.fileKey));
  
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <EditorContent />  {/* SSR 데이터가 클라이언트로 전달됨 */}
    </HydrationBoundary>
  );
}
```

`staleTime: 0`으로 설정해도, dehydrated 상태가 클라이언트 캐시에 주입됩니다.

### 정리: Prefetch 가능 여부 판단 기준

| 데이터 특성 | Prefetch | 이유 |
|------------|----------|------|
| 문서 목록 | ✅ 가능 | 버전 무관, 캐싱 OK |
| 에디터 설정 | ⛔ 금지 | 버전/토큰 민감 |
| 사용자 정보 | ✅ 가능 | 세션 동안 불변 |
| 실시간 데이터 | ⛔ 금지 | 즉시 stale됨 |

---

## 5. Hydration Mismatch 주의사항

Suspense 패턴으로 리팩토링할 때 흔히 마주치는 함정이 있습니다.

### 문제: Date.now()로 인한 Hydration 불일치

```tsx
// ❌ Hydration Mismatch 발생
function EditorContent({ fileKey }) {
  const [sessionTimestamp] = useState(() => Date.now());
  const editorId = `editor-${fileKey}-${sessionTimestamp}`;
  // 서버: editor-abc-1736661234567
  // 클라이언트: editor-abc-1736661234890 (다른 값!)
}
```

서버에서 렌더링된 HTML과 클라이언트에서 hydration 시 생성된 값이 달라 React 경고가 발생합니다.

### 해결: React useId() 사용

```tsx
// ✅ SSR-safe한 ID 생성
function EditorContent({ fileKey }) {
  const reactId = useId();  // 서버/클라이언트 동일한 값
  const editorId = `editor-${fileKey}-${reactId}`;
}
```

`useId()`는 React 18에서 도입된 Hook으로, SSR 환경에서도 서버/클라이언트 간 일관된 ID를 보장합니다.

### 주의: useSuspenseQuery는 enabled 옵션을 무시함

`useSuspenseQuery`는 `useQuery`와 달리 `enabled` 옵션을 **무시**합니다. 항상 즉시 실행됩니다.

```tsx
// useQuery - enabled가 false면 실행 안 됨
const { data } = useQuery({
  queryFn: () => fetchData(id),
  enabled: Boolean(id),  // id가 없으면 skip
});

// useSuspenseQuery - enabled 무시, 항상 실행!
const { data } = useSuspenseQuery({
  queryFn: () => fetchData(id),
  enabled: Boolean(id),  // ⚠️ 이 옵션은 무시됨!
});
```

**해결책**: Hook 내부에서 명시적으로 검증

```tsx
export function useEditorConfigSuspense(fileKey: string) {
  if (!fileKey) {
    throw new Error('fileKey is required for useEditorConfigSuspense');
  }
  return useSuspenseQuery(editorConfigQueryOptions(fileKey));
}
```

이렇게 하면 개발 단계에서 빈 값 전달을 바로 발견할 수 있습니다.

### SSR 환경에서 피해야 할 패턴

| 패턴 | 문제 | 해결책 |
|------|------|--------|
| `Date.now()` | 시간 차이 | `useId()` 또는 고정값 |
| `Math.random()` | 랜덤값 차이 | `useId()` 또는 서버에서 생성 |
| `typeof window` 체크 | 조건부 렌더링 불일치 | `useEffect`로 클라이언트 전용 처리 |
| `localStorage` 접근 | 서버에 없음 | `useEffect` 내에서만 접근 |

---

## 7. 권장 리팩토링: 에디터 페이지 Suspense 전환

현재 에디터 페이지는 명령형 패턴을 사용합니다:

```tsx
// 현재: 명령형 상태 처리
const { data: editorConfig, isLoading, error } = useEditorConfig(fileKey);

if (!isValidFileKey || error) {
  return <ErrorState />;
}

if (isLoading || !editorConfig) {
  return <LoadingState />;
}

return <DocumentEditor config={editorConfig.config} />;
```

이를 선언적 Suspense 패턴으로 개선할 수 있습니다:

```tsx
// 개선: 선언적 Suspense 패턴
export default function EditorPage() {
  const { fileKey } = useParams();
  
  if (!isValidUUID(fileKey)) {
    return <ErrorState />;
  }

  return (
    <ErrorBoundary fallback={<ErrorState />}>
      <Suspense fallback={<LoadingState />}>
        <EditorContent fileKey={fileKey} />
      </Suspense>
    </ErrorBoundary>
  );
}

function EditorContent({ fileKey }) {
  // useSuspenseQuery 사용 - data 항상 존재 보장
  const { data: editorConfig } = useEditorConfigSuspense(fileKey);
  return <DocumentEditor config={editorConfig.config} />;
}
```

**장점**:
- 문서 목록 페이지와 일관된 패턴
- 컴포넌트가 "성공 상태"에만 집중
- 로딩/에러 처리의 관심사 분리

---

## 8. 결론

### use Hook 적용 가이드

```
┌─────────────────────────────────────────────────────────────┐
│                      use Hook 적용 기준                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ✅ 사용하면 좋은 경우                                         │
│     - 조건부로 Context를 읽어야 할 때                          │
│     - Server Component에서 Client로 Promise 전달              │
│     - 단순 일회성 데이터 로딩                                  │
│                                                               │
│  ⛔ 사용하지 않는 것이 좋은 경우                               │
│     - TanStack Query가 이미 잘 구축된 프로젝트                 │
│     - 캐싱, 재검증, 낙관적 업데이트가 필요한 경우              │
│     - 복잡한 서버 상태 관리                                    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 핵심 교훈

1. **새 기능이 항상 최선은 아니다**: 기존 도구(TanStack Query)가 잘 작동하면 굳이 바꿀 필요 없음
2. **데이터 특성을 파악하라**: 시간 민감성, 버전 의존성에 따라 패턴이 달라져야 함
3. **일관성이 중요하다**: 프로젝트 내 동일한 패턴 유지 (Suspense 또는 명령형)

---

**더 읽어보기:**
- [React 19: use Hook Reference](https://react.dev/reference/react/use)
- [TanStack Query: SSR Guide](https://tanstack.com/query/latest/docs/framework/react/guides/ssr)
- [ONLYOFFICE Document Server: Document Key](https://api.onlyoffice.com/editors/config/document)
