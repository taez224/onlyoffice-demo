# Frontend - Next.js 16 + React 19

## Quick Start

```bash
cd frontend
pnpm install
pnpm dev  # Runs on port 3000
```

## Tech Stack

- **Next.js 16** - React framework with App Router
- **React 19** - UI library
- **TypeScript 5** - Type safety
- **Turbopack** - Fast bundler (built-in)
- **TanStack Query** - Server state management
- **TanStack Table** - Table UI
- **shadcn/ui** - UI components (Tailwind CSS)

## Key Files

- `app/page.tsx` - Document list page
- `app/editor/[id]/page.tsx` - ONLYOFFICE editor page
- `app/layout.tsx` - Root layout with QueryClient Provider
- `components/Editor.tsx` - ONLYOFFICE editor component (Client Component)

## Important: Client Components

ONLYOFFICE Document Editor **must** be a Client Component:

```tsx
'use client'  // Required!

import { DocumentEditor } from '@onlyoffice/document-editor-react'
```

This is because ONLYOFFICE requires browser APIs that aren't available in Server Components.

## Project Structure

```
app/
├── layout.tsx              # Root layout + QueryClient
├── page.tsx                # Document list (Server Component)
└── editor/
    └── [id]/
        └── page.tsx        # Editor page (uses Client Component)

components/
├── Editor.tsx              # ONLYOFFICE wrapper (Client Component)
├── DocumentTable.tsx       # TanStack Table
└── UploadButton.tsx        # File upload

hooks/
├── useDocuments.ts         # Fetch document list
├── useUploadDocument.ts    # Upload mutation
└── useDeleteDocument.ts    # Delete mutation

lib/
└── api.ts                  # API client functions
```

## Commands

```bash
pnpm dev      # Development server (port 3000)
pnpm build    # Production build
pnpm start    # Start production server
pnpm lint     # ESLint check
```

## API Integration

Backend proxy is handled by Next.js config or environment variables:

```typescript
// lib/api.ts
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
```

## Common Tasks

**Add new route**: Create file in `app/` directory (file-based routing)

**Add Client Component**: Add `'use client'` directive at top of file

**Fetch data**: Use TanStack Query hooks or Server Components with `fetch()`