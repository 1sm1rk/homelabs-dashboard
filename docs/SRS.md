# SRS — homelabs-dashboard (Software Requirements Specification)

Minimal SRS. Work packages reference REQ-IDs here for traceability. Keep it
lean — extend only when a requirement has a real consumer.

## Scope

One server-rendered web application: an authenticated module shell (layout,
navigation, Keycloak login) hosting one rendering package per backend service.
The first package presents the homelabs-projects API (projects, work packages,
tasks, comments, activity, attachments) with read views and htmx mutation
flows. No database — the dashboard is stateless.

## Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| REQ-1 | Module shell: rail navigation rendered from a module registry, per-module subnav, topbar with identity + logout, maximal working area; hand-written CSS (tokens, grid, dark/light via prefers-color-scheme) | shell renders strictly from the registry (no module-specific hacks); CSS ships without any framework or build step |
| REQ-2 | Authentication: OAuth2 Authorization Code + PKCE against the shared Keycloak realm (confidential client); `GET /health` open; all other routes behind the session; session access token forwarded to module REST APIs | unauthenticated request redirects to login; module calls carry a Bearer token with the dashboard audience |
| REQ-3 | Projects module read views: project list (lean summaries), project detail incl. work-package/task tree, comments, activity, attachments metadata + download proxy | list/detail render wire-faithful data (SNAKE_CASE mapping); unauthenticated access impossible |
| REQ-4 | Projects module mutations: create project, explicit transitions, inline status/assignee/due/sort_order PATCH, create work package/task, comment form — all htmx, errors rendered inline | each mutation updates the fragment in place; 400 field messages and 409 suggestion bodies render verbatim |
| REQ-5 | Operability: `GET /health` liveness; containerized fat-jar image; pipeline publishing the image on main merges | `/health` 200 unauthenticated; main merge publishes the ghcr image |

## Constraints

- Java 25, Spring Boot 4.1.x, Maven, `de.homelabs.dashboard`; records,
  constructor injection.
- Thymeleaf + htmx server-side only; plain hand-written CSS; no npm build,
  no CSS framework.
- Stateless: no database; sessions beyond the auth cookie are in-memory.
- Wire discipline: explicit `@JsonProperty` snake_case on every record
  crossing a REST boundary.

## Non-goals

- WYSIWYG editing (plain text first; the backend wraps text), attachment
  upload UI, additional modules (e.g. memory/search), per-module theming —
  usage-first additions later.