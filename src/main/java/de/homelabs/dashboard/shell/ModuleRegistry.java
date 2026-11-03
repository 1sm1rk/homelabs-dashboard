package de.homelabs.dashboard.shell;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * The shell's single source of navigation: the rail is rendered from this list, so a
 * new module costs one descriptor plus its rendering package — never a template
 * change and never a module-specific branch in the layout.
 *
 * <p>The order of {@link #modules()} is the order of the rail.
 */
@Component
public class ModuleRegistry {

    private final List<ModuleDescriptor> modules;

    public ModuleRegistry(DashboardModuleProperties properties) {
        // The module's own entry points: its overview route first. Labels are message keys
        // like the rail, so the bar renders in the reader's language without template
        // changes. The design guide declares no REST base URL — a module of the shell that
        // renders no backend data is registered like any other.
        this.modules = List.of(
                new ModuleDescriptor(
                        "projects",
                        "module.projects.nav",
                        "/projects",
                        Optional.of(properties.projects().apiUrl()),
                        List.of(new ModuleDescriptor.SubnavItem("module.projects.subnav.overview", "/projects"))),
                new ModuleDescriptor(
                        "design",
                        "module.design.nav",
                        "/design",
                        Optional.empty(),
                        List.of(new ModuleDescriptor.SubnavItem("module.design.subnav.structure", "/design"),
                                new ModuleDescriptor.SubnavItem("module.design.subnav.components",
                                        "/design/components"))));
    }

    /** The registered modules in rail order. */
    public List<ModuleDescriptor> modules() {
        return modules;
    }

    /** Looks a module up by its identifier. */
    public Optional<ModuleDescriptor> find(String id) {
        return modules.stream()
                .filter(module -> module.id().equals(id))
                .findFirst();
    }

    /**
     * The module a request path belongs to: the module whose {@code basePath} is the
     * path or one of its segments. The shell needs it to render that module's subnav —
     * a prefix match on whole segments, so {@code /projects-archive} stays outside
     * {@code /projects}.
     */
    public Optional<ModuleDescriptor> findByPath(String path) {
        return modules.stream()
                .filter(module -> isInside(module.basePath(), path))
                .findFirst();
    }

    private static boolean isInside(String basePath, String path) {
        return path != null
                && (path.equals(basePath) || path.startsWith(basePath + "/"));
    }
}
