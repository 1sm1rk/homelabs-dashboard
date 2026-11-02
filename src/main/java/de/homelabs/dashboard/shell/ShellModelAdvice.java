package de.homelabs.dashboard.shell;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Feeds the layout fragments on every rendered page: the rail entries from the module
 * registry and the signed-in identity for the topbar.
 *
 * <p>Global rather than per-controller because module pages and their htmx fragments
 * both render through the same layout — the shell must not depend on which module is
 * currently answering.
 */
@ControllerAdvice
public class ShellModelAdvice {

    private final ModuleRegistry registry;

    public ShellModelAdvice(ModuleRegistry registry) {
        this.registry = registry;
    }

    @ModelAttribute("modules")
    public List<ModuleDescriptor> modules() {
        return registry.modules();
    }

    @ModelAttribute("currentUser")
    public String currentUser(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
