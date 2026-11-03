# homelabs-dashboard

The web frontend of the homelab services: one server-rendered Spring Boot
surface (Thymeleaf fragments + htmx, hand-written CSS — no frontend build
chain) with a module shell — navigation, authentication, layout — and one
rendering package per backend module. First module: homelabs-projects, the
project-management backend. The dashboard renders everything; the backends
stay pure REST/JSON APIs.

Authentication rides the shared Keycloak realm (OAuth2 Authorization Code +
PKCE). Module data is fetched server-side, so the browser only ever talks to
the dashboard.

## How to run / verify

Requires JDK 25 and Maven.

- Run: `mvn spring-boot:run` — dev server on port 8094
- Verify: `mvn clean verify` (unit tests, boot smoke test, shell rendering,
  projects read and mutation flows, sign-out and session-cookie integration tests; the
  module ITs run a fake JDK-HttpServer projects API — no Docker, no database)

Deployment data comes from the environment; the values are mapped in
`src/main/resources/application.yml`:

| Variable | Purpose | Dev default |
|---|---|---|
| `DASHBOARD_ISSUER_URI` | issuer of the shared Keycloak realm | `https://auth.homelabs.de/realms/homelabs` |
| `DASHBOARD_CLIENT_ID` | confidential client of that realm | `dashboard` |
| `DASHBOARD_CLIENT_SECRET` | that client's secret | — (required, no default) |
| `DASHBOARD_PROJECTS_API_URL` | REST base URL of the projects backend | `http://localhost:8093` |

The client secret is deliberately not part of the repository: the application
refuses to start without `DASHBOARD_CLIENT_SECRET`. For a dev run, keep the
four variables in the gitignored `.env` next to the checkout and export them
into the shell before starting:

```sh
set -a; . ./.env; set +a
mvn spring-boot:run
```

## Module 1: projects

`/projects` lists the projects as the backend's lean summaries (reference, name,
status badge, labels), filtered by the REST's own `status` and `q` parameters, and
carries the create form — an empty short code is derived from the name, a taken one
comes back as the API's own alternatives. `/projects/{reference}` shows one project:
canonical description, labels, the work-package/task tree in the backend's plan order,
and the comment, activity and attachment sections. The plan's rows edit themselves —
status, assignee, due date and position are one control per request, so a PATCH sends
exactly what was touched — and work packages, tasks and comments are created in place.
Every tree row opens the item's own page (`/projects/{reference}/work-packages/{item}`,
`…/tasks/{item}`): the full record with its title, description, labels and — on a task —
work-package editor, a task list in plan order on a work package, and the same project
header and a breadcrumb back to the project on both. Deactivate, activate and archive are
the detail header's explicit moves; an archived
project stays readable with its controls in place, and the API's refusal (409) shows up
at the row or form that caused it. Every mutation answers with the region it changed,
and every rejected write renders the API's own body, field names and suggestions
included. Attachment downloads go through the dashboard (`…/attachments/{id}/content`),
which forwards the session's token and passes the bytes through — the browser never
talks to the backend. The CSRF token rides the page body's `hx-headers` block, so every
htmx request carries it without a token per form.

## Design guide

`/design` documents the frontend inside the frontend: the shell's zones, the inventory of
pages that exist and — per API object — the wire fields the views read. `/design/components`
renders every pattern (page and project header, breadcrumb, status badges, label chips,
cards, list and plan rows, the record, the forms with the description field, the button
variants, the alert and notice blocks, the empty states) with the markup of the real
templates, so the DOM of that page is the copy source for a new view. Both pages are
static — example content, no backend calls — registered in the module registry, translated
through the same two bundles as the rest of the chrome and behind the login like every
other route.

`GET /health` is the open liveness probe (no session needed). Every other
route redirects to the realm login until a session exists — with the values
above the login is real, so the browser lands on the Keycloak login page and
comes back with a session; the topbar shows the realm user and offers the
sign-out form. Signing out ends the dashboard session (a POST through the
security chain); the realm's own SSO session stays, so the next page request
starts a fresh login round-trip. Sessions are held in memory only: a restart
logs you out.

## Commit messages

Lean, describe the implementation only — no references to planning artifacts.

## License

Apache-2.0 (see [LICENSE](LICENSE)).