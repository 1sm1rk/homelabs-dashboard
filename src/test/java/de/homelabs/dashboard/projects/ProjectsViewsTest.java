package de.homelabs.dashboard.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping between the REST wire and the views: the plan tree keeps the backend's
 * order, package-less (and unresolved) tasks stay visible in their own group, and both
 * status vocabularies map onto one badge component with a message key per value.
 */
class ProjectsViewsTest {

    private static final UUID FIRST_PACKAGE = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    private static final UUID SECOND_PACKAGE = UUID.fromString("9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a");

    private static final UUID UNKNOWN_PACKAGE = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    void keepsTheWireOrderOfPackagesAndTasksInsteadOfSorting() {
        List<ItemSummary> packages = List.of(packageRow(SECOND_PACKAGE, "WEB-7", "Second package", 2),
                packageRow(FIRST_PACKAGE, "WEB-6", "First package", 1));
        List<ItemSummary> tasks = List.of(taskRow("WEB-9", "Loose task", null, 4),
                taskRow("WEB-8", "Grouped task", FIRST_PACKAGE, 3));

        ProjectsViews.ProjectTree tree = ProjectsViews.tree(packages, tasks);

        assertThat(tree.packages()).extracting(node -> node.workPackage().title())
                .as("the API's answer is the plan order — re-sorting here would fight it")
                .containsExactly("Second package", "First package");
        assertThat(tree.packages().get(1).tasks()).extracting(ProjectsViews.ItemRow::title)
                .containsExactly("Grouped task");
        assertThat(tree.packages().getFirst().tasks()).isEmpty();
        assertThat(tree.ungroupedTasks()).extracting(ProjectsViews.ItemRow::title).containsExactly("Loose task");
    }

    @Test
    void keepsATaskWhosePackageIsNotInTheListVisible() {
        ProjectsViews.ProjectTree tree = ProjectsViews.tree(List.of(packageRow(FIRST_PACKAGE, "WEB-6", "Package", 1)),
                List.of(taskRow("WEB-8", "Orphaned task", UNKNOWN_PACKAGE, 2)));

        assertThat(tree.packages().getFirst().tasks()).isEmpty();
        assertThat(tree.ungroupedTasks()).extracting(ProjectsViews.ItemRow::title).containsExactly("Orphaned task");
    }

    @Test
    void mapsEveryItemStatusToAKeyAndABadgeModifier() {
        assertThat(itemStatusKeys()).extracting(ProjectsViews::itemStatusKey)
                .containsExactly("status.item.todo", "status.item.in_progress", "status.item.blocked",
                        "status.item.done", "status.item.cancelled");
        assertThat(itemStatusKeys()).extracting(ProjectsViews::itemStatusClass)
                .containsExactly("", "badge--active", "badge--danger", "badge--done", "badge--muted");
    }

    @Test
    void offersTheWireVocabularyInTheStatusEditorInTheApisOrder() {
        assertThat(ProjectsViews.ITEM_STATUS_OPTIONS).extracting(ProjectsViews.StatusOption::value)
                .as("a select that offered anything else would send a value the API rejects")
                .containsExactly("todo", "in_progress", "blocked", "done", "cancelled");
        assertThat(ProjectsViews.ITEM_STATUS_OPTIONS)
                .allSatisfy(option -> assertThat(ProjectsViews.itemStatusKey(option.value()))
                        .isEqualTo(option.labelKey()));
    }

    @Test
    void mapsEveryProjectStatusToAKeyAndABadgeModifier() {
        List<String> statuses = List.of("active", "deactivated", "archived");

        assertThat(statuses).extracting(ProjectsViews::projectStatusKey)
                .containsExactly("status.project.active", "status.project.deactivated", "status.project.archived");
        assertThat(statuses).extracting(ProjectsViews::projectStatusClass)
                .containsExactly("badge--active", "badge--warning", "badge--muted");
    }

    @Test
    void keepsAnUnknownWireValueReadableInsteadOfFailingTheMessageLookup() {
        assertThat(ProjectsViews.itemStatusKey("paused")).isEqualTo("status.unknown");
        assertThat(ProjectsViews.projectStatusKey(null)).isEqualTo("status.unknown");
        assertThat(ProjectsViews.eventKey("resurrected")).isEqualTo("activity.unknown");
    }

    @Test
    void mapsAProjectSummaryToALeanRow() {
        ProjectsViews.ProjectRow row = ProjectsViews.projectRow(new ProjectSummary(
                UUID.fromString("1b3f6d2e-5f7a-4c8b-9d10-2e3f4a5b6c7d"), "WEB", "homelabs dashboard", "active",
                "di.mueller", Instant.parse("2026-11-02T10:33:08Z"), "excerpt", List.of("designed")));

        assertThat(row.reference()).isEqualTo("WEB");
        assertThat(row.statusKey()).isEqualTo("status.project.active");
        assertThat(row.labels()).containsExactly("designed");
    }

