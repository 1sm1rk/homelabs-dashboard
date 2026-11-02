# DESIGN-QUESTIONS — homelabs-dashboard

Cross-session escalation protocol. Execution subagents do not make design
decisions; questions escalate through this file.

## Protocol

1. **Execution subagent:** when implementation hits a point that needs a
   design decision, add a question below — `Q<n>`, one question per point,
   with the context the design session needs (and options, if you see them).
   Commit the file. Then WAIT on that point: do not decide it yourself, do
   not guess; continue only with work that does not depend on the answer.
2. **Design session:** answer directly below the question (`A<n>`), set its
   status to ANSWERED, and mirror lasting decisions into the project's docs
   and the management tool. Commit.
3. **Execution subagent:** pick up ANSWERED questions and continue.

Status: OPEN / ANSWERED. Questions and answers stay in this file as a record
of decided design points.

## Questions

<!-- Template for a question:

### Q<n> — <one-line title> — OPEN

<context the design session needs: what is being built, what is unknown,
options if you see them>

**Question:** <the specific decision needed>

**Blocking?** <yes / no — yes means work on this point is paused>

---
-->

### Q1 — Project list: where do the labels come from? — ANSWERED

**A1 — Option 1 (backend): the lean project list answer gains the labels.**

Decision rationale (design session, 2026-11-02): labels are LEAN BY NATURE — a
project carries at most a handful of label names, so adding them to the summary
keeps the excerpt discipline intact (the discipline exists to keep lists light;
two or three short strings per row do not threaten that). Option 2 is rejected
as the exact counter-pattern: one full canonical record per row is N+1 calls
AND a full-HTML transfer for a view defined as excerpt-only. Option 3 is
rejected because the committed view content deliberately puts labels on the
list (the daily overview reads labels before opening anything).

Consequences:

- The REST contract gains `labels` on the project lean summary — ADDITIVE, no
  breaking change.
- Recorded as the backend task **HP-12** in the tool; implemented + the
  dashboard's fake-API ITs code against the NEW shape immediately; the adapter
  and the gateway are NOT forced (their DTOs read the richer answer and
  silently drop the new field per the existing Jackson behavior — mirroring it
  there is a deliberate, separate decision, NOT wanted now: agents get labels
  through the find filters, not extra summary weight).
- The prod deploy of the backend change rides the next deployment moment
  (latest-pull; no dedicated outage step).

**Blocking?** resolved.

---

### Q2 — Project detail: how is the work-package/task tree composed? — ANSWERED

**A2 — Option 1 (backend): the lean work-item (task) answer gains the package
membership — `work_package_id` + `work_package_reference`, nullable.**

Decision rationale: the tree is the detail view's main content; one work-package
call + one task call per load (composition, not N+2 fan-out) is the honest BFF
shape. The membership is a read-shape addition, not a missing backend
capability, so it belongs with the backend contract.

Consequences:

- `null` membership = a task without a work package: it renders as its OWN
  group, AFTER the packages (the domain allows package-less tasks; hiding them
  would lie about the data).
- The shared lean summary record gains the two nullable components (they are
  null on work-package rows); ADDITIVE — no breaking change for existing
  consumers.
- THE DASHBOARD COMPOSES, IT DOES NOT RE-SORT: grouping by the ids, order
  preserved as the wire delivers it (`sort_order asc, created_at, id`).
- Same adapter consequence as A1: dropped silently until deliberately mirrored.

**Blocking?** resolved.

---