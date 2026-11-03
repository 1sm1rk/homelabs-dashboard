package de.homelabs.dashboard.projects;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

/**
 * The projects API's error body, rendered as data instead of being reworded: the
 * {@code {"error": …}} text split into its segments, plus the {@code suggestions} of a
 * short-code collision.
 *
 * <p>The split follows the API's own shape. A validation answer prefixes each message
 * with the WIRE field name ({@code short_code: must be 2-6 characters …}) and joins
 * several problems with {@code "; "}. That name is what a reader has to correct, so it
 * stays visible next to its message and is never translated — the same
 * retry-don't-guess loop an agent gets from the REST surface.
 */
record ProjectsProblem(List<Segment> segments, List<String> suggestions) {

    /** One message of the body; {@code wireField} is the API's snake_case name or {@code null}. */
    record Segment(String wireField, String text) {
    }

    /** {@code <field>: <message>} — the shape of a bean-validation segment. */
    private static final Pattern FIELD_MESSAGE = Pattern.compile("^([a-z][a-z0-9_]*): (.+)$");

    /**
     * Parses one error body. A body that is not JSON is kept whole: the message is the
     * API's, whatever shape it arrives in, and dropping it would hide the reason.
     */
    static ProjectsProblem parse(String body, ObjectMapper objectMapper) {
        if (body == null || body.isBlank()) {
            return new ProjectsProblem(List.of(), List.of());
        }
        ApiError error = read(body, objectMapper);
        if (error == null) {
            return new ProjectsProblem(List.of(new Segment(null, body.strip())), List.of());
        }
        return new ProjectsProblem(segments(error.error()),
                error.suggestions() == null ? List.of() : List.copyOf(error.suggestions()));
    }

    private static ApiError read(String body, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(body, ApiError.class);
        }
        catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static List<Segment> segments(String error) {
        if (error == null || error.isBlank()) {
            return List.of();
        }
        List<Segment> segments = new ArrayList<>();
        for (String part : error.split("; ")) {
            Matcher fieldMessage = FIELD_MESSAGE.matcher(part);
            segments.add(fieldMessage.matches()
                    ? new Segment(fieldMessage.group(1), fieldMessage.group(2))
                    : new Segment(null, part));
        }
        return List.copyOf(segments);
    }

    /** The shared error shape of the projects API; {@code suggestions} only on a collision. */
    record ApiError(String error, List<String> suggestions) {
    }
}
