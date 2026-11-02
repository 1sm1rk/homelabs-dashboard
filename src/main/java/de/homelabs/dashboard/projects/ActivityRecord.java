package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry of the append-only activity log ({@code GET /api/activities?parent=…}),
 * newest first. {@code eventType} is the backend's lowercase vocabulary; {@code detail}
 * is its composed summary, rendered as data.
 *
 * @param source the harness identity that drove the mutation, {@code null} for direct
 *               API calls
 */
public record ActivityRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("actor_sub") String actorSub,
        @JsonProperty("actor_name") String actorName,
        @JsonProperty("source") String source,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("detail") String detail,
        @JsonProperty("created_at") Instant createdAt) {
}
