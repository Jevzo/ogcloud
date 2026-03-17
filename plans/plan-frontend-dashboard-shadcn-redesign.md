# Agent Identity (Read First)
Use this identity before implementation:

You are a frontend engineering agent working on **OgCloud**, a Kubernetes-native Minecraft cloud platform dashboard.

## Stack
- React-TS + TypeScript (strict mode, no `any`)
- Tailwind CSS (utility-first, no custom CSS unless unavoidable)
- Zustand (global state)
- Zod (schema validation)
- React Hook Form (forms)

## Your Responsibilities
- Build and maintain the OgCloud web dashboard
- Components must be composable, typed, and accessible (WCAG AA)
- State must be predictable - use Zustand stores with clear slices
- All API responses must be validated with Zod schemas before use
- Forms must use React Hook Form + Zod resolvers

## Code Rules
- No `any` - ever
- No prop drilling beyond 2 levels - lift to Zustand
- No inline styles - Tailwind only
- All components must have explicit prop interfaces
- Handle loading, error, and empty states in every data-fetching component

## UI/UX Principles
- Dashboard-first design: dense, scannable, data-rich
- Real-time feel: use optimistic updates and websocket-ready state
- Minecraft-domain awareness: servers, proxies, players, templates are core entities
- Use consistent design tokens via Tailwind config

## Workflow
1. Understand the feature and which entities it touches (server, proxy, template, player)
2. Define Zod schemas for any new API contracts
3. Build the Zustand store slice if new state is needed
4. Implement components bottom-up (atom -> molecule -> organism)
5. Wire to API and validate all responses
6. Ensure accessibility: keyboard nav, aria labels, focus management

## Patterns to Follow
- Co-locate component, types, and hooks in feature folders
- Separate API layer (`/api`) from UI components
- Use `useQuery`-style custom hooks for data fetching

If something is unclear about a design or API contract - ASK before implementing.

# Plan: Shadcn Dashboard Redesign for `frontend/dashboard`

Date: 2026-03-17
Target: replace the current custom dashboard shell and homegrown UI primitives in `frontend/dashboard` with a shadcn-based dashboard styled after the official March 2026 dashboard blocks/examples, while preserving current route coverage, API behavior, and OgCloud-specific information density
Scope: `frontend/dashboard` only - theme, shell, route pages, feature components, form handling, API validation, build/tooling, and cleanup of custom UI primitives. No backend or API contract changes unless a contract mismatch blocks Zod validation, in which case implementation must stop and ask.
Context: the current dashboard already runs on Vite 7 + React 19 + Tailwind v4 + Zustand, but it still relies on custom Tailwind primitives (`AppSelect`, `AppCreatableSelect`, `AppNumberInput`, `AppToasts`, etc.), ad hoc page-local form state, and unvalidated Axios response typing. March 2026 official shadcn guidance now supports Vite, Tailwind v4, React 19, CSS-variable theming, Radix/Base UI block variants, and ready-made dashboard/sidebar/login blocks. The current worktree is dirty, including active changes inside `frontend/dashboard`, so redesign work must not revert unrelated edits.

Execution model: this plan is intentionally split into medium-sized phases so a separate LLM agent can be assigned one phase at a time. Each phase should leave the dashboard in a working state, with lint/build passing for the touched surface before moving on.

## 1. Locked Decisions
1. This plan applies only to `frontend/dashboard`.
2. The redesign target is the official shadcn dashboard style as of March 2026, with `dashboard-01` and sidebar block patterns used as the main composition reference.
3. The implementation must adapt shadcn blocks to React Router + Vite. Do not copy Next.js `app/...` file structure literally into this repo.
4. The resulting UI must stay dense, scannable, and operationally focused. This is not a marketing-site redesign.
5. Flaky custom UI primitives must be retired wherever a first-party shadcn primitive or block pattern exists.
6. Use bun for setup and component generation from inside `frontend/dashboard`.
7. Initialize shadcn in-place for the existing Vite app, then add components with `bunx --bun shadcn@latest add <component-or-block>`.
8. If the CLI asks for library/style choices, prefer `Radix UI` + `new-york` + CSS variables + `lucide` unless the repo already commits to a different shadcn base at implementation time.
9. Adopt shadcn CSS-variable theming and move the dashboard away from the current bright cyan toward a slightly darker blue.
10. All API response entry points touched by the redesign must be validated with Zod before data reaches the UI or stores.
11. All rewritten forms must use React Hook Form + Zod resolver.
12. Zustand remains only for shared cross-route or global state such as auth, network settings, and shell-level UI state. Do not introduce new deep prop chains.
13. Existing routes and admin workflows must remain intact during the migration.
14. If a required frontend workflow is unclear, or an API payload shape does not match the current TypeScript assumptions, implementation must stop and ask.
15. Because the worktree already contains user changes, the agent must integrate around them and never revert unrelated edits.

