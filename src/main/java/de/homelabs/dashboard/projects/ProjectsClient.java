package de.homelabs.dashboard.projects;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import de.homelabs.dashboard.shell.AccessTokenProvider;
import de.homelabs.dashboard.shell.ModuleRegistry;

/**
 * The module's single door to the projects REST API: one thin client, one base URL, one
 * rule — every call forwards the session's access token as Bearer.
 *
 * <p>Consumed endpoints are the read side the views need — the lean project list, the
 * full project record, the full work-package and task records, the lean work-package and
 * task lists, comments, activity, attachment metadata and one attachment's bytes — plus
 * the write routes behind the same resources: create, the three explicit lifecycle moves,
 * partial updates and the comment form. The dashboard consumes that contract and never
 * forks it; shapes the views need beyond it belong to the backend.
 *
 * <p>Write bodies name their wire fields explicitly, like the read records, because the
 * API drops an unknown property silently: a misspelled field would be a no-op, not an
 * error. {@code null} means "leave unchanged" on every partial update, an empty string
 * clears a nullable field, and {@link ItemPatch}'s absent fields stay absent — the
 * API's tri-state, carried by the browser's per-field forms.
 *
 * <p>The token is read per call (not at construction): the session can end between two
 * requests, and the interceptor is the one place where that decision is made.
 *
 * <p>List windows are the API's own defaults/maxima — {@value #ITEM_LIMIT} for the lean
 * item and project lists, {@value #ACTIVITY_LIMIT} for comments, activity and
 * attachments. A larger project therefore shows its first {@value #ITEM_LIMIT} items
 * until paging exists; truncation is the API's rule, not a silent cut here.
 */
@Component
public class ProjectsClient {

    /** Registry id of the module this client belongs to. */
    static final String MODULE_ID = "projects";

    /** The API clamps list answers to 100 rows. */
    static final int ITEM_LIMIT = 100;

    /** The API's default activity/comment window. */
    static final int ACTIVITY_LIMIT = 50;

