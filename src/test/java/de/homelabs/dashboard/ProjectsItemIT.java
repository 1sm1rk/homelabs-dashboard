package de.homelabs.dashboard;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.testsupport.FakeProjectsApi;
import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The item pages against the production login gate, the production CSRF protection and a
 * fake projects API: the full record with the project header and the breadcrumb above it,
 * the work package's task list in plan order, and one editor flow per field — each PATCH
 * asserted by its exact wire body, because the API drops a misspelled field silently.
 *
 * <p>Every mutation carries the token the LAYOUT rendered (parsed out of the body's
 * {@code hx-headers} and sent as the header htmx would send), on one shared session — the
 * production path, not a test-only bypass.
 */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class ProjectsItemIT {

    private static final FakeProjectsApi API = new FakeProjectsApi();

    private static final String TOKEN = "session-access-token";

    private static final String ISSUER = "https://auth.example.test/realms/homelabs";

    private static final String SUBJECT = "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90";

    private static final String PROJECT_ID = "1b3f6d2e-5f7a-4c8b-9d10-2e3f4a5b6c7d";

    private static final String PACKAGE_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private static final String OTHER_PACKAGE_ID = "9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a";

    private static final String TASK_ID = "5d6e7f80-9a1b-4c2d-8e3f-0a1b2c3d4e5f";

    private static final String LABEL_ID = "b1e0d6b9-3c5a-4d2e-9f10-6a7b8c9d0e1f";

    /** The body-level header block of the layout: header name and token. */
    private static final Pattern CSRF_HEADERS =
            Pattern.compile("<body[^>]*hx-headers=\"\\{&quot;([^&]+)&quot;:&quot;([^&]+)&quot;\\}\"");

    @DynamicPropertySource
    static void projectsApiUrl(DynamicPropertyRegistry registry) {
        registry.add("homelabs.dashboard.modules.projects.api-url", API::baseUrl);
    }

    @AfterAll
    static void stopApi() {
        API.close();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClients;

    @Autowired
    private ClientRegistrationRepository clientRegistrations;

    private MockMvc mockMvc;

    /** One browser session: the CSRF token lives in it, like in the running application. */
    private MockHttpSession session;

    private String csrfHeader;

    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        API.reset();
        session = new MockHttpSession();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        csrfToken = renderedCsrfToken();
    }

    @Test
    void rendersTheWorkPackagePageWithItsFullRecordHeaderBreadcrumbAndTasks() throws Exception {
        stubWorkPackagePage();

        mockMvc.perform(authenticated(get("/projects/WEB/work-packages/WEB-1")))
                .andExpect(status().isOk())
                // The shared project header: reference, state, name, who and when.
                .andExpect(content().string(containsString("class=\"mono muted\">WEB</span>")))
                .andExpect(content().string(containsString("badge badge--active")))
                .andExpect(content().string(containsString("<h1 class=\"page__title\">homelabs dashboard</h1>")))
                .andExpect(content().string(containsString("Erstellt von")))
                .andExpect(content().string(containsString("Zuletzt geändert")))
                // The trail above the content, every crumb leading back to the project.
                .andExpect(content().string(containsString("class=\"breadcrumb\"")))
                .andExpect(content().string(containsString("href=\"/projects/WEB\"")))
                .andExpect(content().string(containsString("breadcrumb__item--current\">WEB-1<")))
                // The full record.
                .andExpect(content().string(containsString("id=\"item\"")))
                .andExpect(content().string(containsString("Package one")))
                .andExpect(content().string(containsString("<p>package description</p>")))
                .andExpect(content().string(containsString(">" + SUBJECT + "</span>")))
                .andExpect(content().string(containsString(">di.mueller</span>")))
                .andExpect(content().string(containsString(">2026-11-20</time>")))
                .andExpect(content().string(containsString(">1</dd>")))
                .andExpect(content().string(containsString(">2</dd>")))
                .andExpect(content().string(containsString("<span class=\"chip\">designed</span>")))
                // One form per field, each posting to its own route.
                .andExpect(content().string(containsString(
                        "hx-patch=\"/projects/WEB/work-packages/WEB-1/title\"")))
                .andExpect(content().string(containsString(
                        "hx-patch=\"/projects/WEB/work-packages/WEB-1/description\"")))
                .andExpect(content().string(containsString(
                        "hx-patch=\"/projects/WEB/work-packages/WEB-1/labels\"")))
                .andExpect(content().string(containsString(
                        "hx-patch=\"/projects/WEB/work-packages/WEB-1/status\"")))
                .andExpect(content().string(containsString("name=\"description_html\"")))
                .andExpect(content().string(containsString("id=\"item-edit-notice-title\"")))
                // A work package carries no grouping, so it offers no select for one.
                .andExpect(content().string(not(containsString("name=\"work_package_id\""))))
                // Its own tasks in the plan order, linking into their pages; a task of
                // another package stays out of this list.
                .andExpect(content().string(containsString("id=\"item-tasks\"")))
                .andExpect(content().string(containsString("href=\"/projects/WEB/tasks/WEB-2\"")))
                .andExpect(content().string(not(containsString("Loose task"))));

        assertThat(API.calls())
                .as("one item page: the project record, the item record and the plan's task list")
                .hasSize(3)
                .allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + TOKEN));
    }

    @Test
    void rendersTheTaskPageWithItsWorkPackageCrumbAndTheMoveSelect() throws Exception {
        stubTaskPage();

        mockMvc.perform(authenticated(get("/projects/WEB/tasks/WEB-2")))
                .andExpect(status().isOk())
                // The trail names the work package of the task and still leads back to the project.
                .andExpect(content().string(containsString(">WEB-1</a>")))
                .andExpect(content().string(containsString("breadcrumb__item--current\">WEB-2<")))
                .andExpect(content().string(containsString("<p>task description</p>")))
                // The move select: the project's packages plus the explicit ungrouped choice.
                .andExpect(content().string(containsString("name=\"work_package_id\"")))
                .andExpect(content().string(containsString("<option value=\"\">Ohne Arbeitspaket</option>")))
                .andExpect(content().string(containsString("selected=\"selected\">WEB-1 · Package one</option>")))
                .andExpect(content().string(containsString("WEB-4 · Package two")))
                .andExpect(content().string(containsString(
                        "hx-patch=\"/projects/WEB/tasks/WEB-2/work-package\"")))
                .andExpect(content().string(containsString("hx-patch=\"/projects/WEB/tasks/WEB-2/status\"")))
                // Only a work package page composes a task list.
                .andExpect(content().string(not(containsString("id=\"item-tasks\""))));
    }

    @Test
    void answersAHandMadeUrlForAnItemOfAnotherProjectWithTheNotFoundNotice() throws Exception {
        // A task of another project, addressed under WEB's path: the API answers its own
        // record, the page composition refuses the mix instead of rendering one project's
        // header over another project's task.
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/tasks/WEB-2", 200, task("in_progress", "OPS"));

        mockMvc.perform(authenticated(get("/projects/WEB/tasks/WEB-2")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dieser Eintrag gibt es nicht (mehr).")))
                .andExpect(content().string(not(containsString("<p>task description</p>"))));

        assertThat(API.calls()).as("the mismatch stops after the two reads")
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + TOKEN));
    }

    @Test
    void rendersTheProjectHeaderOnTheProjectPageAndBothItemPages() throws Exception {
        stubDetail();
        stubWorkPackagePage();
        stubTaskPage();

        for (String path : List.of("/projects/WEB", "/projects/WEB/work-packages/WEB-1",
                "/projects/WEB/tasks/WEB-2")) {
            mockMvc.perform(authenticated(get(path)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("homelabs dashboard")))
                    .andExpect(content().string(containsString("Erstellt von")))
                    .andExpect(content().string(containsString("Zuletzt geändert")))
                    .andExpect(content().string(containsString("hx-post=\"/projects/WEB/deactivate\"")))
                    .andExpect(content().string(containsString("id=\"project-notice\"")));
        }
    }

    @Test
    void rejectsAnEditorMutationWithoutTheRenderedCsrfToken() throws Exception {
        mockMvc.perform(patch("/projects/WEB/work-packages/WEB-1/title").session(session)
                        .with(authentication(login())).locale(Locale.GERMANY))
                .andExpect(status().isForbidden());

        assertThat(API.calls())
                .as("the security chain answers before the module reaches the backend")
                .isEmpty();
    }

    @Test
    void editsTheTitleAndRerendersThePageFromAFreshRead() throws Exception {
        stubWorkPackagePage();
        API.queue("GET", "/api/work-packages/WEB-1", 200, workPackage("todo", "Renamed package"));
        API.json("PATCH", "/api/work-packages/WEB-1", 200, workPackage("todo", "Renamed package"));

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/title", "title", "Renamed package")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Renamed package")))
                .andExpect(content().string(containsString("id=\"item\"")))
                .andExpect(content().string(not(containsString("<html"))));

        FakeProjectsApi.Call patch = API.onlyCall("PATCH", "/api/work-packages/WEB-1");
        assertThat(patch.body())
                .as("the title editor sends the title alone")
                .isEqualTo("{\"title\":\"Renamed package\"}");
        assertThat(patch.authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void skipsTheWriteWhenTheTitleDidNotChange() throws Exception {
        stubWorkPackagePage();

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/title", "title", "Package one")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"item\"")));

        assertThat(API.callsTo("PATCH", "/api/work-packages/WEB-1"))
                .as("an unchanged save is not a write — the API answers 'nothing to change' to a no-op")
                .isEmpty();
    }

    @Test
    void sendsTheDescriptionAsHtmlAloneAndEmptiesTheContentWithAnEmptyTextarea() throws Exception {
        stubWorkPackagePage();
        API.queue("GET", "/api/work-packages/WEB-1", 200, workPackage("todo", "Package one", "<p>new text</p>"));
        API.json("PATCH", "/api/work-packages/WEB-1", 200, workPackage("todo", "Package one", "<p>new text</p>"));

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/description",
                        "description_html", "<p>new text</p>")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<p>new text</p>")));

        FakeProjectsApi.Call first = API.callsTo("PATCH", "/api/work-packages/WEB-1").getFirst();
        assertThat(first.body())
                .as("the canonical HTML alone: sending both shapes would be a 400")
                .isEqualTo("{\"description_html\":\"<p>new text</p>\"}");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/description",
                        "description_html", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());

        assertThat(API.callsTo("PATCH", "/api/work-packages/WEB-1").get(1).body())
                .as("an empty textarea empties the content instead of being skipped")
                .isEqualTo("{\"description_html\":\"\"}");
    }

    @Test
    void replacesTheWholeLabelSetFromTheCommaInput() throws Exception {
        stubWorkPackagePage();
        API.queue("GET", "/api/work-packages/WEB-1", 200, workPackage("todo", "Package one"));

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/labels",
                        "labels", "designed, frontend")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/labels", "labels", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());

        List<FakeProjectsApi.Call> patches = API.callsTo("PATCH", "/api/work-packages/WEB-1");
        assertThat(patches.get(0).body())
                .as("a comma list becomes the API's list, trimmed")
                .isEqualTo("{\"labels\":[\"designed\",\"frontend\"]}");
        assertThat(patches.get(1).body())
                .as("an empty input is an empty set — the labels go")
                .isEqualTo("{\"labels\":[]}");
    }

    @Test
    void editsTheFourRowFieldsOnTheItemPageWithTheWireNames() throws Exception {
        stubWorkPackagePage();
        API.json("PATCH", "/api/work-packages/WEB-1", 200, workPackage("done", "Package one"));

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/status", "status", "done")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"item\"")));
        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/assignee", "assignee_sub", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/due-date", "due_date", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/sort-order", "sort_order", "4")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());

        List<FakeProjectsApi.Call> patches = API.callsTo("PATCH", "/api/work-packages/WEB-1");
        assertThat(patches).extracting(FakeProjectsApi.Call::body).containsExactly(
                "{\"status\":\"done\"}",
                // An emptied field is the API's "clear", not "unchanged": the form sends "".
                "{\"assignee_sub\":\"\"}",
                "{\"due_date\":\"\"}",
                "{\"sort_order\":4}");
    }

    @Test
    void movesATaskBetweenPackagesAndUngroupsItWithTheEmptyChoice() throws Exception {
        stubTaskPage();

        mockMvc.perform(formPatch("/projects/WEB/tasks/WEB-2/work-package",
                        "work_package_id", "WEB-4")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(formPatch("/projects/WEB/tasks/WEB-2/work-package",
                        "work_package_id", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());

        List<FakeProjectsApi.Call> patches = API.callsTo("PATCH", "/api/tasks/WEB-2");
        assertThat(patches.get(0).body())
                .as("a package reference moves the task into it")
                .isEqualTo("{\"work_package_id\":\"WEB-4\"}");
        assertThat(patches.get(1).body())
                .as("the empty reference is the API's ungroup token, not an absent field")
                .isEqualTo("{\"work_package_id\":\"\"}");
    }

    @Test
    void rendersAValidationMessageWithTheWireFieldNameAtTheEditor() throws Exception {
        API.json("PATCH", "/api/work-packages/WEB-1", 400, "{\"error\":\"sort_order: must be at least 1\"}");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/sort-order", "sort_order", "0")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#item-edit-notice-sort-order"))
                .andExpect(header().string("HX-Reswap", "innerHTML"))
                .andExpect(content().string(containsString("<code class=\"problem__field\">sort_order</code>")))
                .andExpect(content().string(containsString("must be at least 1")))
                .andExpect(content().string(not(containsString("id=\"item\""))));
    }

    @Test
    void rendersTheArchivedConflictInlineAtTheEditorThatCausedIt() throws Exception {
        API.json("PATCH", "/api/work-packages/WEB-1", 409,
                "{\"error\":\"project WEB is archived: the whole project is read-only, writes are rejected\"}");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1/status", "status", "done")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#item-edit-notice-status"))
                .andExpect(content().string(containsString("the whole project is read-only")))
                .andExpect(content().string(not(containsString("id=\"item\""))));
    }

    @Test
    void surfacesAnUnknownItemAsTheLocalizedNotice() throws Exception {
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/work-packages/WEB-9", 404, "{\"error\":\"no work package WEB-9\"}");

        mockMvc.perform(authenticated(get("/projects/WEB/work-packages/WEB-9")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dieser Eintrag gibt es nicht")))
                .andExpect(content().string(containsString("no work package WEB-9")));
    }

    /** Every answer of a work-package page: the project, the item and the project's tasks. */
    private void stubWorkPackagePage() {
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/work-packages/WEB-1", 200, workPackage("todo", "Package one"));
        API.json("GET", "/api/tasks", 200, "[" + leanTask("WEB-2", "A task", PACKAGE_ID, 3) + ","
                + leanTask("WEB-3", "Loose task", null, 4) + "]");
    }

    /** Every answer of a task page: the project, the task and the project's packages. */
    private void stubTaskPage() {
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/tasks/WEB-2", 200, task("in_progress"));
        API.json("GET", "/api/work-packages", 200,
                "[" + leanPackage("WEB-1", "Package one", 1) + "," + leanPackage("WEB-4", "Package two", 2) + "]");
    }

    /** The project detail's six answers, so the header test can load the page. */
    private void stubDetail() {
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/work-packages", 200, "[" + leanPackage("WEB-1", "Package one", 1) + "]");
        API.json("GET", "/api/tasks", 200, "[" + leanTask("WEB-2", "A task", PACKAGE_ID, 3) + "]");
        API.json("GET", "/api/comments", 200, "[]");
        API.json("GET", "/api/activities", 200, "[]");
        API.json("GET", "/api/attachments", 200, "[]");
    }

    /** The layout's token, read from the page exactly as htmx would read it. */
    private String renderedCsrfToken() throws Exception {
        String html = mockMvc.perform(authenticated(get("/")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher headers = CSRF_HEADERS.matcher(html);
        assertThat(headers.find()).as("the layout renders the token as body-level hx-headers").isTrue();
        csrfHeader = headers.group(1);
        return headers.group(2);
    }

    /**
     * A form-encoded PATCH, the way htmx sends it: the body is the parameter source, which
     * is what the request's form-content filter has to make readable for the controller.
     */
    private MockHttpServletRequestBuilder formPatch(String path, String name, String value) {
        String form = URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        return authenticated(patch(path)).contentType(MediaType.APPLICATION_FORM_URLENCODED).content(form);
    }

    /** One request with this test's session, login, CSRF token and the German chrome. */
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        OAuth2AuthenticationToken authentication = login();
        ClientRegistration registration = clientRegistrations
                .findByRegistrationId(StubClientRegistrationRepository.REGISTRATION_ID);
        authorizedClients.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, authentication.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, Instant.now(),
                        Instant.now().plus(Duration.ofMinutes(10)))),
                authentication);
        return request.session(session)
                .header(csrfHeader == null ? "X-CSRF-TOKEN" : csrfHeader, csrfToken)
                .locale(Locale.GERMANY)
                .with(authentication(authentication));
    }

    private static OAuth2AuthenticationToken login() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of("dashboard"));
        claims.put("sub", SUBJECT);
        claims.put("preferred_username", "di.mueller");

        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), claims);
        OidcUser oidcUser = new DefaultOidcUser(
                List.of(new OidcUserAuthority(idToken, OidcUserInfo.builder().subject(SUBJECT).build())), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(),
                StubClientRegistrationRepository.REGISTRATION_ID);
    }

    private static String project(String status) {
        return """
                {"id":"%s","short_code":"WEB","name":"homelabs dashboard","status":"%s",
                 "description_html":"<p>canonical description</p>","description_text":"canonical description",
                 "owner_sub":"%s","owner_name":"di.mueller","created_at":"2026-11-02T10:00:00Z",
                 "updated_at":"2026-11-03T09:00:00Z",
                 "labels":[{"id":"%s","name":"designed","color":null}]}
                """.formatted(PROJECT_ID, status, SUBJECT, LABEL_ID);
    }

    private static String workPackage(String status, String title) {
        return workPackage(status, title, "<p>package description</p>");
    }

    private static String workPackage(String status, String title, String descriptionHtml) {
        return """
                {"id":"%s","reference":"WEB-1","project_id":"%s","project_short_code":"WEB",
                 "title":"%s","description_html":"%s","description_text":"package description",
                 "status":"%s","assignee_sub":"%s","assignee_name":"di.mueller","due_date":"2026-11-20",
                 "seq":1,"sort_order":2,"created_at":"2026-11-02T10:00:00Z",
                 "updated_at":"2026-11-03T09:00:00Z",
                 "labels":[{"id":"%s","name":"designed","color":null}]}
                """.formatted(PACKAGE_ID, PROJECT_ID, title, descriptionHtml, status, SUBJECT, LABEL_ID);
    }

    private static String task(String status) {
        return task(status, "WEB");
    }

    private static String task(String status, String projectShortCode) {
        return """
                {"id":"%s","reference":"WEB-2","project_id":"%s","project_short_code":"%s",
                 "work_package_id":"%s","work_package_reference":"WEB-1",
                 "title":"A task","description_html":"<p>task description</p>",
                 "description_text":"task description","status":"%s",
                 "assignee_sub":null,"assignee_name":null,"due_date":null,"seq":2,"sort_order":3,
                 "created_at":"2026-11-02T10:00:00Z","updated_at":"2026-11-03T09:00:00Z","labels":[]}
                """.formatted(TASK_ID, PROJECT_ID, projectShortCode, PACKAGE_ID, status);
    }

    private static String leanPackage(String reference, String title, int sortOrder) {
        return """
                {"id":"%s","reference":"%s","title":"%s","status":"todo","sort_order":%d,
                 "assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"",
                 "work_package_id":null,"work_package_reference":null}
                """.formatted(OTHER_PACKAGE_ID, reference, title, sortOrder);
    }

    private static String leanTask(String reference, String title, String workPackageId, int sortOrder) {
        return """
                {"id":"%s","reference":"%s","title":"%s","status":"todo","sort_order":%d,
                 "assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"",
                 "work_package_id":%s,"work_package_reference":null}
                """.formatted(TASK_ID, reference, title, sortOrder,
                workPackageId == null ? "null" : "\"" + workPackageId + "\"");
    }
}