## 2. March 2026 Research Snapshot
1. Official Vite installation now supports initializing shadcn directly in a Vite project, including Tailwind v4 and React 19 support.
2. Official Tailwind v4 guidance confirms the current stack is compatible with modern shadcn output, including `@theme` and `@theme inline`.
3. Official theming guidance recommends CSS variables, which fits the requested darker-blue palette change and sharply reduces the need for custom global CSS.
4. Official component guidance relevant to this migration includes `sidebar`, `breadcrumb`, `command`, `combobox`, `field`, `form`, `dialog`, `sheet`, `dropdown-menu`, `badge`, `card`, `table`, `sonner`, `chart`, `tabs`, `switch`, `checkbox`, and `tooltip`.
5. Official data-table guidance is intentionally compositional rather than monolithic. It combines shadcn `table` primitives with `@tanstack/react-table`.
6. Official form guidance uses React Hook Form and Zod together via `zodResolver`.
7. Official toast guidance deprecates the old toast component in favor of `sonner`.
8. Official blocks now include dashboard and sidebar patterns, and as of February 2026 they are available for both Radix UI and Base UI variants through the same CLI workflow.
9. The official blocks page currently exposes `dashboard-01`, `sidebar-07`, and `login-01` as directly installable references that fit this migration well, but their generated file targets assume file-based app routing and must be adapted to this repo's React Router layout.

## 3. Confirmed Current State in `frontend/dashboard`
1. `package.json` includes `react`, `react-dom`, `react-router`, `axios`, `zustand`, `motion`, and `react-icons`, but does not include `zod`, `react-hook-form`, `@hookform/resolvers`, `@tanstack/react-table`, or any shadcn-generated files.
2. The app already has a valid Vite alias setup for `@/*` in both `vite.config.ts` and `tsconfig.app.json`, which means shadcn alias generation should fit this repo cleanly.
3. There is currently no `src/components/ui` directory.
4. `src/index.css` defines a cyan-heavy custom theme and a large set of hand-rolled `.app-*` utility classes that have become a parallel design system.
5. `src/main.tsx` routes the following authenticated dashboard areas:
- Dashboard home
- Servers
- Server details
- Groups
- Group details
- Players
- Inbox
- Network with nested `overview`, `server-settings`, `general`, and `messaging`
- Permissions
- Permission group details
- Templates
- Web users
- Settings
6. `src/components/DashboardLayout.tsx` is a custom sidebar/header/footer shell with hardcoded nav rendering, a custom health card, and custom action buttons.
7. `src/components/HeaderSearch.tsx` is a bespoke search surface that manually implements dropdown/search behavior and player management launching.
8. Shared custom UI primitives are spread across many pages:
- `AppSelect`
- `AppCreatableSelect`
- `AppNumberInput`
- `AppToasts`
- `TableRefreshButton`
- `DetailStatCard`
- `ServerActionButtons`
- custom modal shells such as deploy/command/player-management
9. `GroupFormFields.tsx`, `PermissionGroupFormFields.tsx`, and multiple page-level edit/create flows still use page-local controlled state rather than React Hook Form.
10. The API layer uses Axios generics and normalization helpers, but not runtime validation.
11. The repo already has active user changes in dashboard files such as `GroupsPage.tsx`, `NetworkPage.tsx`, `ServerDetailsPage.tsx`, `ServersPage.tsx`, and related API/type files. The migration must preserve that work.

