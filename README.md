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
  sign-out and session-cookie integration tests)

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