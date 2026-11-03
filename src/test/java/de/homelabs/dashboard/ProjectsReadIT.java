package de.homelabs.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The module's read views against the production login gate and a fake projects API: the
 * list renders lean rows only, the detail composes the plan tree in the wire's order,
 * every upstream call carries the session's Bearer token, the download proxy passes the
 * bytes through, and the backend's error answers surface inline.
 *
 * <p>The token is not mocked away: the tests store an authorized client exactly as the
 * login does and let {@code AccessTokenProvider} resolve it, so the forwarding path under
 * test is the production one.
 */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class ProjectsReadIT {

    private static final FakeProjectsApi API = new FakeProjectsApi();

    private static final String TOKEN = "session-access-token";

    private static final String ISSUER = "https://auth.example.test/realms/homelabs";

    private static final String SUBJECT = "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90";

    private static final String PROJECT_ID = "1b3f6d2e-5f7a-4c8b-9d10-2e3f4a5b6c7d";

    /** The package the grouped test task belongs to — the FIRST package of the wire. */
    private static final String FIRST_PACKAGE_ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    /** The package the wire delivers SECOND, although its sort order is the lower one. */
    private static final String SECOND_PACKAGE_ID = "9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a";

    private static final String ATTACHMENT_ID = "3c4d5e6f-7a8b-4c9d-8e1f-2a3b4c5d6e7f";

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

    @BeforeEach
    void setUp() {
        API.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rendersLeanProjectRowsWithoutTheCanonicalDescription() throws Exception {
        API.json("GET", "/api/projects", 200, """
                [{"id":"%s","reference":"WEB","name":"homelabs dashboard","status":"active",
                  "owner_name":"di.mueller","updated_at":"2026-11-02T10:33:08Z",
                  "excerpt":"lean excerpt of the description","labels":["designed","frontend"]},
                 {"id":"2c4d7e8f-6a9b-4d1c-8032-4f5a6b7c8d9e","reference":"OPS",
                  "name":"homelabs projects","status":"archived","owner_name":"di.mueller",
                  "updated_at":"2026-11-01T09:00:00Z","excerpt":"","labels":[]}]
                """.formatted(PROJECT_ID));

        mockMvc.perform(authenticatedGet("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">WEB</td>")))
                .andExpect(content().string(containsString("homelabs dashboard")))
                .andExpect(content().string(containsString("badge badge--active")))
                .andExpect(content().string(containsString("badge badge--muted")))
                .andExpect(content().string(containsString("<span class=\"chip\">designed</span>")))
                .andExpect(content().string(containsString("<span class=\"chip\">frontend</span>")))
                .andExpect(content().string(containsString("class=\"subnav\"")))
                .andExpect(content().string(containsString(">Übersicht</a>")))
                .andExpect(content().string(not(containsString("canonical description"))));

        // The filter fragment renders the same lean rows without any shell around them.
        mockMvc.perform(authenticatedGet("/projects/rows").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">WEB</td>")))
                .andExpect(content().string(containsString("badge badge--active")))
                .andExpect(content().string(containsString("<span class=\"chip\">designed</span>")))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("canonical description"))));

        assertThat(API.calls())
                .as("the list view reads the lean answer only — no per-row detail call")
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.path()).isEqualTo("/api/projects"));
    }

    @Test
    void rendersThePlanTreeInTheWireOrderAndThePackagelessTasksAfterIt() throws Exception {
        stubDetail();

        String html = mockMvc.perform(authenticatedGet("/projects/WEB"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("canonical description")))
                .andExpect(content().string(containsString(">designed</span>")))
                .andExpect(content().string(containsString(">comment one<")))
                .andExpect(content().string(containsString("Status geändert")))
                .andExpect(content().string(containsString(">notes.txt</a>")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html.indexOf("Second package"))
                .as("the wire delivers sort_order 2 before 1 — the view must keep that order")
                .isLessThan(html.indexOf("First package"));
        assertThat(html.indexOf("First package"))
                .as("a grouped task renders under its work package")
                .isLessThan(html.indexOf("Grouped task"));
        assertThat(html.indexOf("Grouped task"))
                .as("a task without a work package renders after the packages")
                .isLessThan(html.indexOf("Loose task"));
        assertThat(html).contains("Aufgaben ohne Arbeitspaket");
        assertThat(html)
                .as("every tree row links into the item's own page")
                .contains("href=\"/projects/WEB/work-packages/WEB-7\"")
                .contains("href=\"/projects/WEB/tasks/WEB-8\"");
    }

    @Test
    void forwardsTheSessionTokenOnEveryUpstreamCallOfOneLoad() throws Exception {
        stubDetail();

        mockMvc.perform(authenticatedGet("/projects/WEB")).andExpect(status().isOk());

        assertThat(API.calls())
                .as("one detail load: the record, the plan tree and the three sections")
                .hasSize(6)
                .allSatisfy(call -> assertThat(call.authorization())
                        .as("%s %s", call.method(), call.path())
                        .isEqualTo("Bearer " + TOKEN));
    }

    @Test
    void handsTheFiltersToTheApiAndRendersTheFilteredRowsAlone() throws Exception {
        API.json("GET", "/api/projects", 200, """
                [{"id":"%s","reference":"WEB","name":"homelabs dashboard","status":"archived",
                  "owner_name":"di.mueller","updated_at":"2026-11-02T10:33:08Z","excerpt":"","labels":[]}]
                """.formatted(PROJECT_ID));

        mockMvc.perform(authenticatedGet("/projects/rows")
                        .param("status", "archived")
                        .param("q", "dashboard")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">WEB</td>")))
                .andExpect(content().string(not(containsString("<html"))));

        assertThat(API.onlyCall("GET", "/api/projects").query())
                .as("the filter parameters are the REST parameters, handed over unchanged")
                .contains("status=archived")
                .contains("q=dashboard");
    }

    @Test
    void proxiesAnAttachmentWithItsContentTypeAndEtag() throws Exception {
        byte[] bytes = { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x7f };
        API.binary("GET", "/api/attachments/" + ATTACHMENT_ID + "/content", 200, "image/png", bytes,
                Map.of("ETag", "\"3f7c1d9a\"", "Content-Disposition", "inline; filename=\"diagram.png\""));

        mockMvc.perform(authenticatedGet("/projects/WEB/attachments/" + ATTACHMENT_ID + "/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("ETag", "\"3f7c1d9a\""))
                .andExpect(content().bytes(bytes));

        assertThat(API.onlyCall("GET", "/api/attachments/" + ATTACHMENT_ID + "/content").authorization())
                .isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void redirectsAnUnauthenticatedViewRequestToTheLogin() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/keycloak")));
        mockMvc.perform(get("/projects/WEB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/keycloak")));

        assertThat(API.calls())
                .as("the gate answers before the module calls the backend")
                .isEmpty();
    }

    @Test
    void surfacesAnUnknownProjectAsALocalizedNotice() throws Exception {
        API.json("GET", "/api/projects/OPS", 404, "{\"error\":\"no project OPS\"}");

        mockMvc.perform(authenticatedGet("/projects/OPS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dieses Projekt gibt es nicht")))
                .andExpect(content().string(containsString("no project OPS")));
    }

    @Test
    void rendersTheBackendConflictMessageInline() throws Exception {
        API.json("GET", "/api/projects/WEB", 409, "{\"error\":\"project WEB is archived\"}");

        mockMvc.perform(authenticatedGet("/projects/WEB"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Die Projektschnittstelle hat die Anfrage abgelehnt.")))
                .andExpect(content().string(containsString("project WEB is archived")));
    }

    @Test
    void sendsTheReaderBackToTheLoginWhenTheBackendRejectsTheToken() throws Exception {
        API.json("GET", "/api/projects/WEB", 401, "{\"error\":\"unauthorized\"}");

        mockMvc.perform(authenticatedGet("/projects/WEB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/keycloak"));
    }

    @Test
    void rendersTheDetailAloneWhenHtmxSwapsItIntoTheWorkingArea() throws Exception {
        stubDetail();

        mockMvc.perform(authenticatedGet("/projects/WEB").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("homelabs dashboard")))
                .andExpect(content().string(containsString("canonical description")))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("class=\"rail\""))));
    }

    /** One detail load's answers: the record, two packages in "wrong" order, two tasks. */
    private void stubDetail() {
        API.json("GET", "/api/projects/WEB", 200, """
                {"id":"%s","short_code":"WEB","name":"homelabs dashboard","status":"active",
                 "description_html":"<p>canonical description</p>","description_text":"canonical description",
                 "owner_sub":"%s","owner_name":"di.mueller","created_at":"2026-11-02T10:00:00Z",
                 "updated_at":"2026-11-02T12:00:00Z",
                 "labels":[{"id":"b1e0d6b9-3c5a-4d2e-9f10-6a7b8c9d0e1f","name":"designed","color":null}]}
                """.formatted(PROJECT_ID, SUBJECT));
        API.json("GET", "/api/work-packages", 200, """
                [{"id":"%s","reference":"WEB-7","title":"Second package","status":"todo","sort_order":2,
                  "assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"",
                  "work_package_id":null,"work_package_reference":null},
                 {"id":"%s","reference":"WEB-6","title":"First package","status":"done","sort_order":1,
                  "assignee_sub":"%s","assignee_name":"di.mueller","due_date":"2026-11-20","excerpt":"",
                  "work_package_id":null,"work_package_reference":null}]
                """.formatted(SECOND_PACKAGE_ID, FIRST_PACKAGE_ID, SUBJECT));
        API.json("GET", "/api/tasks", 200, """
                [{"id":"5d6e7f80-9a1b-4c2d-8e3f-0a1b2c3d4e5f","reference":"WEB-8",
                  "title":"Grouped task","status":"in_progress","sort_order":3,
                  "assignee_sub":null,"assignee_name":null,"due_date":null,"excerpt":"",
                  "work_package_id":"%s","work_package_reference":"WEB-6"},
                 {"id":"7f8091a2-3b4c-4d5e-9f60-1a2b3c4d5e6f","reference":"WEB-9",
                  "title":"Loose task","status":"blocked","sort_order":4,
                  "assignee_sub":"%s","assignee_name":"di.mueller","due_date":null,"excerpt":"",
                  "work_package_id":null,"work_package_reference":null}]
                """.formatted(FIRST_PACKAGE_ID, SUBJECT));
        API.json("GET", "/api/comments", 200, """
                [{"id":"8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d","entity_type":"project",
                  "entity_id":"%s","author_sub":"%s","author_name":"di.mueller","content":"comment one",
                  "created_at":"2026-11-02T12:30:00Z"}]
                """.formatted(PROJECT_ID, SUBJECT));
        API.json("GET", "/api/activities", 200, """
                [{"id":"9b0c1d2e-3f4a-4b5c-9d6e-8f9a0b1c2d3e","entity_type":"project",
                  "entity_id":"%s","actor_sub":"%s","actor_name":"di.mueller","source":"opencode",
                  "event_type":"status_changed","detail":"active → deactivated",
                  "created_at":"2026-11-02T12:31:00Z"}]
                """.formatted(PROJECT_ID, SUBJECT));
        API.json("GET", "/api/attachments", 200, """
                [{"id":"%s","entity_type":"project","entity_id":"%s","filename":"notes.txt",
                  "mime_type":"text/plain","byte_size":2048,"sha256":"3f7c1d9a","uploaded_by_sub":"%s",
                  "uploaded_by_name":"di.mueller","created_at":"2026-11-02T12:32:00Z"}]
                """.formatted(ATTACHMENT_ID, PROJECT_ID, SUBJECT));
    }

    /**
     * A request with the login the OAuth2 client would have produced: the authorized
     * client holds the access token the module forwards, the authentication names the
     * realm subject.
     */
    private MockHttpServletRequestBuilder authenticatedGet(String path) {
        OAuth2AuthenticationToken authentication = login();
        ClientRegistration registration = clientRegistrations
                .findByRegistrationId(StubClientRegistrationRepository.REGISTRATION_ID);
        authorizedClients.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, authentication.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, Instant.now(),
                        Instant.now().plus(Duration.ofMinutes(10)))),
                authentication);
        return get(path).locale(Locale.GERMANY).with(authentication(authentication));
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
}
