package de.homelabs.dashboard.projects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.ModelAndView;

import de.homelabs.dashboard.shell.AccessTokenProvider;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the backend's answers into the module's surfaces, mirroring its error style
 * instead of inventing one:
 *
 * <ul>
 *   <li>401 — the token the dashboard forwarded was not accepted: back into the login
 *       round-trip, because only a fresh session can fix that.</li>
 *   <li>404 — a localized not-found notice; the reference the reader typed may simply not
 *       exist.</li>
 *   <li>400 / 409 — the backend's own message rendered inline, verbatim: field problems
 *       name the wire field and a read-only conflict explains itself.</li>
 *   <li>anything else, and an unreachable backend — a localized upstream notice with the
 *       technical message next to it.</li>
 * </ul>
 *
 * <p>Only this module's controller is advised: the shell's own routes keep their
 * behaviour.
 */
@ControllerAdvice(assignableTypes = ProjectsController.class)
class ProjectsUpstreamErrorHandler {

    private static final String VIEW = "projects/error";

    private static final String FRAGMENT = VIEW + " :: notice";

    private final ObjectMapper objectMapper;

    ProjectsUpstreamErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(RestClientResponseException.class)
    ModelAndView handleUpstream(RestClientResponseException exception, HttpServletRequest request,
            HttpServletResponse response) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new ModelAndView("redirect:/oauth2/authorization/" + AccessTokenProvider.REGISTRATION_ID);
        }
        String headingKey = switch (status) {
            case 404 -> "projects.error.notFound";
            case 400 -> "projects.error.badRequest";
            case 409 -> "projects.error.conflict";
            default -> "projects.error.upstream";
        };
        return notice(headingKey, upstreamMessage(exception), request, response);
    }

    @ExceptionHandler(ResourceAccessException.class)
    ModelAndView handleUnreachable(ResourceAccessException exception, HttpServletRequest request,
            HttpServletResponse response) {
        return notice("projects.error.upstream", exception.getMessage(), request, response);
    }

    private ModelAndView notice(String headingKey, String detail, HttpServletRequest request,
            HttpServletResponse response) {
        ModelAndView view = new ModelAndView(HtmxRequest.isFragment(request) ? FRAGMENT : VIEW);
        view.addObject("headingKey", headingKey);
        view.addObject("detail", detail);
        if (HtmxRequest.isFragment(request)) {
            // The notice is a page-level block, not a row: send the swap to the working
            // area so an htmx call from a filter form or a section link lands sensibly.
            response.setHeader("HX-Retarget", "#main");
            response.setHeader("HX-Reswap", "innerHTML");
        }
        return view;
    }

    /** The backend's {@code {"error": …}} message; the raw body when it is not that shape. */
    private String upstreamMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getStatusText();
        }
        try {
            ApiError error = objectMapper.readValue(body, ApiError.class);
            return error.error() == null ? body : error.error();
        }
        catch (RuntimeException unreadable) {
            return body;
        }
    }

    /** The shared error shape of the projects API. */
    record ApiError(String error) {
    }
}
