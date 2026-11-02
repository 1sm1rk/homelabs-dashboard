package de.homelabs.dashboard.shell;

import java.net.URI;

/**
 * One entry of the shell's module registry: where the module appears in the rail
 * ({@code navLabel}, {@code basePath}) and which REST base URL its client talks to.
 *
 * <p>The descriptor deliberately carries no view information: the module's pages and
 * htmx fragments live in that module's own template package, so the registry only
 * decides what the shell shows.
 *
 * @param id        stable identifier, used by the module's own code (never rendered)
 * @param navLabel  label of the rail entry
 * @param basePath  path prefix of the module inside the dashboard (e.g. {@code /projects})
 * @param apiBaseUrl REST base URL of the backend the module renders from
 */
public record ModuleDescriptor(String id, String navLabel, String basePath, URI apiBaseUrl) {
}
