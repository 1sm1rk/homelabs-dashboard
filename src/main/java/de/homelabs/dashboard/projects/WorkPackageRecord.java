package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The full record of {@code GET /api/work-packages/{idOrRef}}: the canonical content and
 * its derived plain text, status, the assignee (subject plus the display name resolved by
 * the API), due date, both numbers the record carries — {@code seq} as the reference
 * number, {@code sortOrder} as the mutable position inside the project — timestamps and
 * labels.
 *
 * <p>The lean list answer ({@link ItemSummary}) is what the plan tree reads; this shape is
 * the detail view's own record.
 */
public record WorkPackageRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("project_short_code") String projectShortCode,
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

    public WorkPackageRecord {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
