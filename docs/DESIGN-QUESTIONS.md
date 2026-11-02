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

### Q1 — Project list: where do the labels come from? — OPEN

The list view is specified with four columns per project: reference, name, a
status badge and the project's labels. The lean list answer of the projects API
(`GET /api/projects`) carries `id`, `reference`, `name`, `status`, `owner_name`,
`updated_at` and `excerpt` — no labels. Project labels exist only in the full
record (`GET /api/projects/{idOrCode}`), which is the detail view's source.

Options as the implementation sees them:

1. **Backend.** Add the project's labels to the lean list answer. The list keeps
   one call per page and stays lean; the dashboard changes not at all.
2. **Dashboard, one full record per row.** Call the get endpoint for every row of
   the list page and render only the label chips from it. N+1 calls per list
   load, and full canonical content is transferred for a view that is meant to
   carry excerpts only.
3. **View change.** The list renders reference, name and status badge; labels
   appear on the detail page, which already has them.

**Question:** which of the three is binding? If option 3, the list's required
content changes with it.

**Blocking?** yes — "lean list answer" and "labels rendered in the list" cannot
both hold with today's REST shape.

---

### Q2 — Project detail: how is the work-package/task tree composed? — OPEN

The detail view renders the work packages in wire order with each package's
tasks grouped underneath, tasks in wire order. The lean task answer
(`GET /api/tasks?project=…`) carries `id`, `reference`, `title`, `status`,
`sort_order`, `assignee_sub`, `assignee_name`, `due_date` and `excerpt` — no
work-package membership, so a task cannot be attributed to a package from that
answer.

Options as the implementation sees them:

1. **Backend.** Add the group to the lean task answer (`work_package_id` /
   `work_package_reference`). The dashboard then composes the tree from one
   work-package call and one task call.
2. **Dashboard composition.** One lean task call per work package
   (`?project=…&wp=<reference>`) plus one project-wide lean call to recognise
   the tasks that belong to no package — N+2 calls per detail load.
3. **Flat rendering.** Work packages and tasks in one list, grouped by nothing;
   no tree.

**Question:** which is binding? If option 2: where do ungrouped tasks render —
as their own group, or not at all?

**Blocking?** yes — the tree is the detail view's main content.

---