## 4. Target Visual Direction
1. Use the official dashboard block language: inset sidebar shell, compact header, breadcrumb framing, dense cards, data tables, and compact secondary actions.
2. Preserve OgCloud identity through copy, metrics, and Minecraft-domain objects rather than through bespoke widget chrome.
3. Replace the current neon cyan emphasis with a darker blue that still reads as active and technical.
4. Recommended primary direction for implementation:
- primary base around `#1d4ed8`
- hover/active around `#1e40af`
- ring/focus states derived from the same blue family
- success/warning/error remain status-specific and should not be remapped into blue
5. Keep slate/graphite neutrals and avoid introducing purple or soft consumer-app styling.
6. Keep motion minimal and intentional. If `motion` remains, use it only where it supports perceived responsiveness rather than decorative page choreography.
7. Mobile support must remain real, not incidental. The new sidebar must degrade to a `Sheet`/mobile drawer pattern instead of becoming unusable.

## 5. Architecture and Foldering Target
1. Add shadcn-generated primitives under `src/components/ui`.
2. Keep route entry files in `src/pages` during the migration, but move rewritten page internals into feature folders so the route files become thin composition layers.
3. Recommended feature structure:
- `src/features/dashboard/...`
- `src/features/servers/...`
- `src/features/groups/...`
- `src/features/players/...`
- `src/features/network/...`
- `src/features/permissions/...`
- `src/features/templates/...`
- `src/features/web-users/...`
- `src/features/auth/...`
4. Co-locate each feature's components, Zod schemas, types, and query-style hooks.
5. Keep API modules under `src/lib/api`, but pair them with schema parsing helpers or feature-level schema files.
6. Add a shared `src/lib/utils.ts` if shadcn initialization does not already create one.
7. Limit global CSS to tokens, resets, and truly global behaviors. Page and component styling should move to Tailwind utilities and shadcn variants.

## 6. Component Replacement Plan
### 6.1 Shell and navigation
1. Replace the current `DashboardLayout` implementation with a shadcn sidebar shell built from `sidebar`, `breadcrumb`, `separator`, `avatar`, `dropdown-menu`, `tooltip`, `badge`, and `button`.
2. Use a sidebar variant close to `dashboard-01` or `sidebar-07` so desktop can collapse to icons and mobile can open as a drawer.
3. Keep the cluster health surface, but present it as a compact sidebar/footer card or top-bar status cluster instead of a bespoke panel.
4. Migrate icons from `react-icons` to `lucide-react` as pages are rewritten. Remove `react-icons` only after the last usage is gone.
5. Treat block-generated sidebar components as a reference implementation, not as immutable output. Rename, split, and relocate them to fit the current router-based app shell.

### 6.2 Search and quick actions
1. Replace the manual `HeaderSearch` dropdown with shadcn `command` or `combobox` patterns.
2. Keep cross-entity search for servers, groups, and players.
3. Retain direct player-management launch behavior, but open the management surface through shadcn `dialog` or `sheet`.
4. Add keyboard-first behavior and clear empty/loading/error states.

### 6.3 Feedback and transient state
1. Replace `AppToasts` with `sonner`.
2. Mount a single root `Toaster` near the app root.
3. Replace page-local toast array rendering with direct `toast.success`, `toast.error`, and `toast.promise` usage where appropriate.
4. Replace bespoke refresh buttons with shadcn `button` variants plus spinner/loading affordances.

### 6.4 Forms
1. Replace `AppSelect` with shadcn `select` when the option set is small and non-searchable.
2. Replace `AppCreatableSelect` with shadcn `combobox` or a `popover` + `command` composition that supports explicit create actions.
3. Replace `AppNumberInput` with shadcn `input` plus RHF parsing/validation and helper text.
4. Replace `FieldHintLabel` and custom label glue with `field`, `field-label`, `field-description`, and `field-error`.
5. Convert all rewritten create/edit forms to RHF + Zod before shipping the redesign.

### 6.5 Tables and dense data surfaces
1. Replace hand-built tables with a consistent shadcn table/data-table pattern.
2. Use `@tanstack/react-table` where sorting, column visibility, row actions, or reusable pagination justify it.
3. Keep simpler read-only tables on plain shadcn `table` primitives when a full data-table abstraction would add noise.
4. Standardize row actions through `dropdown-menu` or a consistent button cluster.
5. Use `skeleton` states instead of bare text placeholders where the user is waiting on data.

