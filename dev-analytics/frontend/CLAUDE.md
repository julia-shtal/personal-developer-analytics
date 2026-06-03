# Frontend (React 18 + TypeScript)

Loaded only when working in `frontend/`. Root `CLAUDE.md` still applies when crossing the API boundary (changing a DTO, adding an endpoint).

## Run it

```bash
npm install
npm run dev       # Vite dev server on :5173, proxies /api to :8080
npm run build     # Outputs to ../src/main/resources/static/ (embedded in fat JAR)
npm run lint
```

## Non-negotiables

**Use the existing Axios instance from `lib/api.ts`.** Never call `fetch()` or instantiate raw `axios()` — both skip the JWT request interceptor and the 401 auto-refresh handler, which means silent auth failures and stale-token loops.

**Use the existing chart wrappers.** `MetricLineChart`, `MetricBarChart`, `MultiLineChart` handle responsive sizing, date-sorted x-axis, and the 7-color team palette. Reach for raw Recharts only when no wrapper fits — then add the new chart type *as a wrapper*, don't inline it.

**`clsx` for conditional classes**, not template literals. `clsx('btn', isLoading && 'opacity-50')` — not `` `btn ${isLoading ? 'opacity-50' : ''}` ``.

**Auth gates via `isManager` / `isAdmin` from `AuthContext`**, not `user.role === 'MANAGER'`. The convenience getters survive role-enum renames and keep the role check in one place.

**React Query: invalidate on mutation.** The recalculate mutation uses `queryKey: []` (invalidate all) because it touches many metric types. For narrower mutations, invalidate only the affected keys. Default `staleTime` is 2 min, `retry: 1` — don't change these globally without a reason.

**Login clears the React Query cache.** `AuthContext.login()` clears the cache before setting tokens — otherwise the previous user's data is visible for up to `staleTime`. Preserve this clear in any new auth flow (e.g. if SSO is added later).

**Tailwind tokens only.** No hardcoded hex values, no inline `style={{ color: '#...' }}`. New colors go in `tailwind.config.js`.

**Three states required on every fetching component.** Loading, error, empty — each rendered explicitly. Reference: `AiSummaryCard`. Use `Spinner` / `PageSpinner` for loading.

**`forwardRef` for form primitives.** `Input` and `Select` already follow this pattern for form-library compatibility. New input primitives match.

**New page routes need both sides.** Add the route in React Router *and* to the SPA fallback list in `SpaFallbackController` on the backend. Without the backend entry, deep links return 404 instead of forwarding to `index.html`.

**Protect auth-required routes** with the existing `ProtectedRoute` wrapper. Manager/admin pages additionally check `isManager` / `isAdmin` inside the page component.

## When adding a metric widget

Pair with the backend `add-metric` skill. Frontend half:

1. Add typed `fetch<Slug>Metric(query)` to `api/metrics.ts` (TS interfaces mirror backend DTOs — no codegen, keep manually in sync).
2. Use `MetricLineChart` / `MetricBarChart` if the metric fits; otherwise add a wrapper first.
3. Register the widget in `DashboardPage` (or the relevant page) inside the existing grid layout.
4. The widget reads from React Query with the global defaults — no per-query overrides unless justified.

## What's NOT in this file

- Generic React / TypeScript / Tailwind / Recharts patterns — Claude has them.
- Routing config — lives in `App.tsx`.
- Build setup — `vite.config.ts`, `tailwind.config.js`.
