/**
 * Module 1 — the project-management backend's rendering package: the REST client, the
 * wire records, the view mapping and the module's read views
 * ({@code templates/projects/}).
 *
 * <p>The module consumes the backend's REST contract 1:1: wire records name their
 * SNAKE_CASE fields explicitly, the views render the backend's order instead of sorting,
 * and every call forwards the session's access token. It never forks the domain — a
 * shape a view needs belongs to the backend.
 */
package de.homelabs.dashboard.projects;