    /** Element types of the list answers — the wire records, in their wire order. */
    private static final ParameterizedTypeReference<List<ProjectSummary>> PROJECT_SUMMARIES =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ItemSummary>> ITEM_SUMMARIES =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<CommentRecord>> COMMENTS =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<ActivityRecord>> ACTIVITIES =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<AttachmentRecord>> ATTACHMENTS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    ProjectsClient(ModuleRegistry registry, AccessTokenProvider accessTokens) {
        URI apiBaseUrl = registry.find(MODULE_ID)
                .orElseThrow(() -> new IllegalStateException("module '" + MODULE_ID + "' is not registered"))
                .apiBaseUrl();
        this.http = RestClient.builder()
                .baseUrl(apiBaseUrl.toString())
                .requestInterceptor((request, body, execution) -> {
                    String accessToken = accessTokens.currentAccessToken();
                    if (accessToken != null) {
                        request.getHeaders().setBearerAuth(accessToken);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * The lean project list. {@code status} and {@code query} are the REST filters and
     * stay optional: without a status the API answers its default view (active projects
     * only), so the dashboard never filters locally.
     */
    List<ProjectSummary> findProjects(String status, String query) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/projects")
                        .queryParam("limit", ITEM_LIMIT)
                        .queryParamIfPresent("status", present(status))
                        .queryParamIfPresent("q", present(query))
                        .build()), PROJECT_SUMMARIES);
    }

    /** The full project record, canonical HTML included. */
    ProjectRecord findProject(String reference) {
        return http.get()
                .uri(uri -> uri.path("/api/projects/{reference}").build(reference))
                .retrieve()
                .body(ProjectRecord.class);
    }

    /**
     * The project's work packages in the API's plan order (lowest sort order first).
     * {@code include_inactive_projects} keeps the tree visible for a deactivated or
     * archived project — the detail view mirrors the backend's state, it does not hide
     * the subtree.
     */
    List<ItemSummary> findWorkPackages(String projectCode) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/work-packages")
                        .queryParam("project", projectCode)
                        .queryParam("include_inactive_projects", true)
                        .queryParam("limit", ITEM_LIMIT)
                        .build()), ITEM_SUMMARIES);
    }

    /** The full record of one work package, canonical HTML and labels included. */
    WorkPackageRecord findWorkPackage(String reference) {
        return http.get()
                .uri(uri -> uri.path("/api/work-packages/{reference}").build(reference))
                .retrieve()
                .body(WorkPackageRecord.class);
    }

    /** The full record of one task, its work-package membership included. */
    TaskRecord findTask(String reference) {
        return http.get()
                .uri(uri -> uri.path("/api/tasks/{reference}").build(reference))
                .retrieve()
                .body(TaskRecord.class);
    }

    /** The project's tasks in the same plan order, each carrying its package membership. */
    List<ItemSummary> findTasks(String projectCode) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/tasks")
                        .queryParam("project", projectCode)
                        .queryParam("include_inactive_projects", true)
                        .queryParam("limit", ITEM_LIMIT)
                        .build()), ITEM_SUMMARIES);
    }

    /** Comments of one entity, newest first. {@code parent} carries the typed reference. */
    List<CommentRecord> findComments(String parent) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/comments")
                        .queryParam("parent", parent)
                        .queryParam("limit", ACTIVITY_LIMIT)
                        .build()), COMMENTS);
    }

    /** The entity's activity log, newest first. */
    List<ActivityRecord> findActivities(String parent) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/activities")
                        .queryParam("parent", parent)
                        .queryParam("limit", ACTIVITY_LIMIT)
                        .build()), ACTIVITIES);
    }

    /** Attachment metadata of one entity, newest first — never the bytes. */
    List<AttachmentRecord> findAttachments(String parent) {
        return listOf(http.get()
                .uri(uri -> uri.path("/api/attachments")
                        .queryParam("parent", parent)
                        .queryParam("limit", ACTIVITY_LIMIT)
                        .build()), ATTACHMENTS);
    }

    /**
     * One attachment's bytes with their stored content type and ETag. The browser only
     * talks to the dashboard, so this call is the download proxy's server-side half.
     */
    AttachmentContent downloadAttachment(UUID attachmentId) {
        ResponseEntity<byte[]> response = http.get()
                .uri(uri -> uri.path("/api/attachments/{id}/content").build(attachmentId))
                .retrieve()
                .toEntity(byte[].class);
        HttpHeaders headers = response.getHeaders();
        return new AttachmentContent(response.getBody(), headers.getFirst(HttpHeaders.CONTENT_TYPE),
                headers.getFirst(HttpHeaders.ETAG), headers.getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    private static <T> List<T> listOf(RestClient.RequestHeadersSpec<?> request,
            ParameterizedTypeReference<List<T>> type) {
        List<T> body = request.retrieve().body(type);
        // An empty list is an answer; the null guard keeps a bodiless 200 from becoming
        // an NPE three templates later.
        return body == null ? List.of() : body;
    }

    /** Creates a project; an absent short code is derived from the name by the API. */
    void createProject(String name, String shortCode, String description) {
        http.post()
                .uri(uri -> uri.path("/api/projects").build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProjectCreate(name, blankToNull(shortCode), blankToNull(description)))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * One of the explicit lifecycle moves. Status is never a PATCH field of the API:
     * {@code deactivate}, {@code activate} and {@code archive} are their own operations,
     * and {@code archived} answers 409 to every later write of the subtree.
     */
    void transitionProject(String reference, ProjectTransition transition) {
        http.post()
                .uri(uri -> uri.path("/api/projects/{reference}/{transition}")
                        .build(reference, transition.name()))
                .retrieve()
                .toBodilessEntity();
    }

    /** Creates a work package inside the project; it takes the project's next reference. */
    void createWorkPackage(String projectReference, String title, String assigneeSub, String dueDate) {
        http.post()
                .uri(uri -> uri.path("/api/projects/{reference}/work-packages").build(projectReference))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WorkPackageCreate(title, blankToNull(assigneeSub), blankToNull(dueDate)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Creates a task of the project, optionally grouped into one of its work packages. */
    void createTask(String projectReference, String title, String workPackageReference, String assigneeSub,
            String dueDate) {
        http.post()
                .uri(uri -> uri.path("/api/tasks").build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TaskCreate(title, projectReference, blankToNull(workPackageReference),
                        blankToNull(assigneeSub), blankToNull(dueDate)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Partial update of one work package; {@code null} fields stay unchanged. */
    void patchWorkPackage(String reference, ItemPatch patch) {
        patch("/api/work-packages/{reference}", reference, patch);
    }

    /** Partial update of one task; {@code null} fields stay unchanged. */
    void patchTask(String reference, ItemPatch patch) {
        patch("/api/tasks/{reference}", reference, patch);
    }

    /** Adds a plain-text comment to the entity the typed {@code parent} names. */
    void createComment(String parent, String text) {
        http.post()
                .uri(uri -> uri.path("/api/comments").build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CommentCreate(parent, text))
                .retrieve()
                .toBodilessEntity();
    }

    private void patch(String path, String reference, ItemPatch patch) {
        http.patch()
                .uri(uri -> uri.path(path).build(reference))
                .contentType(MediaType.APPLICATION_JSON)
                .body(patch)
                .retrieve()
                .toBodilessEntity();
    }

    private static Optional<String> present(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /**
     * An empty form field on a create means "not set", exactly like an absent one — the
     * API trims it to nothing either way, so the body leaves it out instead of shipping a
     * blank. On a partial update the empty string is meaningful (it clears), so the
     * {@link ItemPatch} path passes it through untouched.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** The API's lifecycle moves; the constants are the path segments of the routes. */
    enum ProjectTransition {
        deactivate, activate, archive
    }

    /** Body of {@code POST /api/projects}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProjectCreate(
            @JsonProperty("name") String name,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("description") String description) {
    }

    /** Body of {@code POST /api/projects/{idOrCode}/work-packages}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WorkPackageCreate(
            @JsonProperty("title") String title,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate) {
    }

    /** Body of {@code POST /api/tasks}; the project is the body's, the grouping is optional. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TaskCreate(
            @JsonProperty("title") String title,
            @JsonProperty("project") String project,
            @JsonProperty("work_package_id") String workPackageId,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate) {
    }

    /** Body of {@code POST /api/comments}. */
    record CommentCreate(
            @JsonProperty("parent") String parent,
            @JsonProperty("text") String text) {
    }

    /**
     * Body of the two item PATCH routes. Only the fields the reader touched are sent:
     * {@code null} means "unchanged", while an empty {@code assignee_sub} or
     * {@code due_date} clears the field, and an empty {@code work_package_id} removes a
     * task's grouping. {@code sort_order} has no clear state — the column is not
     * nullable, so {@code null} always leaves it alone.
     *
     * <p>{@code description_html} is the ONLY content field the dashboard sends: the API
     * refuses a body that carries both shapes, and re-sending the derived plain text
     * would flatten an item written as markup. {@code labels} replaces the whole set.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ItemPatch(
            @JsonProperty("title") String title,
            @JsonProperty("description_html") String descriptionHtml,
            @JsonProperty("status") String status,
            @JsonProperty("assignee_sub") String assigneeSub,
            @JsonProperty("due_date") String dueDate,
            @JsonProperty("sort_order") Integer sortOrder,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("work_package_id") String workPackageId) {

        /** The four fields of a plan-row editor, as the row forms submit them. */
        static ItemPatch row(String status, String assigneeSub, String dueDate, Integer sortOrder) {
            return new ItemPatch(null, null, status, assigneeSub, dueDate, sortOrder, null, null);
        }

        static ItemPatch title(String title) {
            return new ItemPatch(title, null, null, null, null, null, null, null);
        }

        static ItemPatch descriptionHtml(String descriptionHtml) {
            return new ItemPatch(null, descriptionHtml, null, null, null, null, null, null);
        }

        static ItemPatch status(String status) {
            return new ItemPatch(null, null, status, null, null, null, null, null);
        }

        static ItemPatch assigneeSub(String assigneeSub) {
            return new ItemPatch(null, null, null, assigneeSub, null, null, null, null);
        }

        static ItemPatch dueDate(String dueDate) {
            return new ItemPatch(null, null, null, null, dueDate, null, null, null);
        }

        static ItemPatch sortOrder(Integer sortOrder) {
            return new ItemPatch(null, null, null, null, null, sortOrder, null, null);
        }

        static ItemPatch labels(List<String> labels) {
            return new ItemPatch(null, null, null, null, null, null, labels, null);
        }

        static ItemPatch workPackageId(String workPackageId) {
            return new ItemPatch(null, null, null, null, null, null, null, workPackageId);
        }
    }

    /** Bytes of one attachment with the response metadata the proxy forwards unchanged. */
    record AttachmentContent(byte[] bytes, String contentType, String etag, String contentDisposition) {
    }
}
