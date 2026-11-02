package de.homelabs.dashboard.shell;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment data of the module registry: the REST base URL of every backend module.
 *
 * <p>One property per module, mapped from the environment in {@code application.yml}
 * (hippocampus convention), so pointing a module at another backend is configuration,
 * never a code change.
 *
 * @param projects module 1, the project-management backend
 */
@ConfigurationProperties("homelabs.dashboard.modules")
public record DashboardModuleProperties(Projects projects) {

    /**
     * @param apiUrl base URL of the module's REST API (no trailing slash expected; the
     *               module's client appends the endpoint paths)
     */
    public record Projects(URI apiUrl) {
    }
}
