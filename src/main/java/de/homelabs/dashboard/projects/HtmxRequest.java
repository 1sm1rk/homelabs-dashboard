package de.homelabs.dashboard.projects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * How the module tells an htmx swap from a page load: htmx sends {@code HX-Request: true}
 * with every request it issues, so the same route can answer with the whole page or with
 * the fragment to swap.
 */
final class HtmxRequest {

    private static final String REQUEST_HEADER = "HX-Request";

    static boolean isFragment(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(REQUEST_HEADER));
    }

    private HtmxRequest() {
    }
}
