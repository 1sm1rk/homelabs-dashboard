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
- Verify: `mvn clean verify` (unit tests, boot smoke test, shell rendering)

Deployment data comes from the environment; the dev defaults live in
`src/main/resources/application.yml`:

| Variable | Purpose | Dev default |
|---|---|---|
| `DASHBOARD_ISSUER_URI` | issuer of the shared Keycloak realm | `https://auth.homelabs.de/realms/homelabs` |
| `DASHBOARD_CLIENT_ID` | confidential client of that realm | `dashboard` |
| `DASHBOARD_CLIENT_SECRET` | that client's secret | `dashboard-dev-secret` |
| `DASHBOARD_PROJECTS_API_URL` | REST base URL of the projects backend | `http://localhost:8093` |

`GET /health` is the open liveness probe (no session needed). Every other
route redirects to the realm login until a session exists, so the login flow
needs the `dashboard` client in the realm. Sessions are held in memory only: a
restart logs you out.

## Commit messages

Lean, describe the implementation only — no references to planning artifacts.

## License

Apache-2.0 (see [LICENSE](LICENSE)).