    @Test
    void resolvesTheAssigneeNameWithTheSubjectAsFallback() {
        ProjectsViews.ItemRow named = ProjectsViews.itemRows(List.of(taskRow("WEB-8", "Task", null, 1))).getFirst();
        ProjectsViews.ItemRow unnamed = ProjectsViews
                .itemRows(List.of(new ItemSummary(UUID.randomUUID(), "WEB-9", "Task", "todo", 2, null, null, null,
                        "", null, null)))
                .getFirst();

        assertThat(named.assignee()).isEqualTo("di.mueller");
        assertThat(unnamed.assignee()).isNull();
    }

    @Test
    void mapsAFullRecordToTheItemViewWithTheRouteSegmentItsEditorsPostTo() {
        ProjectsViews.ItemDetail workPackage = ProjectsViews.itemDetail(fullWorkPackage());
        ProjectsViews.ItemDetail task = ProjectsViews.itemDetail(fullTask());

        assertThat(workPackage.kind()).isEqualTo(ProjectsViews.ItemDetail.WORK_PACKAGES);
        assertThat(workPackage.reference()).isEqualTo("WEB-1");
        assertThat(workPackage.status()).isEqualTo("in_progress");
        assertThat(workPackage.statusKey()).isEqualTo("status.item.in_progress");
        assertThat(workPackage.statusClass()).isEqualTo("badge--active");
        assertThat(workPackage.assigneeSub()).isEqualTo("8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90");
        assertThat(workPackage.assignee()).isEqualTo("di.mueller");
        assertThat(workPackage.seq()).isEqualTo(7);
        assertThat(workPackage.sortOrder()).isEqualTo(2);
        assertThat(workPackage.labels()).containsExactly("designed");
        assertThat(workPackage.workPackageReference())
                .as("a work package lives outside the grouping it owns")
                .isNull();

        assertThat(task.kind()).isEqualTo(ProjectsViews.ItemDetail.TASKS);
        assertThat(task.workPackageReference()).isEqualTo("WEB-1");
    }

    @Test
    void trailsAnItemPageThroughTheProjectAndTheTasksWorkPackage() {
        ProjectsViews.ProjectHeader project = ProjectsViews.projectHeader(fullProject());

        ProjectsViews.Breadcrumb workPackageTrail = ProjectsViews.breadcrumb(project,
                ProjectsViews.itemDetail(fullWorkPackage()));
        assertThat(workPackageTrail.ancestors()).extracting(ProjectsViews.Crumb::label)
                .containsExactly("homelabs dashboard");
        assertThat(workPackageTrail.current()).isEqualTo("WEB-1");

        ProjectsViews.Breadcrumb taskTrail = ProjectsViews.breadcrumb(project, ProjectsViews.itemDetail(fullTask()));
        assertThat(taskTrail.ancestors()).extracting(ProjectsViews.Crumb::label)
                .as("the task trail names the work package it belongs to")
                .containsExactly("homelabs dashboard", "WEB-1");
        assertThat(taskTrail.ancestors()).extracting(ProjectsViews.Crumb::path)
                .as("every crumb above the open item leads back to the project")
                .containsOnly("/projects/WEB");
        assertThat(taskTrail.current()).isEqualTo("WEB-2");
    }

    private static List<String> itemStatusKeys() {
        return List.of("todo", "in_progress", "blocked", "done", "cancelled");
    }

    private static ProjectRecord fullProject() {
        return new ProjectRecord(UUID.randomUUID(), "WEB", "homelabs dashboard", "<p>description</p>",
                "description", "active", "8f2a1c44", "di.mueller", Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-03T09:00:00Z"), List.of(label()));
    }

    private static WorkPackageRecord fullWorkPackage() {
        return new WorkPackageRecord(UUID.randomUUID(), "WEB-1", UUID.randomUUID(), "WEB", "Package one",
                "<p>package</p>", "package", "in_progress", "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90", "di.mueller",
                LocalDate.parse("2026-11-20"), 7, 2, Instant.parse("2026-11-02T10:00:00Z"),
                Instant.parse("2026-11-03T09:00:00Z"), List.of(label()));
    }

    private static TaskRecord fullTask() {
        return new TaskRecord(UUID.randomUUID(), "WEB-2", UUID.randomUUID(), "WEB", FIRST_PACKAGE, "WEB-1",
                "A task", "<p>task</p>", "task", "todo", null, null, null, 8, 3,
                Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-03T09:00:00Z"), List.of());
    }

    private static LabelRecord label() {
        return new LabelRecord(UUID.randomUUID(), "designed", null);
    }

    private static ItemSummary packageRow(UUID id, String reference, String title, int sortOrder) {
        return new ItemSummary(id, reference, title, "todo", sortOrder, null, null, null, "", null, null);
    }

    private static ItemSummary taskRow(String reference, String title, UUID workPackageId, int sortOrder) {
        return new ItemSummary(UUID.randomUUID(), reference, title, "todo", sortOrder, "8f2a1c44", "di.mueller",
                LocalDate.parse("2026-11-20"), "", workPackageId, null);
    }
}
