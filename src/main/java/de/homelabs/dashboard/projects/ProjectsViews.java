package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapping from the REST wire records to what the templates render: the plan tree, the
 * lean rows, the section entries, and the presentation attributes of the two status
 * vocabularies (message key plus badge modifier).
 *
 * <p>Two rules hold every mapping here:
 *
 * <ul>
 *   <li>Order is the wire's order. The API delivers items by {@code sort_order}, then
 *       creation; grouping walks that sequence and never re-sorts it.</li>
 *   <li>Status text is a message key, never a literal — including the backend's lowercase
 *       wire values, so both bundles carry the same vocabulary.</li>
 * </ul>
 */
public final class ProjectsViews {

    /**
     * The project statuses the list filter offers; empty means the API's default view.
     * Public because the status vocabulary is the module's presentation contract: the
     * design guide renders its options and badges from exactly this list.
     */
    public static final List<StatusOption> PROJECT_STATUS_OPTIONS = List.of(
            new StatusOption("active", "status.project.active"),
            new StatusOption("deactivated", "status.project.deactivated"),
            new StatusOption("archived", "status.project.archived"));

    /**
     * The item statuses the row editors offer, in the API's order: the same five lowercase
     * wire values the items carry, so a select can never send a value the API rejects.
     * Public for the same reason as {@link #PROJECT_STATUS_OPTIONS}.
     */
    public static final List<StatusOption> ITEM_STATUS_OPTIONS = List.of(
            new StatusOption("todo", "status.item.todo"),
            new StatusOption("in_progress", "status.item.in_progress"),
            new StatusOption("blocked", "status.item.blocked"),
            new StatusOption("done", "status.item.done"),
            new StatusOption("cancelled", "status.item.cancelled"));

    /** One choice of a status filter or editor: the wire value and the key of its label. */
    public record StatusOption(String value, String labelKey) {
    }

    /** One lean project row of the list view. */
    public record ProjectRow(String reference, String name, String statusKey, String statusClass, List<String> labels) {
    }

    /**
     * The detail header: identity, lifecycle and labels of the open project. {@code status}
     * is the wire value the header decides its lifecycle moves from; {@code statusKey} and
     * {@code statusClass} are its label and badge modifier.
     */
    public record ProjectHeader(String reference, String name, String status, String statusKey, String statusClass,
            String ownerName, Instant updatedAt, String descriptionHtml, List<String> labels) {
    }

    /**
     * One work-package or task row. {@code status} is the wire value the editor submits
     * back, {@code statusKey} its label; {@code assigneeSub} is the value the API stores
     * (what an edit writes), {@code assignee} the display name a reader recognizes.
     */
    public record ItemRow(String reference, String title, String status, String statusKey, String statusClass,
            String assigneeSub, String assignee, LocalDate dueDate, int sortOrder) {
    }

    /** One work package with the tasks grouped under it, in wire order. */
    public record PackageNode(ItemRow workPackage, List<ItemRow> tasks) {
    }

    /** The plan tree: the project's packages, then the tasks without one. */
    public record ProjectTree(List<PackageNode> packages, List<ItemRow> ungroupedTasks) {
    }

    /**
     * The full record one item page renders. {@code kind} is the route segment the item's
     * own links build on ({@code work-packages} or {@code tasks}); {@code status} is the
     * wire value its editors submit, {@code statusKey} and {@code statusClass} its label
     * and badge modifier. {@code workPackageReference} is always {@code null} on a work
     * package — only a task carries a group.
     */
    public record ItemDetail(String kind, String reference, String title, String status, String statusKey,
            String statusClass, String descriptionHtml, String assigneeSub, String assignee, LocalDate dueDate,
            int seq, int sortOrder, Instant createdAt, Instant updatedAt, List<String> labels,
            String workPackageReference) {

        /** Route segment of a work package in the module's item pages. */
        static final String WORK_PACKAGES = "work-packages";

        /** Route segment of a task in the module's item pages. */
        static final String TASKS = "tasks";
    }

