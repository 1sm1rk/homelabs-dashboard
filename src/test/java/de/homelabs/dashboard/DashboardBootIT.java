package de.homelabs.dashboard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import de.homelabs.dashboard.shell.ModuleDescriptor;
import de.homelabs.dashboard.shell.ModuleRegistry;
import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot smoke test: the context starts on a random port and the login gate is really
 * there — the liveness probe answers without a session, every page request does not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StubClientRegistrationRepository.class)
class DashboardBootIT {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ModuleRegistry registry;

    @Test
    void servesTheOpenHealthProbe() throws Exception {
        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"", "homelabs-dashboard");
    }

    @Test
    void redirectsAnUnauthenticatedPageRequestToTheLogin() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .get()
                .asString()
                .startsWith("http://localhost:")
                .contains("/oauth2/authorization/" + StubClientRegistrationRepository.REGISTRATION_ID);
    }

    @Test
    void redirectsTheDesignGuideToTheLogin() throws Exception {
        for (String path : new String[] { "/design", "/design/components" }) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as("status of %s", path).isEqualTo(302);
            assertThat(response.headers().firstValue("Location"))
                    .get()
                    .asString()
                    .as("redirect of %s", path)
                    .contains("/oauth2/authorization/" + StubClientRegistrationRepository.REGISTRATION_ID);
        }
    }

    @Test
    void wiresTheProjectsModuleFromTheConfiguredApiUrl() {
        ModuleDescriptor projects = registry.find("projects").orElseThrow();

        assertThat(projects.apiBaseUrl()).contains(URI.create("http://localhost:8093"));
    }

    @Test
    void registersTheDesignGuideWithoutABackend() {
        ModuleDescriptor design = registry.find("design").orElseThrow();

        assertThat(design.basePath()).isEqualTo("/design");
        assertThat(design.apiBaseUrl()).isEmpty();
        assertThat(design.subnav()).extracting(ModuleDescriptor.SubnavItem::path)
                .containsExactly("/design", "/design/components");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
