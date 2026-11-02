package de.homelabs.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The homelab's single browser surface: one server-rendered shell (modules,
 * authentication, layout) around one rendering package per backend module. The
 * backends stay pure REST/JSON — the dashboard renders everything.
 *
 * <p>Stateless by design: no database, no files of its own; a session exists in memory
 * only.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }
}
