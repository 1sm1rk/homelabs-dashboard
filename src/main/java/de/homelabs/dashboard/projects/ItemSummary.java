package de.homelabs.dashboard.projects;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One LEAN work-package or task line of {@code GET /api/work-packages} and
 * {@code GET /api/tasks}: reference, title, status, the item's position inside its
 * project, assignee with display name, due date and a short plain-text excerpt.
 *
 * <p>{@code workPackageId} and {@code workPackageReference} carry the grouping of a TASK
 * line; work-package lines and ungrouped tasks answer {@code null} on both. That pair is
 * what the detail view groups by — the dashboard reads the membership off the wire
 * instead of calling one work-package endpoint per task.
 */
public record ItemSummary(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("title") String title,
        @JsonProperty("status") String status,
        @JsonProperty("sort_order") int sortOrder,
        @JsonProperty("assignee_sub") String assigneeSub,
        @JsonProperty("assignee_name") String assigneeName,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("excerpt") String excerpt,
        @JsonProperty("work_package_id") UUID workPackageId,
        @JsonProperty("work_package_reference") String workPackageReference) {
}
