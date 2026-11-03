package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The full project record of {@code GET /api/projects/{idOrCode}}: canonical HTML plus
 * its derived plain text, lifecycle status, provenance and the attached labels. Only the
 * detail view reads this shape.
 */
public record ProjectRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("short_code") String shortCode,
        @JsonProperty("name") String name,
        @JsonProperty("description_html") String descriptionHtml,
        @JsonProperty("description_text") String descriptionText,
        @JsonProperty("status") String status,
        @JsonProperty("owner_sub") String ownerSub,
        @JsonProperty("owner_name") String ownerName,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("labels") List<LabelRecord> labels) {

    public ProjectRecord {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
