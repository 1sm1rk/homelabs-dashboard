package de.homelabs.dashboard;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sign-out flow against the production login gate (no permit-all seam here): the topbar
 * form posts to {@code /logout}, the security chain ends the session, and the next request
 * is unauthenticated again. Without the CSRF token the mutation is rejected.
 */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class LogoutIT {

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
    void signingOutEndsTheSessionAndDropsTheCookie() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/").locale(Locale.GERMANY).session(session).with(user("di.mueller")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid())
                .as("the sign-out invalidates the browser session instead of just clearing the context")
                .isTrue();

        mockMvc.perform(get("/").locale(Locale.GERMANY))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString(
                        "/oauth2/authorization/" + StubClientRegistrationRepository.REGISTRATION_ID)));
    }

    @Test
    void rejectsASignOutWithoutTheCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/logout").session(session).with(user("di.mueller")))
                .andExpect(status().isForbidden());

        assertThat(session.isInvalid())
                .as("a rejected sign-out leaves the session alone")
                .isFalse();
    }
}
