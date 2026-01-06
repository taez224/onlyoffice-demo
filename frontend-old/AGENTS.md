# Frontend – React 18 + Vite

## Purpose & Stack
- Lightweight Vite + React 18 + TypeScript 5 client that embeds `@onlyoffice/document-editor-react` (v2.1.1) and fetches config from the Spring Boot backend.
- Axios handles `/api/config` calls; styling is minimal (CSS modules in `App.css`/`index.css`).

## Run, Build, Test
```bash
cd frontend
pnpm install            # once
pnpm dev                # http://localhost:5173
pnpm build && pnpm preview
pnpm lint               # eslint + typescript-eslint rules
```
Vite proxies `/api` to `http://localhost:8080` via `vite.config.ts`; adjust when backend host changes.

## Key Files
- `src/main.tsx` – boots React with `<App />`.
- `src/App.tsx` – reads `fileKey` from `window.location.search` and conditionally renders `<Editor />`.
- `src/components/Editor.tsx` – client wrapper around `DocumentEditor`; fetches config via Axios and renders the SDK component with lifecycle hooks.
- `src/assets/` – static assets (logos, etc.).
- `index.html`, `vite.config.ts` – Vite entry and proxy configuration.

## Data Flow
1. User opens `http://localhost:5173?fileKey=550e8400-e29b-41d4-a716-446655440000` (UUID).
2. `App.tsx` validates the query param (fileKey must be present) and displays an error if absent.
3. `Editor.tsx` issues `GET /api/config?fileKey={uuid}` (proxied to backend) and stores `{ documentServerUrl, config }`.
4. When loaded, `DocumentEditor` connects to the Document Server URL and renders the document UI.
5. Document Server uses the UUID fileKey to download and track changes via callbacks.

## Coding Guidelines
- Keep React components functional with hooks; TypeScript props should be explicit (`interface EditorProps { fileKey: string }`).
- Avoid storing secrets or backend URLs in the bundle; rely on the `/api` proxy or `import.meta.env` variables for overrides.
- Document Editor needs full viewport height; maintain inline styles or move to CSS with `height: 100vh`.
- Error and loading states must remain user-friendly (spinner + actionable message).

## Testing Expectations
- No formal UI tests exist yet; at minimum, run `pnpm lint` before commits.
- When adding logic (e.g., multi-file dashboards), introduce Vitest + React Testing Library specs under `src/**/*.test.tsx` and mock Axios responses.

## Troubleshooting
- **Config fetch fails**: check Vite proxy configuration and backend availability (`curl http://localhost:8080/api/config?fileKey=550e8400-e29b-41d4-a716-446655440000`). Use a valid UUID fileKey from upload response or migration endpoint.
- **Editor not loading**: ensure `DocumentEditor` receives both `documentServerUrl` and `config` fields; watch browser console for JWT errors.
- **CORS/proxy issues**: update `vite.config.ts` `server.proxy['/api']` to the correct backend host/port.
