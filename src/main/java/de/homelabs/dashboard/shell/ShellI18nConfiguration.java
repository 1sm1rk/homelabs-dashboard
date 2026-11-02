package de.homelabs.dashboard.shell;

import java.nio.charset.StandardCharsets;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Bilingual shell chrome: German is the default language, English the second one.
 *
 * <p>The bundles live next to the templates: {@code messages.properties} holds the
 * German texts (the default bundle, so a lookup never ends in another language at
 * random) and {@code messages_en.properties} mirrors it key by key — the two files are
 * one contract and must always carry the same keys.
 *
 * <p>Wiring: this {@link MessageSource} resolves the {@code #{key}} expressions of the
 * templates, {@link DashboardLocaleResolver} decides which locale a request renders in,
 * and {@link DashboardLocaleChangeInterceptor} applies an explicit {@code ?lang=}
 * switch. The shell's pages therefore contain no chrome text of their own.
 */
@Configuration
public class ShellI18nConfiguration implements WebMvcConfigurer {

    /** Bundle base name; the unsuffixed bundle is the German default, the suffix is {@code _en}. */
    static final String BUNDLE_BASENAME = "messages";

    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename(BUNDLE_BASENAME);
        // Explicit, not inherited: the bundles are UTF-8, like every source file here.
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // A key missing from one bundle resolves in the German default bundle — never in
        // whatever locale the JVM happens to run in.
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    LocaleResolver localeResolver() {
        return new DashboardLocaleResolver();
    }

    @Bean
    LocaleChangeInterceptor localeChangeInterceptor() {
        return new DashboardLocaleChangeInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
