# VISION — homelabs-dashboard

## Why this project exists

Every homelab service so far exposes an API — for agents — or nothing at all.
The human in the loop works with files and terminals. This project gives the
services a single, calm browser surface: one place where homelab data can be
looked at and worked with, growing module by module as the backends grow. The
dashboard renders everything server-side (Thymeleaf fragments + htmx) —
no SPA framework, no build chain, plain hand-written CSS — so the frontend
stays as lean and repairable as the services it shows.

## User

The homelab's owner working daily with the services (project management
first). Single authenticated realm user today; multi-user-ready for free,
because authentication and data both stay with the services.

## Non-goals

- No SPA/JS framework, no npm/node build, no CSS framework — hand-written
  CSS only.
- No fragment-producing backends: the dashboard is the single renderer (the
  backends stay pure REST/JSON).
- No dashboard-owned data: work state stays in the backends.
- No permission model of its own; display mirrors the backend's rules.

## Success criteria

- Daily use: project work happens in the browser instead of around the API.
- A new module joins as one package + one registry entry — no shell surgery.