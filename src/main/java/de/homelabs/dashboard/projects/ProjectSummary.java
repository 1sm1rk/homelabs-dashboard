package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One LEAN project line of {@code GET /api/projects}: reference (the project's short
 * code), name, status, creator display name, last update, the label names and a short
 * plain-text excerpt — the canonical description stays out of list answers (excerpt
 * discipline).
 *
 * <p>Every component names its wire field explicitly: the dashboard never relies on a
 * naming strategy, and Jackson silently drops a field whose name does not match.
 */
public record ProjectSummary(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("name") String name,
        @JsonProperty("status") String status,
        @JsonProperty("owner_name") String ownerName,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("excerpt") String excerpt,
        @JsonProperty("labels") List<String> labels) {

    public ProjectSummary {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
