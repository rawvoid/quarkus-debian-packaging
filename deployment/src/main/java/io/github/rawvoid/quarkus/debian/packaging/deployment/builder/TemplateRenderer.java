package io.github.rawvoid.quarkus.debian.packaging.deployment.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads classpath templates and replaces {@code ${key}} placeholders.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9._-]+)}");
    private static final String TEMPLATE_ROOT = "debian/templates/";

    private TemplateRenderer() {
    }

    public static String render(String templateName, Map<String, String> variables) {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(variables, "variables");
        String template = load(templateName);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            // Leave unknown ${...} intact so shell scripts keep runtime expansions like ${JAVA_HOME}.
            if (value == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static byte[] renderBytes(String templateName, Map<String, String> variables) {
        return render(templateName, variables).getBytes(StandardCharsets.UTF_8);
    }

    private static String load(String templateName) {
        String resource = TEMPLATE_ROOT + templateName;
        ClassLoader cl = TemplateRenderer.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing Debian template resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Debian template: " + resource, e);
        }
    }
}
