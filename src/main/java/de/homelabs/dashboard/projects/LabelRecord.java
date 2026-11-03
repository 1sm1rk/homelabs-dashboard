package de.homelabs.dashboard.projects;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One label as every full record of the API carries it: assignment travels by name, the
 * id and the optional colour are provenance (the dashboard writes names, never ids).
 */
public record LabelRecord(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("color") String color) {
}
