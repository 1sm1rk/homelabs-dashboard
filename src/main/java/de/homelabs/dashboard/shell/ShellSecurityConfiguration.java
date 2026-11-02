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
                .oauth2Login(Customizer.withDefaults());
        return http.build();
    }
}