### 6.6 Dialogs and overlays
1. Replace custom modal shells with shadcn `dialog`, `alert-dialog`, and `sheet`.
2. Use `alert-dialog` for destructive confirmation flows.
3. Prefer `sheet` on mobile for tall or form-heavy overlays.
4. Ensure focus trapping, close affordances, and keyboard escape behavior are consistent.

## 7. Page-by-Page Migration Plan
### 7.1 App shell
1. `src/components/AppShell.tsx`
- keep it minimal
- add the root `Toaster`
- keep suspense behavior
2. `src/components/DashboardLayout.tsx`
- replace the entire shell with a shadcn sidebar/header implementation
- preserve auth logout, role gating, linked Minecraft avatar, and cluster health status
- convert the nav definition into a typed config that can feed sidebar groups and breadcrumbs

### 7.2 Dashboard home
1. Rebuild the metrics area with shadcn `card`, `badge`, `progress`, and optional `chart`.
2. Rebuild scaling actions with a reusable table/data-table surface.
3. Reframe group cards into denser cards with runtime/status badges and lighter image dependence.
4. Add real `skeleton`, `empty`, and error handling surfaces rather than `"--"` placeholders.

### 7.3 Servers and server details
1. `ServersPage`
- convert filters to shadcn toolbar controls
- replace the table with a data-table pattern
- move row actions into a consistent action menu or compact inline action group
- migrate deploy and execute-command flows to shadcn dialogs
2. `ServerDetailsPage`
- refactor hero stats and action controls into cards, badges, tabs, and dialogs
- standardize logs/metrics/history tables if present

### 7.4 Groups and group details
1. Replace list/table surfaces with shadcn table or data-table components.
2. Rewrite create/edit forms with RHF + Zod, including scaling/resource fields.
3. Use `field` groupings to visually separate identity, runtime, scaling, and resources.
4. Keep runtime-profile-specific conditional logic, but render it through consistent field primitives.

### 7.5 Players
1. Replace manual table and modals with shadcn table/data-table plus dialog/sheet management.
2. Rebuild `PlayerManagementModal` using cards, field groups, tabs if needed, and consistent action buttons.
3. Keep online/offline, permission, and identity data dense and immediately visible.

### 7.6 Network area
1. `NetworkPage`
- replace the custom section chooser with shadcn tabs, sidebar subnav, or segmented card navigation while keeping nested routing
- keep the nested route model
2. `NetworkOverviewPage`
- use cards, alerts, chart, and status badges for live network state
3. `NetworkServerSettingsPage`
- convert all controls to RHF + Zod
- use switches, selects, field groups, and alert-dialog for restart/maintenance operations
4. `NetworkGeneralPage`
- convert routing/general settings to validated shadcn form controls
5. `NetworkMessagingPage`
- use textareas, preview cards, and grouped fields for MOTD and player-facing messaging

### 7.7 Permissions
1. Rewrite permission-group list/detail surfaces with consistent table and form primitives.
2. Convert permission group edit forms to RHF + Zod.
3. Use shadcn badges and inline metadata to make inherited/default status easier to scan.

### 7.8 Templates
1. Replace custom selects and creatable template/version flows with shadcn `select` and `combobox`.
2. Rebuild list and edit surfaces with table/data-table, cards, and dialogs where appropriate.
3. Keep template versioning and metadata dense, not card-heavy for its own sake.

### 7.9 Web users, inbox, settings, login, not found
1. `WebUsersPage`
- convert role/status filters and edit actions to shadcn controls
- move user edit/create flows to RHF + Zod dialogs
2. `InboxPage`
- use card/table layout with clearer empty states
3. `SettingsPage`
- convert account/profile forms to RHF + Zod
4. `LoginPage`
- replace the custom form styling with a shadcn login pattern inspired by the March 2026 login blocks
5. `NotFound.tsx`
- align with the new shell style and button variants

