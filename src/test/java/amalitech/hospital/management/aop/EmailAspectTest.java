package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.SendTemplatedEmail;
import amalitech.hospital.management.service.MailService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link EmailAspect} through the real Spring AOP proxy — calling
 * {@link MailService}'s real (Spring-managed) bean, so {@code @Around("@annotation(...)")}
 * actually has to intercept for real, per CLAUDE.md's Testing section (aspects need a
 * real proxy, not a plain Mockito unit test). Only {@link JavaMailSender} is mocked —
 * everything else (template resolution/rendering, the switch on template name) runs for
 * real, it just never reaches an actual SMTP server.
 */
// management.health.mail.enabled=false: Actuator's MailHealthContributorAutoConfiguration
// tries to wrap every JavaMailSender bean into a composite health indicator at startup;
// @MockitoBean's bean-override mechanism trips it up ("'beans' must not be empty"), so
// it's disabled here specifically — unrelated to what this test actually exercises.
@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
class EmailAspectTest {

    @Autowired
    private MailService mailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void sendOtpEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendOtpEmail("test@example.com", "Ada", "123456", 48, LocalDateTime.now().plusHours(48));

        verify(mailSender, atLeastOnce()).createMimeMessage();
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordResetEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendPasswordResetEmail("test@example.com", "Ada", "reset-token", "https://app/reset?token=x", 30);

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordChangedEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendPasswordChangedEmail("test@example.com", "Ada", LocalDateTime.now());

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void sendNotificationEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendNotificationEmail("test@example.com", "Ada", "Subject", "Heading",
                "<p>Body</p>", "Open HMS", "https://app");

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmailVerificationEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendEmailVerificationEmail("test@example.com", "Ada", "https://app/verify-email?token=x", 24);

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void sendGeneratedPasswordEmail_rendersRealTemplateAndSendsThroughTheRealProxy() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

        mailService.sendGeneratedPasswordEmail("test@example.com", "Ada", "Tr0ub4dor&3");

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void send_neverPropagates_whenJavaMailSenderThrows() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));

        // EmailAspect.send() catches MailException and just logs — must never propagate,
        // since every real caller (AuthService) treats "email attempted" as best-effort.
        mailService.sendPasswordChangedEmail("test@example.com", "Ada", LocalDateTime.now());
    }

    /** None of {@link MailService}'s 6 real methods can ever hit the aspect's
     *  unknown-template default branch — every {@code @SendTemplatedEmail} value in
     *  production code is one of the 6 known cases. A test-local bean stands in as
     *  "the next real caller" purely so that defensive branch is covered too. */
    @Test
    void unknownTemplateValue_throwsIllegalState() {
        assertThatThrownBy(bean::sendWithUnknownTemplate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown email template");
    }

    @Autowired
    private TestEmailBean bean;

    @TestConfiguration
    static class Config {
        @Bean
        TestEmailBean testEmailBean() {
            return new TestEmailBean();
        }
    }

    public static class TestEmailBean {
        @SendTemplatedEmail("bogus-template")
        public void sendWithUnknownTemplate() {
            throw new IllegalStateException("EmailAspect did not intercept this call");
        }
    }
}
