package de.homelabs.dashboard;

import java.util.Locale;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * <p>The login gate is replaced by a test-only permit-all chain (no production backdoor);
 * the gate itself is proven by {@link DashboardBootIT}.
 */
@SpringBootTest
@Import({ StubClientRegistrationRepository.class, ShellRenderingIT.PermitAllConfiguration.class })
class ShellRenderingIT {

    private static final String LOCALE_COOKIE = "dashboard.locale";

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

    /** The anchor that carries {@code lang="de"} or {@code lang="en"} in the topbar. */
    private static String languageLink(String html, String language) {
        Matcher matcher = Pattern.compile("<a[^>]*lang=\"" + language + "\"[^>]*>").matcher(html);

        assertThat(matcher.find()).as("language switch link '%s'", language).isTrue();
        return matcher.group();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PermitAllConfiguration {

        /** Test-only chain in front of the real one: rendering without a login round-trip. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain renderingTestSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }
    }
}