## 8. Shared Data and Validation Plan
1. Introduce Zod schemas for every API response shape touched by the redesign:
- auth/session
- dashboard overview
- servers
- groups
- players
- search
- templates
- permissions
- network settings/status/locks
- web users
- audit/inbox items if surfaced
2. Parse server responses at the API boundary in `src/lib/api/*` before data is returned to pages or hooks.
3. Keep normalization logic only after successful parsing, not instead of parsing.
4. Add query-style hooks per feature for fetch state encapsulation. Example direction:
- `useDashboardOverviewQuery`
- `useServersQuery`
- `useServerDetailsQuery`
- `useGroupsQuery`
- `useNetworkOverviewQuery`
5. Keep auth/session refresh inside the existing store unless the redesign reveals a clear reason to refactor it.
6. Do not add a second global state mechanism for fetch state unless there is a concrete need.

## 9. Phased Implementation Plan
### Phase 1. Foundation, theme, and shell
Goal: establish the shadcn base, darker-blue theme, root toaster, and router-compatible shell so later phases can focus on features instead of infrastructure.

1. Start in `frontend/dashboard` and inspect the dirty worktree before changing anything.
2. Read `FRONTEND.md` and adopt the frontend identity above.
3. Run shadcn initialization in-place for Vite using bun.
4. Install supporting libraries that are not already present:
- `zod`
- `react-hook-form`
- `@hookform/resolvers`
- `@tanstack/react-table`
5. Add the base shadcn primitives needed for the shell first:
- `sidebar`
- `breadcrumb`
- `button`
- `separator`
- `avatar`
- `badge`
- `tooltip`
- `dropdown-menu`
- `sheet`
- `command`
- `input`
- `sonner`
- `skeleton`
- `card`
6. Add the high-value block references next so the new shell and login flow start from official compositions instead of custom redesign work:
- `dashboard-01`
- `sidebar-07`
- `login-01`
7. Extract only the reusable shell/login pieces from those blocks and adapt them to `src/components` and `src/pages`. Do not keep generated file-based route stubs that do not fit React Router.
8. Add shared form/data primitives that later phases will need:
- `field`
- `form`
- `select`
- `combobox`
- `textarea`
- `switch`
- `checkbox`
- `radio-group` if needed
- `dialog`
- `alert-dialog`
- `table`
- `tabs`
- `pagination`
- `chart`
9. Generate `components.json` and keep CSS variables enabled.
10. Refactor `src/index.css` to shadcn-compatible theme tokens and remove as many `.app-*` classes as possible without breaking untouched routes.
11. Build the new `AppShell` and `DashboardLayout` with sidebar, header, breadcrumb framing, mobile drawer behavior, avatar/account actions, and root `Toaster`.
12. Keep routing intact for all existing pages, even if some routes still render legacy page bodies during this phase.

Phase deliverables:
- shadcn initialized in `frontend/dashboard`
- darker-blue theme tokens in place
- root toaster mounted
- new sidebar/header shell active
- project still builds with mixed legacy/new route bodies

Phase stop conditions:
- stop and ask if shadcn CLI output conflicts with the existing router/app structure in a way that cannot be cleanly adapted
- stop and ask if the theme/token migration breaks too many untouched pages to safely proceed incrementally

### Phase 2. Dashboard home and shared search/query layer
Goal: replace the top-level operational landing experience and establish the validated fetch/query pattern that later phases should copy.

1. Add Zod parsing for dashboard overview and search payloads.
2. Create feature-local schema and query-style hooks for dashboard home and global search.
3. Rebuild `DashboardHome.tsx` with shadcn cards, badges, progress, optional charts, skeletons, and a reusable scaling-actions table surface.
4. Replace the current thumbnail-heavy group cards with denser operational cards.
5. Rebuild `HeaderSearch.tsx` using shadcn `command` or `combobox`, preserving cross-entity search and player-management launch behavior.
6. Replace `AppToasts` usage in the touched surfaces with `sonner`.
7. Keep all loading, empty, and error states explicit.

Phase deliverables:
- redesigned dashboard home
- redesigned global header search
- first Zod-validated dashboard/search API boundary
- first reusable query-style hooks for the frontend

Phase stop conditions:
- stop and ask if search results or dashboard payloads differ from the current TypeScript assumptions
- stop and ask if player-management launch behavior needs UX decisions beyond what the current app already implies

### Phase 3. Servers and server details
Goal: migrate the most operationally critical runtime-management screens to shadcn tables, dialogs, and validated data flows.

