package de.homelabs.dashboard.projects;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import de.homelabs.dashboard.projects.ProjectsClient.ItemPatch;
import de.homelabs.dashboard.projects.ProjectsClient.ProjectTransition;

/**
 * The projects module: the lean project list, the project detail with its plan tree, the
 * comments, activity and attachments sections, and the mutation flows that write back
 * through the same REST contract.
 *
 * <p>Every route answers twice — as a full page for a link or a reload, and as the
 * fragment an htmx request swaps in place. The fragment is the same template: the full
 * page wraps it in the shell layout, the htmx answer returns just the piece.
 *
 * <p>A mutation answers with the region it changed, re-read from the API afterwards: the
 * backend owns the order (a {@code sort_order} change moves a row) and composes its own
 * full records, so a fresh read is the honest way to show where a change landed. When
 * the API rejects a mutation with 400 or 409, the module shows the API's own body at the
 * control instead — {@link ProjectsRejection} carries the notice block, and the error
 * handler retargets the swap there.
 */
@Controller
@RequestMapping("/projects")
public class ProjectsController {

    /** Model attribute of the open project's reference (the section links build on it). */
    static final String REFERENCE = "reference";

    /** Notice block of the create-project form on the list page. */
    private static final String CREATE_PROJECT_NOTICE = "#create-project-notice";

    /** Notice block of the detail header (lifecycle moves). */
    private static final String PROJECT_NOTICE = "#project-notice";

    /** Notice blocks of the two create forms inside the plan card. */
    private static final String WORK_PACKAGE_NOTICE = "#create-work-package-notice";

    private static final String TASK_NOTICE = "#create-task-notice";

    /** Notice block of the comment form. */
    private static final String COMMENTS_NOTICE = "#comments-notice";

    /** Selector prefix of a plan row's own notice block, followed by the item reference. */
    private static final String ITEM_NOTICE = "#item-notice-";

    private final ProjectsClient projects;

    public ProjectsController(ProjectsClient projects) {
        this.projects = projects;
    }

