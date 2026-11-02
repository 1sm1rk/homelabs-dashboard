package de.homelabs.dashboard;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shell rendering: the layout really composes head, rail, topbar and content slot, the
 * rail entry comes from the module registry — not from a hard-coded template — and the
 * chrome renders in German (the default) or English, whichever the locale chain
 * resolves.
 *
 * <p>The login gate is replaced by a test-only permit-all chain (no production
 * backdoor); the gate itself is proven by {@link DashboardBootIT}, the sign-out flow by
 * {@link LogoutIT}.
 */
@SpringBootTest
@Import({ StubClientRegistrationRepository.class, ShellRenderingIT.PermitAllConfiguration.class })
class ShellRenderingIT {

    private static final String LOCALE_COOKIE = "dashboard.locale";

    /** Realm issue of the identity claims; the login would hand exactly this over. */
    private static final String ISSUER = "https://auth.example.test/realms/homelabs";

    /** The subject of the test identity — the realm's raw principal key. */
    private static final String SUBJECT = "8f2a1c44-0d4b-4f0a-9b28-2d5c6e1f7a90";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** A browser that sends {@code Accept-Language: de}. */
    private MockHttpServletRequestBuilder home() {
        return get("/").locale(Locale.GERMANY);
    }

    @Test
    void rendersTheRailEntryFromTheModuleRegistry() throws Exception {
        mockMvc.perform(home())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"rail\"")))
                .andExpect(content().string(containsString("href=\"/projects\"")))
                .andExpect(content().string(containsString(">Projekte</a>")));
    }

    @Test
    void rendersTheTopbarAndTheContentSlotInGermanByDefault() throws Exception {
        mockMvc.perform(home())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"topbar\"")))
                .andExpect(content().string(containsString("<html lang=\"de\"")))
                .andExpect(content().string(containsString("<title>Übersicht · homelabs</title>")))
                .andExpect(content().string(containsString("class=\"page__title\"")))
                .andExpect(content().string(containsString("Eine Oberfläche für die Homelab-Dienste.")))
                .andExpect(content().string(not(containsString("class=\"topbar__identity\""))))
                .andExpect(cookie().doesNotExist(LOCALE_COOKIE));
    }

    @Test
    void referencesTheStylesheetAndHtmx() throws Exception {
        mockMvc.perform(home())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/dashboard.css\"")))
                .andExpect(content().string(containsString("src=\"/js/htmx.min.js\"")));
    }

    @Test
    void switchesTheChromeToEnglishAndPersistsTheChoiceInTheCookie() throws Exception {
        String html = mockMvc.perform(home().param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(LOCALE_COOKIE, "en"))
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(containsString("<title>Overview · homelabs</title>")))
                .andExpect(content().string(containsString(">Projects</a>")))
                .andExpect(content().string(containsString("One surface for the homelab services.")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(languageLink(html, "en"))
                .contains("aria-current=\"true\"")
                .contains("topbar__locale--active");
        assertThat(languageLink(html, "de")).doesNotContain("aria-current");
    }

    @Test
    void switchesBackToGermanAndRewritesTheCookie() throws Exception {
        String html = mockMvc.perform(get("/")
                        .locale(Locale.UK)
                        .cookie(new Cookie(LOCALE_COOKIE, "en"))
                        .param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(LOCALE_COOKIE, "de"))
                .andExpect(content().string(containsString("<html lang=\"de\"")))
                .andExpect(content().string(containsString("<title>Übersicht · homelabs</title>")))
                .andExpect(content().string(containsString(">Projekte</a>")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(languageLink(html, "de"))
                .contains("aria-current=\"true\"")
                .contains("topbar__locale--active");
        assertThat(languageLink(html, "en")).doesNotContain("aria-current");
    }

    @Test
    void keepsTheCookieChoiceOverTheBrowserLanguage() throws Exception {
        mockMvc.perform(get("/")
                        .locale(Locale.GERMANY)
                        .cookie(new Cookie(LOCALE_COOKIE, "en")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Overview · homelabs</title>")))
                .andExpect(content().string(containsString(">Projects</a>")));
    }

    @Test
    void fallsBackToGermanForAnUnsupportedBrowserLanguage() throws Exception {
        mockMvc.perform(get("/").locale(Locale.FRANCE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Übersicht · homelabs</title>")));
    }

    @Test
    void ignoresAnUnsupportedLanguageParameter() throws Exception {
        mockMvc.perform(home().param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(LOCALE_COOKIE))
                .andExpect(content().string(containsString("<title>Übersicht · homelabs</title>")));
    }

    @Test
    void ignoresAnUnknownCookieValue() throws Exception {
        mockMvc.perform(get("/")
                        .locale(Locale.GERMANY)
                        .cookie(new Cookie(LOCALE_COOKIE, "fr")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Übersicht · homelabs</title>")));
    }

    @Test
    @WithMockUser(username = "di.mueller")
    void rendersTheIdentityAndTheSignOutButtonInTheActiveLanguage() throws Exception {
        mockMvc.perform(home())
                .andExpect(content().string(containsString("class=\"topbar__user\"")))
                .andExpect(content().string(containsString("di.mueller")))
                .andExpect(content().string(containsString(">Abmelden</button>")));

        mockMvc.perform(home().param("lang", "en"))
                .andExpect(content().string(containsString(">Sign out</button>")));
    }

    @Test
    void rendersThePreferredUsernameOfTheOidcPrincipal() throws Exception {
        mockMvc.perform(home().with(authentication(oidcLogin(Map.of(
                        "preferred_username", "di.mueller",
                        "name", "Dirk Müller")))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span class=\"topbar__user\">di.mueller</span>")));
    }

    @Test
    void fallsBackToTheNameClaimWithoutAPreferredUsername() throws Exception {
        mockMvc.perform(home().with(authentication(oidcLogin(Map.of("name", "Dirk Müller")))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span class=\"topbar__user\">Dirk Müller</span>")));
    }

    @Test
    void fallsBackToThePrincipalKeyWithoutAnyDisplayClaim() throws Exception {
        mockMvc.perform(home().with(authentication(oidcLogin(Map.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span class=\"topbar__user\">" + SUBJECT + "</span>")));
    }

    @Test
    void rendersTheSignOutAsACsrfProtectedPostForm() throws Exception {
        String html = mockMvc.perform(home().with(user("di.mueller")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"topbar__identity\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(signOutForm(html))
                .contains("method=\"post\"")
                .contains("action=\"/logout\"")
                .containsOnlyOnce("name=\"_csrf\"");
    }

    @Test
    void rendersNoSubnavOnShellRoutes() throws Exception {
        // The projects module declares its overview entry, but the shell's own home page
        // belongs to no module: the bar appears on module routes only.
        mockMvc.perform(home().with(user("di.mueller")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"subnav\""))));
    }

    /** The anchor that carries {@code lang="de"} or {@code lang="en"} in the topbar. */
    private static String languageLink(String html, String language) {
        Matcher matcher = Pattern.compile("<a[^>]*lang=\"" + language + "\"[^>]*>").matcher(html);

        assertThat(matcher.find()).as("language switch link '%s'", language).isTrue();
        return matcher.group();
    }

    /** The sign-out form of the topbar, including its hidden fields. */
    private static String signOutForm(String html) {
        Matcher matcher = Pattern.compile("<form[^>]*action=\"/logout\"[^>]*>.*?</form>", Pattern.DOTALL).matcher(html);

        assertThat(matcher.find()).as("sign-out form").isTrue();
        return matcher.group();
    }

    /**
     * An authenticated login the way the OAuth2 client would hand it over: the OIDC
     * principal as the authentication's principal, the registration id as authorized
     * client.
     */
    private static Authentication oidcLogin(Map<String, Object> claims) {
        Map<String, Object> idTokenClaims = new LinkedHashMap<>();
        idTokenClaims.put("iss", ISSUER);
        idTokenClaims.put("aud", List.of("dashboard"));
        idTokenClaims.put("sub", SUBJECT);
        idTokenClaims.putAll(claims);

        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                idTokenClaims);
        OidcUser oidcUser = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken, OidcUserInfo.builder()
                .subject(SUBJECT).build())), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(),
                StubClientRegistrationRepository.REGISTRATION_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PermitAllConfiguration {

        /**
         * Test-only chain in front of the real one: rendering without a login round-trip.
         * CSRF stays enabled — the shell's sign-out form renders its token from the same
         * machinery the production chain uses, so the rendering tests see the real markup.
         */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain renderingTestSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