1. Add Zod parsing for server list/detail and command/deploy-related payloads touched by these pages.
2. Rebuild `ServersPage.tsx` with a shadcn toolbar, filters, table/data-table, consistent row actions, and `sonner` feedback.
3. Migrate deploy and execute-command overlays to shadcn `dialog` or `sheet`.
4. Rebuild `ServerDetailsPage.tsx` with cards, badges, tabs, action menus, and consistent telemetry/status presentation.
5. Replace touched custom primitives such as `TableRefreshButton`, `ServerActionButtons`, and `ExecuteCommandModal` where shadcn composition is the better fit.

Phase deliverables:
- redesigned server list page
- redesigned server details page
- deploy/command flows on shadcn dialogs
- validated server API boundary for touched routes

Phase stop conditions:
- stop and ask if any command/deploy workflow depends on undocumented backend semantics
- stop and ask if server details expose payload shapes not represented in the current frontend types

### Phase 4. Groups and permissions forms
Goal: convert the most complex configuration forms to the intended RHF + Zod pattern and remove the most brittle custom form primitives.

1. Add Zod parsing for group and permission-group payloads touched in this phase.
2. Rebuild `GroupsPage.tsx` and `GroupDetailsPage.tsx` with shadcn list/table surfaces and RHF + Zod forms.
3. Replace `GroupFormFields.tsx` with field-based form sections for identity, runtime, scaling, and resources.
4. Rebuild `PermissionsPage.tsx` and `PermissionGroupDetailsPage.tsx` with shadcn tables/forms and clear badges for defaults/inheritance.
5. Remove touched usages of `AppSelect`, `AppNumberInput`, and related field glue from these surfaces.

Phase deliverables:
- redesigned groups list/detail
- redesigned permissions list/detail
- RHF + Zod form pattern established for configuration-heavy pages

Phase stop conditions:
- stop and ask if any group/runtime/permission contract is ambiguous
- stop and ask if runtime-profile behavior needs product decisions beyond the current UI logic

### Phase 5. Network management subtree
Goal: migrate the nested network area in one controlled pass so its subnavigation, forms, and live status surfaces remain coherent.

1. Preserve the nested routing model under `NetworkPage`.
2. Add Zod parsing for network settings, status, locks, and any nested route payloads touched here.
3. Rebuild `NetworkPage.tsx` with shadcn subnavigation, tabs, or segmented navigation while preserving active-route clarity.
4. Rebuild `NetworkOverviewPage.tsx` with cards, alerts, charts, and live-state badges.
5. Rebuild `NetworkServerSettingsPage.tsx`, `NetworkGeneralPage.tsx`, and `NetworkMessagingPage.tsx` with RHF + Zod, switches, selects, textareas, and `alert-dialog` for high-impact operations.
6. Preserve current user edits in the network area and integrate around them instead of overwriting them.

Phase deliverables:
- redesigned network shell and all nested network subpages
- validated network API boundary
- RHF + Zod across network management forms

Phase stop conditions:
- stop and ask if current in-flight network edits conflict with the redesign direction
- stop and ask if maintenance/restart/runtime-refresh UX needs confirmation

### Phase 6. Players, templates, and web users
Goal: migrate the remaining operational management surfaces that still depend heavily on custom selects, dialogs, and table controls.

1. Add Zod parsing for players, templates, and web-user payloads touched in this phase.
2. Rebuild `PlayersPage.tsx` and `PlayerManagementModal` with shadcn table/data-table plus dialog or sheet management.
3. Rebuild `TemplatesPage.tsx` with shadcn `select`, `combobox`, table/data-table, and edit/create dialogs.
4. Rebuild `WebUsersPage.tsx` with shadcn filters, tables, badges, and RHF + Zod edit flows.
5. Continue replacing custom feedback/actions with shared shadcn compositions.

Phase deliverables:
- redesigned players page and management surface
- redesigned templates page
- redesigned web users page
- more legacy custom primitives removed

Phase stop conditions:
- stop and ask if template creation/versioning behavior is underspecified
- stop and ask if player moderation/account-link flows need behavior changes rather than UI replacement

### Phase 7. Inbox, settings, auth surfaces, and cleanup
Goal: finish the lower-volume routes, remove obsolete custom UI code, and leave the dashboard in a coherent end state.

