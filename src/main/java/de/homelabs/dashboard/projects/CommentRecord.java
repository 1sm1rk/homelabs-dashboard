package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One comment of {@code GET /api/comments?parent=…}, plain text, newest first. */
public record CommentRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("author_sub") String authorSub,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("content") String content,
        @JsonProperty("created_at") Instant createdAt) {
}
