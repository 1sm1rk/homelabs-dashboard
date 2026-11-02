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
        this.modules = List.of(new ModuleDescriptor(
                "projects",
                "module.projects.nav",
                "/projects",
                properties.projects().apiUrl()));
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
}
