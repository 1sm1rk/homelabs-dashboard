package de.homelabs.dashboard.shell;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module registry is the shell's navigation contract: one descriptor per module,
 * rail order = list order, the REST base URL taken from the module properties.
 */
class ModuleRegistryTest {

    private static final URI PROJECTS_API = URI.create("http://projects:8090");

    private final ModuleRegistry registry = new ModuleRegistry(
            new DashboardModuleProperties(new DashboardModuleProperties.Projects(PROJECTS_API)));

    @Test
    void registersTheProjectsModule() {
        List<ModuleDescriptor> modules = registry.modules();

        assertThat(modules).hasSize(1);
        ModuleDescriptor projects = modules.getFirst();
        assertThat(projects.id()).isEqualTo("projects");
        assertThat(projects.navKey()).isEqualTo("module.projects.nav");
        assertThat(projects.basePath()).isEqualTo("/projects");
    }

    @Test
    void takesTheRestBaseUrlFromTheModuleProperties() {
        assertThat(registry.find("projects"))
                .get()
                .extracting(ModuleDescriptor::apiBaseUrl)
                .isEqualTo(PROJECTS_API);
    }

    @Test
    void findsNoModuleForAnUnknownId() {
        assertThat(registry.find("hippocampus")).isEmpty();
    }
}
