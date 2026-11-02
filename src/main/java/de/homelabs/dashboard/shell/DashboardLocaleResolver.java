package de.homelabs.dashboard.shell;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * The shell's locale chain: the persisted {@code dashboard.locale} cookie first (it
 * outlives the in-memory session and a restart), then the browser's
 * {@code Accept-Language}, then German.
 *
 * <p>The explicit {@code ?lang=} parameter is applied by
 * {@link DashboardLocaleChangeInterceptor}, which writes the cookie through this
 * resolver. Values outside the supported set never reach the chain: an unknown cookie
 * value counts as "no choice yet" and an unsupported browser language falls back to
 * German.
 */
final class DashboardLocaleResolver extends CookieLocaleResolver {

    /** Cookie that persists the chosen UI language beyond the session. */
    static final String COOKIE_NAME = "dashboard.locale";

    /** A year: the choice is a preference, not a per-visit setting. */
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    DashboardLocaleResolver() {
        super(COOKIE_NAME);
        // Path "/" and SameSite=Lax come with the constructor; the age has to be set, or
        // the cookie would die with the browser session.
        setCookieMaxAge(COOKIE_MAX_AGE);
        setDefaultLocale(SupportedLocale.GERMAN.locale());
        setDefaultLocaleFunction(DashboardLocaleResolver::acceptedSupportedLocale);
    }

    /** A cookie value outside the supported set is treated as no choice at all. */
    @Override
    protected Locale parseLocaleValue(String localeValue) {
        return SupportedLocale.byCode(localeValue)
                .map(SupportedLocale::locale)
                .orElse(null);
    }

    /** First supported language of {@code Accept-Language}, German when none matches. */
    private static Locale acceptedSupportedLocale(HttpServletRequest request) {
        for (Locale candidate : Collections.list(request.getLocales())) {
            Optional<SupportedLocale> supported = SupportedLocale.byLocale(candidate);
            if (supported.isPresent()) {
                return supported.get().locale();
            }
        }
        return SupportedLocale.GERMAN.locale();
    }
}
