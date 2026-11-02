package de.homelabs.dashboard.projects;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import de.homelabs.dashboard.shell.AccessTokenProvider;
import de.homelabs.dashboard.shell.ModuleRegistry;

/**
 * The module's single door to the projects REST API: one thin client, one base URL, one
 * rule — every call forwards the session's access token as Bearer.
 *
 * <p>Consumed endpoints are exactly the read side the views need: the lean project list,
 * the full project record, the lean work-package and task lists, comments, activity,
 * attachment metadata and one attachment's bytes. The dashboard consumes that contract
 * and never forks it; shapes the views need beyond it belong to the backend.
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

    private static Optional<String> present(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /** Bytes of one attachment with the response metadata the proxy forwards unchanged. */
    record AttachmentContent(byte[] bytes, String contentType, String etag, String contentDisposition) {
    }
}
