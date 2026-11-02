package de.homelabs.dashboard.shell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The login registration is deployment data: {@code application.yml} maps the four
 * environment variables onto the Keycloak client, and nothing about the realm is pinned in
 * the code. The test binds the shipped YAML into a properties record — no realm, no network,
 * the same way {@link DashboardModulePropertiesTest} binds the module properties.
 */
class ClientRegistrationPropertiesTest {

    private static final String REGISTRATION_PREFIX = "spring.security.oauth2.client.registration.keycloak";

    private static final List<PropertySource<?>> APPLICATION_YAML = loadApplicationYaml();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RegistrationConfiguration.class)
            .withInitializer(context -> APPLICATION_YAML
                    .forEach(source -> context.getEnvironment().getPropertySources().addLast(source)));

    @Test
    void mapsTheDeploymentEnvironmentOntoTheKeycloakRegistration() {
        runner.withPropertyValues(
                        "DASHBOARD_CLIENT_ID=dashboard",
                        "DASHBOARD_CLIENT_SECRET=secret-from-the-environment",
                        "DASHBOARD_ISSUER_URI=https://auth.example.test/realms/homelabs")
                .run(context -> {
                    KeycloakRegistration registration = context.getBean(KeycloakRegistration.class);

                    assertThat(registration.clientId()).isEqualTo("dashboard");
                    assertThat(registration.clientSecret()).isEqualTo("secret-from-the-environment");
                    assertThat(registration.provider()).isEqualTo("keycloak");
                    assertThat(registration.authorizationGrantType()).isEqualTo("authorization_code");
                    assertThat(registration.scope()).containsExactly("openid", "profile", "email");
                    assertThat(registration.redirectUri())
                            .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
                    assertThat(context.getEnvironment()
                            .getProperty("spring.security.oauth2.client.provider.keycloak.issuer-uri"))
                            .isEqualTo("https://auth.example.test/realms/homelabs");
                });
    }

    @Test
    void defaultsTheClientIdAndTheIssuerToTheLiveRealm() {
        runner.withPropertyValues("DASHBOARD_CLIENT_SECRET=secret-from-the-environment")
                .run(context -> {
                    assertThat(context.getBean(KeycloakRegistration.class).clientId()).isEqualTo("dashboard");
                    assertThat(context.getEnvironment()
                            .getProperty("spring.security.oauth2.client.provider.keycloak.issuer-uri"))
                            .isEqualTo("https://auth.homelabs.de/realms/homelabs");
                });
    }

    @Test
    void takesTheClientSecretFromTheEnvironmentOnlyInsteadOfShippingADefault() {
        assertThat(APPLICATION_YAML.getFirst().getProperty(REGISTRATION_PREFIX + ".client-secret"))
                .as("the shipped YAML must reference the environment variable, with no fallback value")
                .isEqualTo("${DASHBOARD_CLIENT_SECRET}");
    }

    private static List<PropertySource<?>> loadApplicationYaml() {
        try {
            return new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The registration as the OAuth2 client auto-configuration binds it. */
    @ConfigurationProperties(REGISTRATION_PREFIX)
    record KeycloakRegistration(String provider, String clientId, String clientSecret,
            String authorizationGrantType, List<String> scope, String redirectUri) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KeycloakRegistration.class)
    static class RegistrationConfiguration {
    }
}
