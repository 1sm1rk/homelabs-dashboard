package de.homelabs.dashboard.shell;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The dashboard's login gate: OAuth2 Authorization Code against the shared realm, one
 * session per browser. Everything but the liveness probe sits behind the session and an
 * unauthenticated request therefore lands on the login redirect (Spring's default).
 *
 * <p>PKCE rides along automatically: the client is confidential and the realm advertises
 * its challenge methods, so Spring sends the challenge and verifier without anything
 * being pinned here.
 *
 * <p>Sign-out is a POST through the security chain (session kill by Spring's
 * {@code SecurityContextLogoutHandler}, plus the session cookie dropped in the browser),
 * which is why the shell ships a form and not a link — the CSRF token protects it like
 * every other mutation. It ends the dashboard session only; the realm's own SSO session
 * lives on, so the next page request starts a fresh login round-trip. RP-initiated
 * logout would end that too, but the realm has no post-logout redirect registered, so
 * the browser would land on Keycloak's logged-out page instead of the dashboard.
 *
 * <p>Authorization is not decided here: the dashboard mirrors the backends' answers
 * (401 becomes a login redirect, 403/409 render inline in the module views).
 */
@Configuration
public class ShellSecurityConfiguration {

    @Bean
    SecurityFilterChain shellSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        // Back to the shell: the next request carries no session and gets
                        // the login redirect again. (Spring's default would target
                        // /login?logout, a page this application does not have.)
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
