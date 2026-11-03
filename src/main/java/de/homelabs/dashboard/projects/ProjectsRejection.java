package de.homelabs.dashboard.projects;

/**
 * A mutation the projects API answered with 400 or 409: the module shows the API's own
 * body inline at the control that caused it, so the reader can retry with the API's
 * suggestions or corrected values instead of guessing what went wrong.
 *
 * <p>{@code retarget} is the selector of the notice block the fragment belongs to. It
 * travels as {@code HX-Retarget} because a request's own swap target is the region a
 * SUCCESSFUL mutation replaces — an error must not replace it.
 */
final class ProjectsRejection extends RuntimeException {

    private final int status;

    private final String body;

    private final String retarget;

    ProjectsRejection(int status, String body, String retarget) {
        super("the projects API rejected the mutation with " + status);
        this.status = status;
        this.body = body;
        this.retarget = retarget;
    }

    int status() {
        return status;
    }

    /** The API's body as it arrived; {@code null} when the answer carried none. */
    String body() {
        return body;
    }

    String retarget() {
        return retarget;
    }
}
