package de.homelabs.dashboard.shell;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;

/**
 * The session's access token, resolved per call: module REST calls forward it as Bearer,
 * so the backend sees the signed-in realm user instead of the dashboard acting as itself.
 *
 * <p>The token comes from the authorized-client store the login fills
 * ({@link OAuth2AuthorizedClientService}, in memory like the session). Nothing here
 * caches or refreshes it — the login owns its lifetime, and a missing client (anonymous
 * request, expired session) simply yields no token; the backend then answers 401, which
 * the module views turn back into a login redirect.
 */
@Component
public class AccessTokenProvider {

    /**
     * Registration id of {@code application.yml}
     * ({@code spring.security.oauth2.client.registration.keycloak}).
     */
    public static final String REGISTRATION_ID = "keycloak";

    private final OAuth2AuthorizedClientService authorizedClients;

    public AccessTokenProvider(OAuth2AuthorizedClientService authorizedClients) {
        this.authorizedClients = authorizedClients;
    }

    /** The access token of the current request's session, or {@code null} without one. */
    public String currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        OAuth2AuthorizedClient authorizedClient = authorizedClients.loadAuthorizedClient(REGISTRATION_ID,
                authentication.getName());
        return authorizedClient == null ? null : authorizedClient.getAccessToken().getTokenValue();
    }
}
