package de.homelabs.dashboard.shell;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The languages the shell's chrome is available in. German is the default: the default
 * bundle {@code messages.properties} is German, {@code messages_en.properties} mirrors
 * it.
 *
 * <p>These are the only values the locale chain accepts — as {@code ?lang=} parameter, as
 * {@code dashboard.locale} cookie and as {@code Accept-Language} match. Adding a language
 * means one constant here plus one bundle file; the shell's templates stay untouched
 * because they carry message keys, not texts.
 */
enum SupportedLocale {

    GERMAN("de", Locale.GERMAN),
    ENGLISH("en", Locale.ENGLISH);

    /** Request parameter of the topbar language switch (LocaleChangeInterceptor convention). */
    static final String PARAM_NAME = "lang";

    private final String code;
    private final Locale locale;

    SupportedLocale(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    /** Exact language code, as written in the switch links, the cookie and the parameter. */
    String code() {
        return code;
    }

    /** The locale the bundles are resolved for. */
    Locale locale() {
        return locale;
    }

    /** Looks a language code up; anything outside the supported set has no match. */
    static Optional<SupportedLocale> byCode(String code) {
        return Arrays.stream(values())
                .filter(supported -> supported.code.equals(code))
                .findFirst();
    }

    /** Looks a request locale up by language, so {@code de-AT} still resolves to German. */
    static Optional<SupportedLocale> byLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(supported -> supported.locale.getLanguage().equals(locale.getLanguage()))
                .findFirst();
    }
}
