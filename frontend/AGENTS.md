# Frontend - Next.js 16 + React 19 (App Router)

## Purpose & Stack
- Next.js 16 + React 19 + TypeScript 5 application with App Router
- Embeds `@onlyoffice/document-editor-react` for document editing
- TanStack Query for server state management, TanStack Table for data tables
- shadcn/ui + Tailwind CSS for styling
- Axios handles API calls to Spring Boot backend

## Run, Build, Test
```bash
cd frontend
pnpm install            # once
pnpm dev                # http://localhost:3000
pnpm build && pnpm start
pnpm lint               # eslint rules
```
Next.js rewrites `/api` to `http://localhost:8080` via `next.config.ts`; adjust when backend host changes.

## Key Files
- `src/app/layout.tsx` - Root layout with QueryClientProvider
- `src/app/page.tsx` - Document list entry (Server Component, renders DocumentsPage)
- `src/app/editor/[fileKey]/page.tsx` - Editor page (/editor/[fileKey])
- `src/components/documents/` - Document list components (Streaming SSR)
  - `documents-page.tsx` - Main client component with Suspense boundary
  - `document-list.tsx` - Uses useSuspenseQuery for data fetching + toggleAll logic
  - `document-table.tsx` - TanStack Table with sorting
  - `table-skeleton.tsx` - Suspense fallback skeleton UI
  - `documents-error-boundary.tsx` - Error boundary with retry
- `src/components/providers/query-provider.tsx` - TanStack Query setup
- `src/components/ui/` - shadcn/ui components
- `src/hooks/use-documents.ts` - `useDocuments()` and `useDocumentsSuspense()` hooks
- `src/lib/utils.ts` - Utility functions (cn helper)

## Data Flow
1. User opens `http://localhost:3000` to see document list
2. **Streaming SSR**: Header renders immediately, table shows skeleton
3. `useSuspenseQuery` fetches `GET /api/documents` on server, streams result
4. User clicks document -> navigates to `/editor/[fileKey]`
5. Editor page fetches config via `GET /api/documents/{fileKey}/config`
6. ONLYOFFICE DocumentEditor renders with JWT-signed config
7. Document Server handles editing and callbacks

## Routing Structure
- `/` - Document list with upload, delete functionality
- `/editor/[fileKey]` - ONLYOFFICE editor for document with given fileKey

## Coding Guidelines
- Use App Router conventions: `page.tsx`, `layout.tsx`, `loading.tsx`, `error.tsx`
- Client components must have `'use client'` directive at top
- Server components are default; prefer them for shell/layout
- ONLYOFFICE editor must be client component (uses browser APIs)
- **Data fetching pattern (Streaming SSR)**:
  - Use `useSuspenseQuery` inside Suspense boundary for streaming
  - Wrap with `<Suspense fallback={<Skeleton />}>` for progressive loading
  - Use `QueryErrorResetBoundary` + `ErrorBoundary` for error handling
  - Avoid `await prefetchQuery` if you want immediate shell rendering
- Props interfaces should be explicit TypeScript types

## Testing Expectations
- Run `pnpm lint` before commits
- Add tests under `src/**/*.test.tsx` using Vitest + React Testing Library
- Mock API responses for component tests

## Troubleshooting
- **API calls fail**: Check `next.config.ts` rewrites and backend availability
- **Editor not loading**: Ensure component is client-side (`'use client'`); check JWT config
- **Hydration errors**: Verify client/server component boundaries are correct
