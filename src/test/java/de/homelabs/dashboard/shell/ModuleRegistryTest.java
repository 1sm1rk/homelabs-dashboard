package de.homelabs.dashboard.shell;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The module registry is the shell's navigation contract: one descriptor per module,
 * rail order = list order, the REST base URL taken from the module properties, and the
 * active module resolved from the request path so the shell knows whose subnav to show.
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

    @Test
    void projectsDeclaresItsOverviewEntry() {
        assertThat(registry.find("projects").orElseThrow().subnav())
                .extracting(ModuleDescriptor.SubnavItem::labelKey, ModuleDescriptor.SubnavItem::path)
                .containsExactly(tuple("module.projects.subnav.overview", "/projects"));
    }

    @Test
    void resolvesTheActiveModuleFromTheRequestPath() {
        assertThat(registry.findByPath("/projects"))
                .get()
                .extracting(ModuleDescriptor::id)
                .isEqualTo("projects");
        assertThat(registry.findByPath("/projects/HP-1")).isPresent();
        assertThat(registry.findByPath("/projects-archive")).isEmpty();
        assertThat(registry.findByPath("/")).isEmpty();
        assertThat(registry.findByPath(null)).isEmpty();
    }

    @Test
    void keepsTheDeclaredSubnavOrderAndRejectsAnEmptyPlaceholder() {
        ModuleDescriptor withSubnav = new ModuleDescriptor("projects", "module.projects.nav", "/projects",
                PROJECTS_API,
                List.of(new ModuleDescriptor.SubnavItem("module.projects.subnav.list", "/projects"),
                        new ModuleDescriptor.SubnavItem("module.projects.subnav.new", "/projects/new")));
        ModuleDescriptor withoutSubnav = new ModuleDescriptor("projects", "module.projects.nav", "/projects",
                PROJECTS_API, null);

        assertThat(withSubnav.subnav()).extracting(ModuleDescriptor.SubnavItem::labelKey)
                .containsExactly("module.projects.subnav.list", "module.projects.subnav.new");
        assertThat(withoutSubnav.subnav()).isEmpty();
    }
}
