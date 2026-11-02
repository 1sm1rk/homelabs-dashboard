package de.homelabs.dashboard.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two chrome bundles are one contract: the German default bundle
 * ({@code messages.properties}) and the English one ({@code messages_en.properties})
 * carry the same keys, so no chrome text can ever miss its translation and fall back
 * silently.
 */
class MessageBundleTest {

    private static final String DEFAULT_BUNDLE = "/messages.properties";
    private static final String ENGLISH_BUNDLE = "/messages_en.properties";

    @Test
    void bothBundlesDefineTheSameKeys() {
        assertThat(bundle(ENGLISH_BUNDLE).stringPropertyNames())
                .as("keys of %s", ENGLISH_BUNDLE)
                .isEqualTo(bundle(DEFAULT_BUNDLE).stringPropertyNames());
    }

    @Test
    void bothBundlesTranslateEveryKey() {
        for (String resource : List.of(DEFAULT_BUNDLE, ENGLISH_BUNDLE)) {
            Properties properties = bundle(resource);

            assertThat(properties.stringPropertyNames())
                    .as("keys of %s", resource)
                    .isNotEmpty()
                    .allSatisfy(key -> assertThat(properties.getProperty(key))
                            .as("value of %s in %s", key, resource)
                            .isNotBlank());
        }
    }

    @Test
    void germanIsTheDefaultBundle() {
        assertThat(MessageBundleTest.class.getResource("/messages_de.properties"))
                .as("German lives in the default bundle messages.properties, so an "
                        + "unresolved locale always ends in German")
                .isNull();
    }

    @Test
    void theShellMessageSourceReadsTheBundlesAsUtf8() {
        MessageSource messageSource = new ShellI18nConfiguration().messageSource();

        assertThat(messageSource.getMessage("page.overview.title", null, Locale.GERMAN))
                .isEqualTo("Übersicht");
        assertThat(messageSource.getMessage("page.overview.title", null, Locale.ENGLISH))
                .isEqualTo("Overview");
        assertThat(messageSource.getMessage("page.overview.lead", null, Locale.GERMAN))
                .isEqualTo("Eine Oberfläche für die Homelab-Dienste.");
    }

    private static Properties bundle(String resource) {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("bundle %s on the classpath", resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return properties;
    }
}