    /** One crumb of a breadcrumb trail: its label and the path it leads back to. */
    public record Crumb(String label, String path) {
    }

    /** The trail above an item page: its ancestors, then the item the page shows. */
    public record Breadcrumb(List<Crumb> ancestors, String current) {
    }

    /** One comment line. */
    public record CommentRow(String author, String content, Instant createdAt) {
    }

    /** One activity line; {@code eventKey} names the localized event vocabulary. */
    public record ActivityRow(String actor, String eventKey, String detail, String source, Instant createdAt) {
    }

    /** One attachment line of the metadata section. */
    public record AttachmentRow(UUID id, String filename, String mimeType, long byteSize, Instant createdAt) {
    }

    static ProjectRow projectRow(ProjectSummary summary) {
        return new ProjectRow(summary.reference(), summary.name(), projectStatusKey(summary.status()),
                projectStatusClass(summary.status()), summary.labels());
    }

    static ProjectHeader projectHeader(ProjectRecord project) {
        return new ProjectHeader(project.shortCode(), project.name(), project.status(),
                projectStatusKey(project.status()), projectStatusClass(project.status()), project.ownerName(),
                project.updatedAt(), project.descriptionHtml(), labelNames(project.labels()));
    }

    /** The detail view of one work package; it carries no group of its own. */
    static ItemDetail itemDetail(WorkPackageRecord item) {
        return new ItemDetail(ItemDetail.WORK_PACKAGES, item.reference(), item.title(), item.status(),
                itemStatusKey(item.status()), itemStatusClass(item.status()), item.descriptionHtml(),
                item.assigneeSub(), firstPresent(item.assigneeName(), item.assigneeSub()), item.dueDate(),
                item.seq(), item.sortOrder(), item.createdAt(), item.updatedAt(), labelNames(item.labels()), null);
    }

    /** The detail view of one task, its work-package reference included. */
    static ItemDetail itemDetail(TaskRecord item) {
        return new ItemDetail(ItemDetail.TASKS, item.reference(), item.title(), item.status(),
                itemStatusKey(item.status()), itemStatusClass(item.status()), item.descriptionHtml(),
                item.assigneeSub(), firstPresent(item.assigneeName(), item.assigneeSub()), item.dueDate(),
                item.seq(), item.sortOrder(), item.createdAt(), item.updatedAt(), labelNames(item.labels()),
                item.workPackageReference());
    }

    /**
     * The breadcrumb above an item page: the project first, then — on a task page — the
     * work package it is grouped into, then the item itself. The way back is always the
     * project; the work-package crumb reaches the package's own page.
     */
    static Breadcrumb breadcrumb(ProjectHeader project, ItemDetail item) {
        String projectPath = "/projects/" + project.reference();
        List<Crumb> ancestors = new ArrayList<>();
        ancestors.add(new Crumb(project.name(), projectPath));
        if (item.workPackageReference() != null) {
            ancestors.add(new Crumb(item.workPackageReference(),
                    projectPath + "/" + ItemDetail.WORK_PACKAGES + "/" + item.workPackageReference()));
        }
        return new Breadcrumb(List.copyOf(ancestors), item.reference());
    }

    static List<ItemRow> itemRows(List<ItemSummary> items) {
        return items.stream().map(ProjectsViews::itemRow).toList();
    }