    /**
     * The project list. {@code status} and {@code q} are handed to the REST filters
     * unchanged; without them the backend answers its default view (active projects).
     */
    @GetMapping
    public String list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            Model model) {
        addListModel(model, status, q);
        return "projects/list";
    }

    /** The list rows alone: the filter form swaps them, the page around them stays. */
    @GetMapping("/rows")
    public String rows(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            Model model) {
        addListModel(model, status, q);
        return "projects/rows :: rows";
    }

    /**
     * Creates a project and answers with the list panel — the create form empty again,
     * the rows a fresh read of the filters this page shows. A short code is optional: the
     * API derives it from the name, and a taken one comes back as 409 with alternatives.
     */
    @PostMapping
    public String create(@RequestParam String name,
            @RequestParam(name = "short_code", required = false) String shortCode,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            Model model, HttpServletRequest request) {
        write(() -> projects.createProject(name, shortCode, description), CREATE_PROJECT_NOTICE);
        addListModel(model, status, q);
        return HtmxRequest.isFragment(request) ? "projects/panel :: panel" : "projects/list";
    }

    /**
     * The project detail: one call for the full record, one for the work packages, one for
     * the tasks — grouped here into the plan tree — plus the three sections of the open
     * project. The tree's composition is deliberate: the API carries the membership on
     * every task line, so no per-task call is needed.
     */
    @GetMapping("/{reference}")
    public String detail(@PathVariable String reference, Model model, HttpServletRequest request) {
        ProjectRecord project = projects.findProject(reference);
        model.addAttribute(REFERENCE, project.shortCode());
        model.addAttribute("project", ProjectsViews.projectHeader(project));
        addTree(model, project.shortCode());
        addComments(model, parent(project.shortCode()));
        addActivity(model, parent(project.shortCode()));
        addAttachments(model, parent(project.shortCode()));
        // An htmx detail load replaces the working area only; a plain request renders the
        // page inside the shell.
        return HtmxRequest.isFragment(request) ? "projects/detail :: content" : "projects/detail";
    }

    /** One of the three explicit lifecycle moves; the whole detail is re-read afterwards. */
    @PostMapping("/{reference}/deactivate")
    public String deactivate(@PathVariable String reference, Model model, HttpServletRequest request) {
        write(() -> projects.transitionProject(reference, ProjectTransition.deactivate), PROJECT_NOTICE);
        return detail(reference, model, request);
    }

    /** Brings a deactivated project back into the normal view. */
    @PostMapping("/{reference}/activate")
    public String activate(@PathVariable String reference, Model model, HttpServletRequest request) {
        write(() -> projects.transitionProject(reference, ProjectTransition.activate), PROJECT_NOTICE);
        return detail(reference, model, request);
    }

    /**
     * Finishes the project: the API turns the whole subtree read-only, so every write the
     * page still offers answers 409 and shows that answer inline.
     */
    @PostMapping("/{reference}/archive")
    public String archive(@PathVariable String reference, Model model, HttpServletRequest request) {
        write(() -> projects.transitionProject(reference, ProjectTransition.archive), PROJECT_NOTICE);
        return detail(reference, model, request);
    }

    /** Creates a work package; the plan card comes back with the API's fresh order. */
    @PostMapping("/{reference}/work-packages")
    public String createWorkPackage(@PathVariable String reference, @RequestParam String title,
            @RequestParam(name = "assignee_sub", required = false) String assigneeSub,
            @RequestParam(name = "due_date", required = false) String dueDate,
            Model model) {
        write(() -> projects.createWorkPackage(reference, title, assigneeSub, dueDate), WORK_PACKAGE_NOTICE);
        return plan(reference, model);
    }

    /** Creates a task of the project, optionally under one of its work packages. */
    @PostMapping("/{reference}/tasks")
    public String createTask(@PathVariable String reference, @RequestParam String title,
            @RequestParam(name = "work_package_id", required = false) String workPackageId,
            @RequestParam(name = "assignee_sub", required = false) String assigneeSub,
            @RequestParam(name = "due_date", required = false) String dueDate,
            Model model) {
        write(() -> projects.createTask(reference, title, workPackageId, assigneeSub, dueDate), TASK_NOTICE);
        return plan(reference, model);
    }

    /**
     * Inline update of one work package. Each control of a plan row is its own form, so a
     * request carries exactly the field the reader touched: absent means unchanged, an
     * empty assignee or due date clears, and {@code sort_order} moves the item. The project
     * reference in the path only keeps the link self-describing — the item reference
     * addresses the record, and the API's own guards decide about the write.
     */
    @PatchMapping("/{reference}/work-packages/{itemReference}")
    public String patchWorkPackage(@PathVariable String reference, @PathVariable String itemReference,
            @RequestParam(required = false) String status,
            @RequestParam(name = "assignee_sub", required = false) String assigneeSub,
            @RequestParam(name = "due_date", required = false) String dueDate,
            @RequestParam(name = "sort_order", required = false) Integer sortOrder,
            Model model) {
        write(() -> projects.patchWorkPackage(itemReference,
                new ItemPatch(status, assigneeSub, dueDate, sortOrder)), ITEM_NOTICE + itemReference);
        return plan(reference, model);
    }

    /** Inline update of one task — the same four fields, the same tri-state. */
    @PatchMapping("/{reference}/tasks/{itemReference}")
    public String patchTask(@PathVariable String reference, @PathVariable String itemReference,
            @RequestParam(required = false) String status,
            @RequestParam(name = "assignee_sub", required = false) String assigneeSub,
            @RequestParam(name = "due_date", required = false) String dueDate,
            @RequestParam(name = "sort_order", required = false) Integer sortOrder,
            Model model) {
        write(() -> projects.patchTask(itemReference, new ItemPatch(status, assigneeSub, dueDate, sortOrder)),
                ITEM_NOTICE + itemReference);
        return plan(reference, model);
    }

    /** Adds a comment; the section comes back with the API's fresh, newest-first list. */
    @PostMapping("/{reference}/comments")
    public String createComment(@PathVariable String reference, @RequestParam String text, Model model) {
        write(() -> projects.createComment(parent(reference), text), COMMENTS_NOTICE);
        model.addAttribute(REFERENCE, reference);
        addComments(model, parent(reference));
        return "projects/comments :: section";
    }

    /** The comment section on its own, newest first as the API delivers it. */
    @GetMapping("/{reference}/comments")
    public String comments(@PathVariable String reference, Model model) {
        model.addAttribute(REFERENCE, reference);
        addComments(model, parent(reference));
        return "projects/comments :: section";
    }

    /** The activity section on its own, newest entries first as the API delivers them. */
    @GetMapping("/{reference}/activity")
    public String activity(@PathVariable String reference, Model model) {
        model.addAttribute(REFERENCE, reference);
        addActivity(model, parent(reference));
        return "projects/activity :: section";
    }

    /** The attachment metadata section on its own. */
    @GetMapping("/{reference}/attachments")
    public String attachments(@PathVariable String reference, Model model) {
        model.addAttribute(REFERENCE, reference);
        addAttachments(model, parent(reference));
        return "projects/attachments :: section";
    }

    /**
     * The download proxy: the browser asks the dashboard, the dashboard asks the backend
     * with the session's Bearer token and passes bytes, content type and ETag through.
     * The upstream response headers are forwarded unchanged — an ETag is the backend's
     * integrity claim, not the dashboard's.
     *
     * <p>The project reference in the path only keeps the link self-describing; the
     * attachment id addresses the file, and the backend's token checks guard it.
     */
    @GetMapping("/{reference}/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> attachmentContent(@PathVariable UUID attachmentId) {
        ProjectsClient.AttachmentContent content = projects.downloadAttachment(attachmentId);
        byte[] bytes = content.bytes() == null ? new byte[0] : content.bytes();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType(content.contentType()))
                .contentLength(bytes.length);
        if (content.etag() != null) {
            response.header(HttpHeaders.ETAG, content.etag());
        }
        if (content.contentDisposition() != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, content.contentDisposition());
        }
        return response.body(bytes);
    }

    private void addListModel(Model model, String status, String query) {
        model.addAttribute("projects",
                projects.findProjects(status, query).stream().map(ProjectsViews::projectRow).toList());
        model.addAttribute("statusFilter", status == null ? "" : status);
        model.addAttribute("queryFilter", query == null ? "" : query);
        model.addAttribute("statusOptions", ProjectsViews.PROJECT_STATUS_OPTIONS);
    }

    /**
     * The plan card of one project: the tree and the two create forms. Both the item edits
     * and the creates answer with it, because the plan order is the API's — re-reading is
     * what shows a reposition.
     */
    private String plan(String reference, Model model) {
        model.addAttribute(REFERENCE, reference);
        addTree(model, reference);
        return "projects/tree :: card";
    }

    private void addTree(Model model, String projectCode) {
        model.addAttribute("tree", ProjectsViews.tree(projects.findWorkPackages(projectCode),
                projects.findTasks(projectCode)));
        model.addAttribute("itemStatusOptions", ProjectsViews.ITEM_STATUS_OPTIONS);
    }

    private void addComments(Model model, String parent) {
        List<ProjectsViews.CommentRow> comments = ProjectsViews.commentRows(projects.findComments(parent));
        model.addAttribute("comments", comments);
    }

    private void addActivity(Model model, String parent) {
        List<ProjectsViews.ActivityRow> activity = ProjectsViews.activityRows(projects.findActivities(parent));
        model.addAttribute("activity", activity);
    }

    private void addAttachments(Model model, String parent) {
        List<ProjectsViews.AttachmentRow> attachments = ProjectsViews
                .attachmentRows(projects.findAttachments(parent));
        model.addAttribute("attachments", attachments);
    }

    /**
     * Runs one mutation and turns the API's two inline-answerable rejections into a notice
     * at {@code retarget}. Everything else keeps the module's page-level error handling —
     * a rejected token still sends the reader back into the login round-trip.
     */
    private static void write(Runnable mutation, String retarget) {
        try {
            mutation.run();
        }
        catch (RestClientResponseException rejected) {
            int status = rejected.getStatusCode().value();
            if (status != HttpStatus.BAD_REQUEST.value() && status != HttpStatus.CONFLICT.value()) {
                throw rejected;
            }
            throw new ProjectsRejection(status, rejected.getResponseBodyAsString(StandardCharsets.UTF_8),
                    retarget);
        }
    }

    /**
     * The typed parent form of the comment, activity and attachment endpoints
     * ({@code project:CODE}) — the only form that is unambiguous for every identifier.
     */
    private static String parent(String projectReference) {
        return "project:" + projectReference;
    }

    private static MediaType mediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        }
        catch (InvalidMediaTypeException unusable) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
