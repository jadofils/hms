package amalitech.hospital.management.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads an HTML file from {@code templates/email/} and substitutes {@code {{key}}}
 * placeholders with caller-supplied values.
 *
 * Single responsibility, on purpose: this class knows nothing about SMTP or which
 * template a given email type uses — that's {@code EmailAspect}'s job (deciding
 * *which* template and *what* goes in it) and {@code JavaMailSender}'s job (actually
 * sending). This class only ever turns a template name + a value map into a final
 * HTML string. Templates are cached in memory after first load — they don't change at
 * runtime, so re-reading the classpath resource on every send would be pure overhead.
 */
@Component
public class EmailTemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateRenderer.class);

    private static final String TEMPLATE_PATH_PREFIX = "templates/email/";
    private static final String TEMPLATE_PATH_SUFFIX = ".html";

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    // Fires for every templated email, called by EmailAspect.executeSendTemplatedEmail
    // after it resolves which MailService template/vars a given @SendTemplatedEmail case needs.
    public String render(String templateName, Map<String, String> variables) {
        log.debug("EmailTemplateRenderer.render invoked — called by EmailAspect.executeSendTemplatedEmail");
        String template = templateCache.computeIfAbsent(templateName, this::load);
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    // Fires only on a cache miss, called from render above (via computeIfAbsent) the
    // first time a given template name is requested.
    private String load(String templateName) {
        log.debug("EmailTemplateRenderer.load invoked — called by EmailTemplateRenderer.render on a template cache miss");
        String path = TEMPLATE_PATH_PREFIX + templateName + TEMPLATE_PATH_SUFFIX;
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing email template: " + path, e);
        }
    }
}