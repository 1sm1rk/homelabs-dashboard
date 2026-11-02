package de.homelabs.dashboard.shell;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Applies the {@code ?lang=de|en} parameter of the topbar language switch and lets the
 * {@link DashboardLocaleResolver} persist the choice in the {@code dashboard.locale}
 * cookie.
 *
 * <p>Only the supported language codes switch anything. Any other value is ignored —
 * the request keeps the locale it already resolved (cookie, {@code Accept-Language},
 * German), so a stale or hand-edited link never puts the shell into a language without
 * a bundle.
 */
final class DashboardLocaleChangeInterceptor extends LocaleChangeInterceptor {

    DashboardLocaleChangeInterceptor() {
        setParamName(SupportedLocale.PARAM_NAME);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws jakarta.servlet.ServletException {
        String requested = request.getParameter(getParamName());
        if (requested != null && SupportedLocale.byCode(requested).isEmpty()) {
            logger.debug("Ignoring unsupported locale parameter '" + requested + "'");
            return true;
        }
        return super.preHandle(request, response, handler);
    }
}