    /**
     * Groups the task list under the work-package list: package order as delivered, task
     * order as delivered inside each group, and a task whose membership is empty — or
     * names a package the list does not carry — in the trailing ungrouped group. Nothing
     * is sorted here; the backend owns the plan order.
     */
    static ProjectTree tree(List<ItemSummary> packages, List<ItemSummary> tasks) {
        Set<UUID> packageIds = packages.stream().map(ItemSummary::id).collect(Collectors.toSet());
        Map<UUID, List<ItemRow>> tasksByPackage = new LinkedHashMap<>();
        List<ItemRow> ungrouped = new ArrayList<>();
        for (ItemSummary task : tasks) {
            if (task.workPackageId() != null && packageIds.contains(task.workPackageId())) {
                tasksByPackage.computeIfAbsent(task.workPackageId(), id -> new ArrayList<>()).add(itemRow(task));
            }
            else {
                ungrouped.add(itemRow(task));
            }
        }
        List<PackageNode> nodes = packages.stream()
                .map(workPackage -> new PackageNode(itemRow(workPackage),
                        List.copyOf(tasksByPackage.getOrDefault(workPackage.id(), List.of()))))
                .toList();
        return new ProjectTree(nodes, List.copyOf(ungrouped));
    }

    static List<CommentRow> commentRows(List<CommentRecord> comments) {
        return comments.stream()
                .map(comment -> new CommentRow(firstPresent(comment.authorName(), comment.authorSub()),
                        comment.content(), comment.createdAt()))
                .toList();
    }

    static List<ActivityRow> activityRows(List<ActivityRecord> activities) {
        return activities.stream()
                .map(activity -> new ActivityRow(firstPresent(activity.actorName(), activity.actorSub()),
                        eventKey(activity.eventType()), activity.detail(), activity.source(), activity.createdAt()))
                .toList();
    }

    static List<AttachmentRow> attachmentRows(List<AttachmentRecord> attachments) {
        return attachments.stream()
                .map(attachment -> new AttachmentRow(attachment.id(), attachment.filename(), attachment.mimeType(),
                        attachment.byteSize(), attachment.createdAt()))
                .toList();
    }

    private static ItemRow itemRow(ItemSummary item) {
        return new ItemRow(item.reference(), item.title(), item.status(), itemStatusKey(item.status()),
                itemStatusClass(item.status()), item.assigneeSub(),
                firstPresent(item.assigneeName(), item.assigneeSub()), item.dueDate(), item.sortOrder());
    }

    /** Label names in the API's order; the names are what an editor sends back. */
    private static List<String> labelNames(List<LabelRecord> labels) {
        return labels.stream().map(LabelRecord::name).toList();
    }

    /**
     * Lifecycle status of a project as a message key; unknown values stay visible. Public
     * together with the class mappers below: they are the module's status vocabulary and
     * the design guide renders its badges from exactly these functions.
     */
    public static String projectStatusKey(String status) {
        return switch (status == null ? "" : status) {
            case "active", "deactivated", "archived" -> "status.project." + status;
            default -> "status.unknown";
        };
    }

    /** Badge modifier of a project status — archived is muted, deactivated warns. */
    public static String projectStatusClass(String status) {
        return switch (status == null ? "" : status) {
            case "active" -> "badge--active";
            case "deactivated" -> "badge--warning";
            case "archived" -> "badge--muted";
            default -> "";
        };
    }

    /** Work-package and task status as a message key. */
    public static String itemStatusKey(String status) {
        return switch (status == null ? "" : status) {
            case "todo", "in_progress", "blocked", "done", "cancelled" -> "status.item." + status;
            default -> "status.unknown";
        };
    }

    /** Badge modifier of an item status; {@code todo} keeps the neutral badge. */
    public static String itemStatusClass(String status) {
        return switch (status == null ? "" : status) {
            case "in_progress" -> "badge--active";
            case "done" -> "badge--done";
            case "blocked" -> "badge--danger";
            case "cancelled" -> "badge--muted";
            default -> "";
        };
    }

    /** Event type of the activity log as a message key. */
    static String eventKey(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case "created", "updated", "status_changed", "labeled", "unlabeled", "commented", "attached", "detached" ->
                    "activity." + eventType;
            default -> "activity.unknown";
        };
    }

    private static String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private ProjectsViews() {
    }
}
