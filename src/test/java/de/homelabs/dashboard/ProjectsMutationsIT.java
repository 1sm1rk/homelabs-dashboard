package de.homelabs.dashboard;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The module's mutation flows against the production login gate, the production CSRF
 * protection and a fake projects API: create, the three lifecycle moves, the four inline
 * item fields, the two create forms and the comment form, each answering with the region
 * it changed, and each rejected write rendering the API's own body at the control.
 *
 * <p>Every mutation here carries the token the LAYOUT rendered (parsed out of the body's
 * {@code hx-headers} and sent as the header htmx would send), on one shared session — the
 * production path, not a test-only bypass. The payloads are asserted verbatim because the
 * API drops a misspelled field silently: a wrong wire name would be a no-op, not an error.
 */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class ProjectsMutationsIT {

    private static final FakeProjectsApi API = new FakeProjectsApi();

    private static final String TOKEN = "session-access-token";

    private static final String ISSUER = "https://auth.example.test/realms/homelabs";

    private static final String SUBJECT = "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90";

    private static final String PROJECT_ID = "1b3f6d2e-5f7a-4c8b-9d10-2e3f4a5b6c7d";

    private static final String PACKAGE_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private static final String COMMENT_ID = "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d";

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
    void rendersTheCsrfTokenOnceAtTheBodyLevelForEveryHtmxRequest() throws Exception {
        String html = mockMvc.perform(authenticated(get("/")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).containsOnlyOnce("hx-headers=");
        assertThat(csrfHeader)
                .as("the header the token is rendered for is the one htmx sends")
                .isEqualTo("X-CSRF-TOKEN");
        assertThat(csrfToken).isNotBlank();
    }

    @Test
    void rejectsAMutationWithoutTheRenderedCsrfToken() throws Exception {
        // The same login and session as every other test — only the token is missing.
        mockMvc.perform(post("/projects/WEB/deactivate").session(session).with(authentication(login()))
                        .locale(Locale.GERMANY))
                .andExpect(status().isForbidden());

        assertThat(API.calls())
                .as("the security chain answers before the module reaches the backend")
                .isEmpty();
    }

    @Test
    void createsAProjectAndSwapsTheWholeListPanelInPlace() throws Exception {
        API.json("POST", "/api/projects", 201, project("active"));
        API.json("GET", "/api/projects", 200, leanList("active"));

        mockMvc.perform(authenticated(post("/projects"))
                        .param("name", "homelabs dashboard")
                        .param("short_code", "WEB")
                        .param("description", "eine Beschreibung")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"project-panel\"")))
                .andExpect(content().string(containsString(">WEB</td>")))
                .andExpect(content().string(containsString("badge badge--active")))
                .andExpect(content().string(containsString("id=\"new-project-name\"")))
                // The panel keeps the filters the page shows, so the rows stay the reader's view.
                .andExpect(content().string(containsString("hx-post=\"/projects?status=&amp;q=\"")))
                .andExpect(content().string(not(containsString("<html"))));

        FakeProjectsApi.Call create = API.onlyCall("POST", "/api/projects");
        assertThat(create.body())
                .as("the create body names the API's wire fields — a misspelled one would be dropped")
                .isEqualTo("{\"name\":\"homelabs dashboard\",\"short_code\":\"WEB\","
                        + "\"description\":\"eine Beschreibung\"}");
        assertThat(create.authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void createsAProjectAsAFullPageWhenHtmxIsNotInvolved() throws Exception {
        API.json("POST", "/api/projects", 201, project("active"));
        API.json("GET", "/api/projects", 200, leanList("active"));

        mockMvc.perform(authenticated(post("/projects")).param("name", "homelabs dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("class=\"rail\"")))
                .andExpect(content().string(containsString(">WEB</td>")));
    }

    @Test
    void rendersTheShortCodeCollisionWithTheApiSuggestionsAtTheForm() throws Exception {
        API.json("POST", "/api/projects", 409, """
                {"error":"short code 'WEB' is already taken","suggestions":["WEB2","WEB3","WEB4"]}
                """);

        mockMvc.perform(authenticated(post("/projects"))
                        .param("name", "homelabs dashboard")
                        .param("short_code", "WEB")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#create-project-notice"))
                .andExpect(header().string("HX-Reswap", "innerHTML"))
                .andExpect(content().string(containsString("already taken")))
                .andExpect(content().string(containsString("WEB2")))
                .andExpect(content().string(containsString("WEB4")))
                .andExpect(content().string(not(containsString("id=\"project-panel\""))));
    }

    @Test
    void rendersAValidationMessageWithTheWireFieldNameAtTheField() throws Exception {
        API.json("POST", "/api/projects", 400,
                "{\"error\":\"short_code: must be 2-6 characters of A-Z and 0-9\"}");

        mockMvc.perform(authenticated(post("/projects"))
                        .param("name", "Kurz")
                        .param("short_code", "K")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#create-project-notice"))
                .andExpect(content().string(containsString("<code class=\"problem__field\">short_code</code>")))
                .andExpect(content().string(containsString("must be 2-6 characters of A-Z and 0-9")));
    }

    @Test
    void deactivatesTheProjectAndRerendersTheDetailInPlace() throws Exception {
        API.json("POST", "/api/projects/WEB/deactivate", 200, project("deactivated"));
        stubDetail("deactivated");

        mockMvc.perform(authenticated(post("/projects/WEB/deactivate")).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("badge badge--warning")))
                .andExpect(content().string(containsString(">Aktivieren</button>")))
                .andExpect(content().string(not(containsString(">Deaktivieren</button>"))))
                .andExpect(content().string(not(containsString("<html"))));

        assertThat(API.onlyCall("POST", "/api/projects/WEB/deactivate").authorization())
                .isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void patchesAnItemStatusInlineAndReReadsThePlan() throws Exception {
        API.json("PATCH", "/api/work-packages/WEB-1", 200, workPackage("done"));
        stubPlan(workPackage("done"), "[]");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1", "status=done"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"plan\"")))
                .andExpect(content().string(containsString("badge--done")))
                .andExpect(content().string(not(containsString("<html"))));

        FakeProjectsApi.Call patch = API.onlyCall("PATCH", "/api/work-packages/WEB-1");
        assertThat(patch.body())
                .as("one control, one field: the untouched fields stay absent instead of riding along")
                .isEqualTo("{\"status\":\"done\"}");
        assertThat(patch.authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void clearsTheAssigneeAndMovesTheItemWithTheWireFieldNames() throws Exception {
        API.json("PATCH", "/api/tasks/WEB-2", 200, task("todo"));
        API.json("PATCH", "/api/work-packages/WEB-1", 200, workPackage("todo"));
        stubPlan(workPackage("todo"), "[]");

        // An empty assignee is the API's "clear", not "unchanged": the form sends "".
        mockMvc.perform(formPatch("/projects/WEB/tasks/WEB-2", "assignee_sub="))
                .andExpect(status().isOk());
        assertThat(API.callsTo("PATCH", "/api/tasks/WEB-2").getFirst().body())
                .isEqualTo("{\"assignee_sub\":\"\"}");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1", "sort_order=3"))
                .andExpect(status().isOk());
        assertThat(API.callsTo("PATCH", "/api/work-packages/WEB-1").getFirst().body())
                .isEqualTo("{\"sort_order\":3}");

        // An emptied position is not a write of 0: the field stays absent and the API
        // answers its own "nothing to change".
        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1", "sort_order="))
                .andExpect(status().isOk());
        assertThat(API.callsTo("PATCH", "/api/work-packages/WEB-1").get(1).body())
                .isEqualTo("{}");
    }

    @Test
    void createsAWorkPackageAndATaskInsideThePlan() throws Exception {
        API.json("POST", "/api/projects/WEB/work-packages", 201, workPackage("todo"));
        API.json("POST", "/api/tasks", 201, task("todo"));
        stubPlan(workPackage("todo"), "[]");

        mockMvc.perform(authenticated(post("/projects/WEB/work-packages"))
                        .param("title", "Paket aus dem Formular")
                        .param("assignee_sub", SUBJECT)
                        .param("due_date", "2026-11-20")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"plan\"")))
                .andExpect(content().string(containsString("id=\"new-work-package-title\"")));

        assertThat(API.onlyCall("POST", "/api/projects/WEB/work-packages").body())
                .isEqualTo("{\"title\":\"Paket aus dem Formular\",\"assignee_sub\":\"" + SUBJECT
                        + "\",\"due_date\":\"2026-11-20\"}");

        mockMvc.perform(authenticated(post("/projects/WEB/tasks"))
                        .param("title", "Aufgabe aus dem Formular")
                        .param("work_package_id", "WEB-1")
                        .param("assignee_sub", "")
                        .param("due_date", "")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"plan\"")));

        assertThat(API.onlyCall("POST", "/api/tasks").body())
                .as("the task create names its project and the package it joins; blank fields stay out")
                .isEqualTo("{\"title\":\"Aufgabe aus dem Formular\",\"project\":\"WEB\","
                        + "\"work_package_id\":\"WEB-1\"}");
    }

    @Test
    void addsACommentAndRerendersTheListInPlace() throws Exception {
        API.json("POST", "/api/comments", 201, """
                {"id":"%s","entity_type":"project","entity_id":"%s","author_sub":"%s",
                 "author_name":"di.mueller","content":"neuer Kommentar","created_at":"2026-11-03T09:15:00Z"}
                """.formatted(COMMENT_ID, PROJECT_ID, SUBJECT));
        API.json("GET", "/api/comments", 200, """
                [{"id":"%s","entity_type":"project","entity_id":"%s","author_sub":"%s",
                  "author_name":"di.mueller","content":"neuer Kommentar","created_at":"2026-11-03T09:15:00Z"}]
                """.formatted(COMMENT_ID, PROJECT_ID, SUBJECT));

        mockMvc.perform(authenticated(post("/projects/WEB/comments"))
                        .param("text", "neuer Kommentar")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"comments\"")))
                .andExpect(content().string(containsString(">neuer Kommentar</p>")))
                .andExpect(content().string(containsString("id=\"new-comment-text\"")))
                .andExpect(content().string(not(containsString("<html"))));

        FakeProjectsApi.Call created = API.onlyCall("POST", "/api/comments");
        assertThat(created.body())
                .as("the parent is the API's typed form — the only unambiguous identifier")
                .isEqualTo("{\"parent\":\"project:WEB\",\"text\":\"neuer Kommentar\"}");
    }

    @Test
    void rendersThePlanWithItsInlineEditorsAndTheLifecycleMoves() throws Exception {
        API.json("GET", "/api/projects/WEB", 200, project("active"));
        API.json("GET", "/api/work-packages", 200, """
                [{"id":"%s","reference":"WEB-1","title":"Package one","status":"in_progress","sort_order":2,
                  "assignee_sub":"%s","assignee_name":"di.mueller","due_date":"2026-11-20","excerpt":"",
                  "work_package_id":null,"work_package_reference":null}]
                """.formatted(PACKAGE_ID, SUBJECT));
        API.json("GET", "/api/tasks", 200, "[" + task("todo") + "]");
        API.json("GET", "/api/comments", 200, "[]");
        API.json("GET", "/api/activities", 200, "[]");
        API.json("GET", "/api/attachments", 200, "[]");

        String html = mockMvc.perform(authenticated(get("/projects/WEB")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"plan\"")))
                // A work-package row posts to the work-package route, a task row to the task route.
                .andExpect(content().string(containsString("hx-patch=\"/projects/WEB/work-packages/WEB-1\"")))
                .andExpect(content().string(containsString("hx-patch=\"/projects/WEB/tasks/WEB-2\"")))
                // The status editor offers the wire vocabulary and preselects the current value.
                .andExpect(content().string(containsString("selected=\"selected\">In Arbeit</option>")))
                // The assignee editor carries the subject the API stores, not the display name.
                .andExpect(content().string(containsString("name=\"assignee_sub\"")))
                .andExpect(content().string(containsString(">di.mueller</span>")))
                // Every row owns a notice block, and the header offers the moves of its state.
                .andExpect(content().string(containsString("id=\"item-notice-WEB-1\"")))
                .andExpect(content().string(containsString("hx-post=\"/projects/WEB/deactivate\"")))
                .andExpect(content().string(containsString("hx-post=\"/projects/WEB/archive\"")))
                .andExpect(content().string(containsString("hx-confirm=")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("value=\"" + SUBJECT + "\"");
        assertThat(html).contains("hx-post=\"/projects/WEB/work-packages\"");
        assertThat(html).contains("hx-post=\"/projects/WEB/tasks\"");
        assertThat(html).contains("id=\"create-work-package-notice\"");
        assertThat(html).contains("id=\"create-task-notice\"");
        assertThat(html).contains("id=\"comments-notice\"");
    }

    @Test
    void rendersTheArchivedConflictInlineAtTheRowThatCausedIt() throws Exception {
        API.json("PATCH", "/api/work-packages/WEB-1", 409,
                "{\"error\":\"project WEB is archived: the whole project is read-only, writes are rejected\"}");

        mockMvc.perform(formPatch("/projects/WEB/work-packages/WEB-1", "status=done"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#item-notice-WEB-1"))
                .andExpect(header().string("HX-Reswap", "innerHTML"))
                .andExpect(content().string(containsString("the whole project is read-only")))
                .andExpect(content().string(not(containsString("id=\"plan\""))));
    }

    @Test
    void surfacesARejectedTransitionAtTheDetailHeader() throws Exception {
        API.json("POST", "/api/projects/WEB/deactivate", 409,
                "{\"error\":\"project WEB is already deactivated\"}");

        mockMvc.perform(authenticated(post("/projects/WEB/deactivate")).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#project-notice"))
                .andExpect(content().string(containsString("already deactivated")));
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
    private MockHttpServletRequestBuilder formPatch(String path, String form) {
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

    /** A detail load's six answers, with the project in the given lifecycle state. */
    private void stubDetail(String status) {
        API.json("GET", "/api/projects/WEB", 200, project(status));
        stubPlan(workPackage("todo"), "[]");
        API.json("GET", "/api/comments", 200, "[]");
        API.json("GET", "/api/activities", 200, "[]");
        API.json("GET", "/api/attachments", 200, "[]");
    }

    /** The plan's two lists: the work packages and the tasks of the project. */
    private void stubPlan(String workPackages, String tasks) {
        API.json("GET", "/api/work-packages", 200, "[" + workPackages + "]");
        API.json("GET", "/api/tasks", 200, tasks);
    }

    private static String leanList(String status) {
        return """
                [{"id":"%s","reference":"WEB","name":"homelabs dashboard","status":"%s",
                  "owner_name":"di.mueller","updated_at":"2026-11-03T09:00:00Z","excerpt":"","labels":[]}]
                """.formatted(PROJECT_ID, status);
    }

    private static String project(String status) {
        return """
                {"id":"%s","short_code":"WEB","name":"homelabs dashboard","status":"%s",
                 "description_html":"<p>canonical description</p>","description_text":"canonical description",
                 "owner_sub":"%s","owner_name":"di.mueller","created_at":"2026-11-02T10:00:00Z",
                 "updated_at":"2026-11-03T09:00:00Z","labels":[]}
                """.formatted(PROJECT_ID, status, SUBJECT);
    }

    private static String workPackage(String status) {
        return """
                {"id":"%s","reference":"WEB-1","title":"Package one","status":"%s","sort_order":1,
                 "assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"",
                 "work_package_id":null,"work_package_reference":null}
                """.formatted(PACKAGE_ID, status);
    }

    private static String task(String status) {
        return """
                {"id":"5d6e7f80-9a1b-4c2d-8e3f-0a1b2c3d4e5f","reference":"WEB-2","title":"A task",
                 "status":"%s","sort_order":2,"assignee_sub":null,"assignee_name":null,
                 "due_date":null,"excerpt":"","work_package_id":"%s","work_package_reference":"WEB-1"}
                """.formatted(status, PACKAGE_ID);
    }
}
