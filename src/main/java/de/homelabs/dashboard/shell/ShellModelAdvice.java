package de.homelabs.dashboard.shell;

import java.security.Principal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Feeds the layout fragments on every rendered page: the rail entries from the module
 * registry, the signed-in identity for the topbar and the locale state of the language
 * switch.
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

    /** Active locale of the request as a plain language code ({@code de} / {@code en}). */
    @ModelAttribute("currentLocale")
    public String currentLocale() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    /**
     * Path the language switch links keep: switching the language stays on the page the
     * reader is on instead of dropping back to the shell's home page.
     */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
