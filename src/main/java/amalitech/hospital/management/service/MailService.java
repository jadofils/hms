package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.SendTemplatedEmail;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Entry points for every outbound HTML email in the system — thin annotated stubs only.
 * {@code @SendTemplatedEmail} on each method is intercepted by {@code EmailAspect}
 * (see {@code aop/EmailAspect}), which renders the matching {@code templates/email/*.html}
 * file and sends it; the bodies below never run. This is the same annotate-a-method /
 * let-the-aspect-do-the-work shape as {@code @ApplyAlgorithm}/{@code @FindUserData}/
 * {@code @SqlQueryBuilder} — see CLAUDE.md's AOP conventions section.
 *
 * Every caller of these methods is external (AuthService today), never this class
 * calling itself — so, unlike UserService/RoleService's self-injection pattern, no
 * self-reference is needed here for the aspect to fire correctly.
 */
@Service
public class MailService {

    /** OTP/verification code — {@code expiryHours} is caller-supplied so any future
     *  verification flow (2FA, email confirmation, ...) can set its own validity window;
     *  the "generated OTP expires 48 hours" requirement maps to callers passing 48. */
    @SendTemplatedEmail("otp")
    public void sendOtpEmail(String toEmail, String recipientName, String otpCode, int expiryHours, LocalDateTime expiresAt) {
        throw new IllegalStateException("EmailAspect did not intercept this call");
    }

    /** Password reset — {@code resetUrl} deep-links into the frontend with the token
     *  pre-filled; {@code resetToken} is also shown as a fallback for API-only clients. */
    @SendTemplatedEmail("passwordReset")
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetToken, String resetUrl, int expiryMinutes) {
        throw new IllegalStateException("EmailAspect did not intercept this call");
    }

    /** Password changed confirmation — sent after both {@code resetPassword} and
     *  {@code changePassword} succeed, so an attacker changing a compromised password
     *  can't do so silently. */
    @SendTemplatedEmail("passwordChanged")
    public void sendPasswordChangedEmail(String toEmail, String recipientName, LocalDateTime changedAt) {
        throw new IllegalStateException("EmailAspect did not intercept this call");
    }

    /** General-purpose notification — the one template every other domain reuses for
     *  one-off communications that don't warrant a dedicated template of their own. */
    @SendTemplatedEmail("generic")
    public void sendNotificationEmail(String toEmail, String recipientName, String subject, String heading, String bodyHtml, String ctaLabel, String ctaUrl) {
        throw new IllegalStateException("EmailAspect did not intercept this call");
    }
}