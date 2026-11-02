package de.homelabs.dashboard.projects;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The projects module's read views: the lean project list, the project detail with its
 * plan tree, and the comments, activity and attachments sections.
 *
 * <p>Every route answers twice — as a full page for a link or a reload, and as the
 * fragment an htmx request swaps in place. The fragment is the same template: the full
 * page wraps it in the shell layout, the htmx answer returns just the piece.
 *
 * <p>This module reads only. Status transitions, inline edits, create forms and the
 * comment form are a later step; nothing here writes to the backend.
 */
@Controller
@RequestMapping("/projects")
public class ProjectsController {

    /** Model attribute of the open project's reference (the section links build on it). */
    static final String REFERENCE = "reference";

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
     * The project detail: one call for the full record, one for the work packages, one for
     * the tasks — grouped here into the plan tree — plus the three sections of the open
     * project. The tree's composition is deliberate: the API carries the membership on
     * every task line, so no per-task call is needed.
     */
    @GetMapping("/{reference}")
    public String detail(@PathVariable String reference, Model model, HttpServletRequest request) {
        ProjectRecord project = projects.findProject(reference);
        String parent = parent(project.shortCode());
        model.addAttribute(REFERENCE, project.shortCode());
        model.addAttribute("project", ProjectsViews.projectHeader(project));
        model.addAttribute("tree", ProjectsViews.tree(projects.findWorkPackages(project.shortCode()),
                projects.findTasks(project.shortCode())));
        addComments(model, parent);
        addActivity(model, parent);
        addAttachments(model, parent);
        // An htmx detail load replaces the working area only; a plain request renders the
        // page inside the shell.
        return HtmxRequest.isFragment(request) ? "projects/detail :: content" : "projects/detail";
    }

    /** The comment section on its own — read-only until the comment form lands. */
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
