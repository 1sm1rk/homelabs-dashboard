package de.homelabs.dashboard.design;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import de.homelabs.dashboard.projects.ProjectsViews;
import de.homelabs.dashboard.projects.ProjectsViews.Breadcrumb;
import de.homelabs.dashboard.projects.ProjectsViews.Crumb;
import de.homelabs.dashboard.projects.ProjectsViews.ItemDetail;
import de.homelabs.dashboard.projects.ProjectsViews.ItemRow;
import de.homelabs.dashboard.projects.ProjectsViews.ProjectHeader;
import de.homelabs.dashboard.projects.ProjectsViews.ProjectRow;
import de.homelabs.dashboard.projects.ProjectsViews.StatusOption;

/**
 * The example content of the design guide's component catalog.
 *
 * <p>Every specimen is typed by the module's own view records, never by a parallel sample
 * type: the catalog renders the real fragments ({@code templates/projects/*}), and those
 * fragments read exactly these records. A renamed accessor therefore breaks the catalog at
 * render time instead of leaving a stale copy behind — the reason the guide can claim to
 * be in sync with the code.
 *
 * <p>The values are illustrative only: no REST call is made anywhere on this page, so the
 * catalog shows patterns, not states. Content strings (titles, label names, the API's own
 * message) are DATA and therefore not translated — only the surrounding chrome is.
 */
public final class DesignSamples {

    /** Project reference the specimens' links are built on. */
    public static final String PROJECT_REFERENCE = "DG-1";

    private static final String ASSIGNEE_SUB = "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90";

    private static final Instant CREATED_AT = Instant.parse("2026-11-02T09:15:00Z");

    private static final Instant UPDATED_AT = Instant.parse("2026-11-03T07:40:00Z");

    private static final List<String> LABELS = List.of("design", "css");

    /**
     * The project header in its ARCHIVED state: the lifecycle buttons stay out of the
     * specimen (the real fragment renders them only for a live project), so the pattern is
     * inert while it still shows the badge, the chips and the read-only notice.
     */
    public static ProjectHeader projectHeader() {
        String status = "archived";
        return new ProjectHeader(PROJECT_REFERENCE, "Design guide", status,
                ProjectsViews.projectStatusKey(status), ProjectsViews.projectStatusClass(status),
                "di.mueller", UPDATED_AT, null, LABELS);
    }

    /** The trail of an item page: the project, then the open item. */
    public static Breadcrumb breadcrumb() {
        return new Breadcrumb(List.of(new Crumb("Design guide", "/projects/" + PROJECT_REFERENCE)), "DG-3");
    }

    /** The full record of a task: the shape the item page renders and edits field by field. */
    public static ItemDetail item() {
        return new ItemDetail("tasks", "DG-3", "Component catalog", "in_progress",
                ProjectsViews.itemStatusKey("in_progress"), ProjectsViews.itemStatusClass("in_progress"),
                "<p>Canonical HTML as the API stores it — paragraphs, lists, code.</p>",
                ASSIGNEE_SUB, "di.mueller", LocalDate.of(2026, 11, 20), 7, 2, CREATED_AT, UPDATED_AT,
                LABELS, "DG-2");
    }

    /** One work-package line of the plan: the highlighted package row. */
    public static ItemRow packageRow() {
        return new ItemRow("DG-2", "Frame and tokens", "in_progress",
                ProjectsViews.itemStatusKey("in_progress"), ProjectsViews.itemStatusClass("in_progress"),
                ASSIGNEE_SUB, "di.mueller", LocalDate.of(2026, 11, 20), 1);
    }

    /** One task line of the plan: indented under its package, unassigned and undated. */
    public static ItemRow taskRow() {
        return new ItemRow("DG-3", "Component catalog", "todo",
                ProjectsViews.itemStatusKey("todo"), ProjectsViews.itemStatusClass("todo"),
                null, null, null, 2);
    }

    /** The work packages a task can be moved into; the select offers them by reference. */
    public static List<ItemRow> workPackages() {
        return List.of(packageRow(),
                new ItemRow("DG-4", "Schematic section", "done",
                        ProjectsViews.itemStatusKey("done"), ProjectsViews.itemStatusClass("done"),
                        ASSIGNEE_SUB, "di.mueller", LocalDate.of(2026, 11, 10), 3));
    }

    /** Two lean project rows: one with labels, one deactivated without any. */
    public static List<ProjectRow> projectRows() {
        return List.of(
                new ProjectRow("DG-1", "Design guide", "status.project.active",
                        ProjectsViews.projectStatusClass("active"), LABELS),
                new ProjectRow("DG-7", "Import follow-up", "status.project.deactivated",
                        ProjectsViews.projectStatusClass("deactivated"), List.of()));
    }

    /** Label names as the backend delivers them: backend data, never translated. */
    public static List<String> labels() {
        return List.of("design", "css", "wire");
    }

    /** One status badge of the catalog; the fragment renders key plus modifier. */
    public record Badge(String labelKey, String modifier) {
    }

    /**
     * Every project status as a badge, derived from the module's own status vocabulary —
     * a status added there appears here without touching the guide.
     */
    public static List<Badge> projectBadges() {
        return badges(ProjectsViews.PROJECT_STATUS_OPTIONS, true);
    }

    /** Every item status as a badge, derived the same way. */
    public static List<Badge> itemBadges() {
        return badges(ProjectsViews.ITEM_STATUS_OPTIONS, false);
    }

    /** The fallback badge of an unknown wire value: neutral, never a guess. */
    public static Badge unknownBadge() {
        return new Badge("status.unknown", "");
    }

    /** One wire field message of a rejected write: the API's field name and its words. */
    public record Segment(String wireField, String text) {
    }

    /** A rejection body as the problem fragment reads it: segments plus suggestions. */
    public record Problem(List<Segment> segments, List<String> suggestions) {
    }

    /** A 400: every message names the wire field a retry has to correct. */
    public static Problem badRequestProblem() {
        return new Problem(List.of(
                new Segment("name", "must not be blank"),
                new Segment("short_code", "must be 2-6 characters of A-Z and 0-9")), List.of());
    }

    /** A 409 of a short-code collision: the API names the codes that are still free. */
    public static Problem conflictProblem() {
        return new Problem(List.of(new Segment("short_code", "is already taken")),
                List.of("DGX", "DG2", "DG9"));
    }

    private static List<Badge> badges(List<StatusOption> options, boolean project) {
        return options.stream()
                .map(option -> new Badge(option.labelKey(), project
                        ? ProjectsViews.projectStatusClass(option.value())
                        : ProjectsViews.itemStatusClass(option.value())))
                .toList();
    }

    private DesignSamples() {
    }
}
