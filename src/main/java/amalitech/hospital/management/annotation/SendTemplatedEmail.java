package amalitech.hospital.management.annotation;

import java.lang.annotation.*;

/**
 * Marks a {@code MailService} method as an HTML-templated email send, intercepted and
 * fully executed by {@code EmailAspect} — see the "AOP conventions" section of
 * CLAUDE.md for the pattern this follows.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SendTemplatedEmail {
    /**
     * Which template to render and send: "otp" | "passwordReset" | "passwordChanged" |
     * "generic". Selects both the {@code templates/email/*.html} file and how the
     * annotated method's own runtime arguments are mapped to template placeholders.
     */
    String value();
}