package de.homelabs.dashboard.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Test-only replacement for the auto-configured client registrations.
 *
 * <p>The real configuration resolves the issuer metadata of the live realm while the
 * application context starts, which no test may depend on (and no CI runner can reach).
 * The registration mirrors {@code application.yml} in shape — same registration id,
 * grant type and scopes — so login-redirect assertions keep their meaning, while the
 * endpoints are deliberately unroutable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubClientRegistrationRepository {

    /** Registration id from {@code application.yml}. */
    public static final String REGISTRATION_ID = "keycloak";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId("dashboard")
                .clientSecret("test-only-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri("http://localhost:0/realms/homelabs")
                .authorizationUri("http://localhost:0/realms/homelabs/protocol/openid-connect/auth")
                .tokenUri("http://localhost:0/realms/homelabs/protocol/openid-connect/token")
                .jwkSetUri("http://localhost:0/realms/homelabs/protocol/openid-connect/certs")
                .userInfoUri("http://localhost:0/realms/homelabs/protocol/openid-connect/userinfo")
                .userNameAttributeName("sub")
                .clientName("homelabs")
                .build());
    }
}
