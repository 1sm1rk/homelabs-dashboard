package de.homelabs.dashboard.shell;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Feeds the layout fragments on every rendered page: the rail entries from the module
 * registry, the subnav of the module the route belongs to, the signed-in identity for
 * the topbar, the locale state of the language switch and the CSRF token every htmx
 * request carries.
 *
 * <p>Global rather than per-controller because module pages and their htmx fragments
 * both render through the same layout — the shell must not depend on which module is
 * currently answering.
 */
@ControllerAdvice
public class ShellModelAdvice {

    /** Realm claim with the human-readable user name; Keycloak always sends it. */
    static final String PREFERRED_USERNAME_CLAIM = "preferred_username";

    /** Fallback claim for realms that only send the full name. */
    static final String NAME_CLAIM = "name";

    /** Request attribute the CSRF filter puts the session's token under. */
    static final String CSRF_ATTRIBUTE = "_csrf";

    private final ModuleRegistry registry;

    public ShellModelAdvice(ModuleRegistry registry) {
        this.registry = registry;
    }

    /**
     * The CSRF token as the {@code hx-headers} JSON of the page body: the token's own
     * header name and a value the security chain accepts, so every htmx POST and PATCH
     * carries it without a token of its own — the attribute is inherited from
     * {@code <body>} into every fragment swapped inside it.
     *
     * <p>{@code null} when the request has no token (no security chain, no session): the
     * attribute is then left out entirely instead of rendering an empty header.
     */
    @ModelAttribute("csrfHeaders")
    public String csrfHeaders(HttpServletRequest request) {
        if (!(request.getAttribute(CSRF_ATTRIBUTE) instanceof CsrfToken token) || token.getToken() == null) {
            return null;
        }
        return "{\"" + token.getHeaderName() + "\":\"" + token.getToken() + "\"}";
    }

    @ModelAttribute("modules")
    public List<ModuleDescriptor> modules() {
        return registry.modules();
    }

    /**
     * The module the request belongs to, or {@code null} on shell routes (home, health):
     * the layout renders a subnav only for the active module, so a module that declares
     * none leaves no bar behind.
     */
    @ModelAttribute("activeModule")
    public ModuleDescriptor activeModule(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return registry.findByPath(path).orElse(null);
    }

    /**
     * Identity line of the topbar: the display name of the signed-in user, taken from the
     * OIDC principal — {@code preferred_username} first, then {@code name}, then the raw
     * principal name (the realm's {@code sub} key, the last resort the login hands over).
     *
     * <p>An anonymous request renders no identity at all: the login gate lets only
     * {@code /health} through, and the permit-all seam of the rendering tests must not
     * grow an "anonymousUser" into the chrome.
     */
    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return firstPresent(
                    claim(oidcUser, PREFERRED_USERNAME_CLAIM),
                    claim(oidcUser, NAME_CLAIM),
                    authentication.getName());
        }
        return authentication.getName();
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

    private static String claim(OidcUser oidcUser, String name) {
        return oidcUser.getClaims().get(name) instanceof String text && !text.isBlank() ? text : null;
    }

    private static String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
