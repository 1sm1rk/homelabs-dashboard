package de.homelabs.dashboard.shell;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * One entry of the shell's module registry: where the module appears in the rail
 * ({@code navKey}, {@code basePath}), which REST base URL its client talks to and — if
 * the module has one — its subnav.
 *
 * <p>The descriptor deliberately carries no view information: the module's pages and
 * htmx fragments live in that module's own template package, so the registry only
 * decides what the shell shows.
 *
 * <p>A module without a backend is a first-class entry: {@code apiBaseUrl} is empty for
 * it, and the shell renders it exactly like a module that talks to a REST API. The REST
 * base URL belongs to the module's own client, not to the navigation.
 *
 * @param id         stable identifier, used by the module's own code (never rendered)
 * @param navKey     message key of the rail entry ({@code module.projects.nav}), so the
 *                   label renders in the reader's language — the descriptor names the
 *                   key, the bundles carry the text
 * @param basePath   path prefix of the module inside the dashboard (e.g. {@code /projects})
 * @param apiBaseUrl REST base URL of the backend the module renders from; empty when the
 *                   module has no backend of its own
 * @param subnav     entries the shell renders as a second bar below the topbar while a
 *                   route of this module is open; empty means the module has no subnav
 */
public record ModuleDescriptor(String id, String navKey, String basePath, Optional<URI> apiBaseUrl,
        List<SubnavItem> subnav) {

    public ModuleDescriptor {
        apiBaseUrl = apiBaseUrl == null ? Optional.empty() : apiBaseUrl;
        subnav = subnav == null ? List.of() : List.copyOf(subnav);
    }

    /** A module without a subnav: the shell renders no bar at all, not an empty one. */
    public ModuleDescriptor(String id, String navKey, String basePath, Optional<URI> apiBaseUrl) {
        this(id, navKey, basePath, apiBaseUrl, List.of());
    }

    /**
     * One entry of a module's subnav — the same contract as the rail: a message key plus
     * the dashboard path, so the label is translated and the shell stays free of
     * module-specific template code.
     *
     * @param labelKey message key of the entry, never the literal text
     * @param path     dashboard path the entry links to
     */
    public record SubnavItem(String labelKey, String path) {
    }
}
