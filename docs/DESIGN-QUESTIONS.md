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