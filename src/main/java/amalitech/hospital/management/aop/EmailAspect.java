package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.SendTemplatedEmail;
import amalitech.hospital.management.utils.EmailTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and sends the HTML email selected by {@code @SendTemplatedEmail}'s
 * {@code value()}, replacing the annotated {@code MailService} method's own body
 * entirely.
 *
 * Dynamic values (recipient, OTP/token, expiry, etc.) come from the annotated method's
 * own runtime arguments, read via {@link ProceedingJoinPoint#getArgs()} — not from
 * annotation attributes, for the same reason as {@code AlgorithmAspect}/
 * {@code FindUserDataAspect}: a caller only knows these at request time, and annotation
 * attribute values are fixed at compile time.
 *
 * Every send is best-effort: a mail failure never propagates to the caller (matching
 * the original {@code MailService.sendPasswordResetEmail} semantics — forgot-password,
 * in particular, must behave identically to the caller whether or not the email
 * actually goes out, to avoid leaking account existence).
 */
@Aspect
@Component
@RequiredArgsConstructor
public class EmailAspect {

    private static final Logger log = LoggerFactory.getLogger(EmailAspect.class);
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
    /** Every template variable key repeated across every {@code case} below. */
    private static final String VAR_RECIPIENT_NAME = "recipientName";

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail-from-name}")
    private String fromName;

    @Value("${app.frontend-base-url}")
    private String appUrl;

    @Value("${app.support-email}")
    private String supportEmail;

    @Around("@annotation(sendTemplatedEmail)")
    public Object executeSendTemplatedEmail(ProceedingJoinPoint pjp, SendTemplatedEmail sendTemplatedEmail) {
        Object[] args = pjp.getArgs();
        String templateName = sendTemplatedEmail.value();

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("appUrl", appUrl);
        vars.put("supportEmail", supportEmail);
        vars.put("year", String.valueOf(Year.now(ZoneOffset.UTC).getValue()));

        String toEmail;
        String subject;
        // The annotation value is a short Java-method-style identifier ("otp",
        // "passwordChanged", ...); the file on disk is descriptively kebab-cased
        // ("otp-verification.html", "password-changed.html", ...) — deliberately not
        // the same string, so it's resolved per case rather than reused as the
        // template name directly.
        String templateFile;

        switch (templateName) {
            case "otp" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                vars.put("otpCode", (String) args[2]);
                vars.put("expiryHours", String.valueOf(args[3]));
                vars.put("expiryDate", ((LocalDateTime) args[4]).format(DISPLAY_FORMAT));
                subject = "Your HMS verification code";
                templateFile = "otp-verification";
            }
            case "passwordReset" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                vars.put("resetToken", (String) args[2]);
                vars.put("resetUrl", (String) args[3]);
                vars.put("expiryMinutes", String.valueOf(args[4]));
                subject = "Reset your HMS password";
                templateFile = "password-reset";
            }
            case "emailVerification" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                vars.put("verifyUrl", (String) args[2]);
                vars.put("expiryHours", String.valueOf(args[3]));
                subject = "Verify your HMS email address";
                templateFile = "email-verification";
            }
            case "accountCreated" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                vars.put("username", (String) args[1]);
                vars.put("generatedPassword", (String) args[2]);
                subject = "Your HMS account has been created";
                templateFile = "account-created";
            }
            case "passwordChanged" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                vars.put("changedAt", ((LocalDateTime) args[2]).format(DISPLAY_FORMAT));
                subject = "Your HMS password was changed";
                templateFile = "password-changed";
            }
            case "generic" -> {
                toEmail = (String) args[0];
                vars.put(VAR_RECIPIENT_NAME, (String) args[1]);
                subject = (String) args[2];
                vars.put("heading", (String) args[3]);
                vars.put("bodyHtml", (String) args[4]);
                vars.put("ctaLabel", (String) args[5]);
                vars.put("ctaUrl", (String) args[6]);
                templateFile = "generic-notification";
            }
            default -> throw new IllegalStateException("Unknown email template: " + templateName);
        }

        String html = templateRenderer.render(templateFile, vars);
        send(toEmail, subject, html, templateName);
        return null;
    }

    private void send(String toEmail, String subject, String html, String templateName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom(fromAddress, fromName);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("Failed to send '{}' email to {}: {}", templateName, toEmail, e.getMessage());
        }
    }
}