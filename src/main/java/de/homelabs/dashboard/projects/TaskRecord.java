package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The full record of {@code GET /api/tasks/{idOrRef}}: the same record a work package
 * carries, plus its membership — the id of the work package it is grouped into and the
 * reference that names it, both {@code null} for a task without a group.
 */
public record TaskRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_short_code") String projectShortCode,
        @JsonProperty("work_package_id") UUID workPackageId,
        @JsonProperty("work_package_reference") String workPackageReference,
        @JsonProperty("title") String title,
        @JsonProperty("description_html") String descriptionHtml,
        @JsonProperty("description_text") String descriptionText,
        @JsonProperty("status") String status,
        @JsonProperty("assignee_sub") String assigneeSub,
        @JsonProperty("assignee_name") String assigneeName,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("seq") int seq,
        @JsonProperty("sort_order") int sortOrder,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("labels") List<LabelRecord> labels) {

    public TaskRecord {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
