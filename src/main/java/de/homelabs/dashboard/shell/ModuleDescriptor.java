package de.homelabs.dashboard.shell;

import java.net.URI;

/**
 * One entry of the shell's module registry: where the module appears in the rail
 * ({@code navKey}, {@code basePath}) and which REST base URL its client talks to.
 *
 * <p>The descriptor deliberately carries no view information: the module's pages and
 * htmx fragments live in that module's own template package, so the registry only
 * decides what the shell shows.
 *
 * @param id        stable identifier, used by the module's own code (never rendered)
 * @param navKey    message key of the rail entry ({@code module.projects.nav}), so the
 *                  label renders in the reader's language — the descriptor names the
 *                  key, the bundles carry the text
 * @param basePath  path prefix of the module inside the dashboard (e.g. {@code /projects})
 * @param apiBaseUrl REST base URL of the backend the module renders from
 */
public record ModuleDescriptor(String id, String navKey, String basePath, URI apiBaseUrl) {
}
