package de.homelabs.dashboard.shell;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open liveness probe for the container healthcheck: it reports no dashboard state, so
 * it needs no session. Every other route is behind the login gate.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "homelabs-dashboard");
    }

    public record HealthResponse(String status, String service) {
    }
}
