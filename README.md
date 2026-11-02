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

Activates with the first implementation step (the repository currently holds
the scaffold):

- Run: `mvn spring-boot:run` (dev port 8094)
- Verify (tests): `mvn clean verify`

`GET /health` is the open liveness probe once the application boots.

## Commit messages

Lean, describe the implementation only — no references to planning artifacts.

## License

Apache-2.0 (see [LICENSE](LICENSE)).