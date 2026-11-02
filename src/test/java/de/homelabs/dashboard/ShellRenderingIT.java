package de.homelabs.dashboard;

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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shell rendering: the layout really composes head, rail, topbar and content slot, and
 * the rail entry comes from the module registry — not from a hard-coded template.
 *
 * <p>The login gate is replaced by a test-only permit-all chain (no production backdoor);
 * the gate itself is proven by {@link DashboardBootIT}.
 */
@SpringBootTest
@Import({ StubClientRegistrationRepository.class, ShellRenderingIT.PermitAllConfiguration.class })
class ShellRenderingIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rendersTheRailEntryFromTheModuleRegistry() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"rail\"")))
                .andExpect(content().string(containsString("href=\"/projects\"")))
                .andExpect(content().string(containsString(">Projects</a>")));
    }

    @Test
    void rendersTheTopbarAndTheContentSlot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"topbar\"")))
                .andExpect(content().string(containsString("<title>Overview · homelabs</title>")))
                .andExpect(content().string(containsString("class=\"page__title\"")))
                .andExpect(content().string(containsString("homelabs dashboard")));
    }

    @Test
    void referencesTheStylesheetAndHtmx() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/dashboard.css\"")))
                .andExpect(content().string(containsString("src=\"/js/htmx.min.js\"")));
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
