package de.homelabs.dashboard.shell;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module properties are deployment data, and the registry is their only consumer:
 * the configured REST base URL has to reach the descriptor the shell renders from.
 */
class DashboardModulePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withBean(ModuleRegistry.class);

    @Test
    void bindsTheProjectsApiUrl() {
        runner.withPropertyValues("homelabs.dashboard.modules.projects.api-url=http://projects:8090")
                .run(context -> {
                    DashboardModuleProperties properties = context.getBean(DashboardModuleProperties.class);

                    assertThat(properties.projects().apiUrl()).isEqualTo(URI.create("http://projects:8090"));
                });
    }

    @Test
    void wiresTheRegistryFromTheBoundProperties() {
        runner.withPropertyValues("homelabs.dashboard.modules.projects.api-url=http://localhost:8093")
                .run(context -> {
                    ModuleRegistry registry = context.getBean(ModuleRegistry.class);

                    assertThat(registry.find("projects").orElseThrow().apiBaseUrl())
                            .contains(URI.create("http://localhost:8093"));
                });
    }

    @EnableConfigurationProperties(DashboardModuleProperties.class)
    static class TestApplication {
    }
}
