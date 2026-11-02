package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attachment metadata of {@code GET /api/attachments?parent=…}: the bytes stay on the
 * backend's volume and reach the browser only through this module's download route.
 */
public record AttachmentRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("filename") String filename,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("byte_size") long byteSize,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("uploaded_by_sub") String uploadedBySub,
        @JsonProperty("uploaded_by_name") String uploadedByName,
        @JsonProperty("created_at") Instant createdAt) {
}