1. Rebuild `InboxPage.tsx`, `SettingsPage.tsx`, `LoginPage.tsx`, and `NotFound.tsx` using the established shadcn patterns.
2. Add Zod validation for any remaining touched API entry points in these routes.
3. Remove obsolete custom primitives only after all usages are gone:
- `AppSelect`
- `AppCreatableSelect`
- `AppNumberInput`
- `AppToasts`
- any replaced modal or button helper that no longer earns its existence
4. Remove `react-icons` only after the last usage is gone.
5. Reassess whether `motion` still earns its cost. Remove it only if the new design no longer uses it meaningfully.
6. Run final route-level cleanup so pages share the same spacing, cards, headers, forms, and feedback patterns.

Phase deliverables:
- redesigned auth/supporting routes
- legacy custom UI layer removed or minimized
- final dependency cleanup complete

Phase stop conditions:
- stop and ask if auth/login behavior appears to depend on flows not visible in the current frontend
- stop and ask before deleting any helper that still has mixed legacy/new usage

## 10. Validation
1. `bun run lint` passes in `frontend/dashboard`.
2. `bun run build` passes in `frontend/dashboard`.
3. All routes currently registered in `src/main.tsx` still render and navigate correctly.
4. Every data-fetching screen has explicit loading, error, and empty states.
5. Every rewritten form uses RHF + Zod and shows inline validation errors.
6. Search remains keyboard-usable and can open the related target or player-management surface.
7. Sidebar navigation works on desktop and mobile, including collapse/drawer behavior.
8. Dialogs, sheets, selects, and comboboxes are keyboard accessible and trap focus correctly.
9. Zod rejects malformed API responses with controlled user-facing failure states instead of undefined behavior.
10. The darker-blue palette is applied consistently across primary actions, active nav states, and focus rings.
11. No page still depends on `AppSelect`, `AppCreatableSelect`, `AppNumberInput`, or `AppToasts`.
12. No `any` types are introduced.

## 11. Phase-by-Phase Validation Rule
1. Each phase must end with `bun run lint` passing for `frontend/dashboard`.
2. Each phase must end with `bun run build` passing for `frontend/dashboard`.
3. Each phase must preserve working navigation for all already-migrated routes plus all untouched routes.
4. A later phase must not start if the previous phase leaves broken imports, dead routes, or partial component generation.
5. If a phase cannot be completed cleanly because of API ambiguity or conflicting local changes, stop and ask instead of rolling the ambiguity into the next phase.

## 12. Definition of Done
1. `frontend/dashboard` uses shadcn components and block patterns as its primary UI system.
2. The current custom/flaky form and feedback primitives are removed or reduced to thin wrappers around shadcn primitives.
3. The dashboard shell matches the improved official shadcn dashboard direction while staying recognizably OgCloud.
4. The primary color system is shifted to the requested darker-blue range.
5. Rewritten forms use React Hook Form + Zod.
6. Rewritten API entry points validate response payloads with Zod.
7. Route coverage and core admin workflows remain intact.
8. The dashboard builds and lints cleanly.

## 13. Research Sources
1. Vite installation: https://ui.shadcn.com/docs/installation/vite
2. Installation overview: https://ui.shadcn.com/docs/installation
3. Tailwind v4 support: https://ui.shadcn.com/docs/tailwind-v4
4. Theming and CSS variables: https://ui.shadcn.com/docs/theming
5. Sidebar component: https://ui.shadcn.com/docs/components/sidebar
6. Command component: https://ui.shadcn.com/docs/components/command
7. Combobox component: https://ui.shadcn.com/docs/components/combobox
8. Field component: https://ui.shadcn.com/docs/components/field
9. React Hook Form guide: https://ui.shadcn.com/docs/forms/react-hook-form
10. Data table guide: https://ui.shadcn.com/docs/components/data-table
11. Sonner component: https://ui.shadcn.com/docs/components/sonner
12. Blocks overview: https://ui.shadcn.com/blocks
13. Sidebar blocks: https://ui.shadcn.com/blocks/sidebar
14. Login blocks: https://ui.shadcn.com/blocks/login
15. February 2026 block update: https://ui.shadcn.com/docs/changelog/2026-02-blocks
16. Examples landing page: https://ui.shadcn.com/examples/dashboard
