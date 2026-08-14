package amalitech.hospital.management.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises real classpath files under {@code templates/email/} — not mocked. This is
 * exactly the gap that let {@code EmailAspect} ship passing a raw annotation value
 * ("generic") as a template file name that didn't match any actual file
 * ("generic-notification.html") until it blew up in a running app: MailService/
 * AuthService/UserService unit tests all mock MailService itself, so nothing ever
 * asked EmailTemplateRenderer to load a real file. Keep every file name EmailAspect
 * resolves to covered here.
 */
class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    /** Every template file EmailAspect's switch resolves a templateName to — see its
     *  templateFile assignments. If this list and EmailAspect's ever drift apart, one
     *  of the two now has a template that either doesn't exist or isn't covered here. */
    @ParameterizedTest
    @ValueSource(strings = {"otp-verification", "password-reset", "password-changed", "generic-notification"})
    void render_loadsRealTemplateFile_andSubstitutesPlaceholders(String templateName) {
        String html = renderer.render(templateName, Map.of("recipientName", "Ada Lovelace"));

        assertThat(html).contains("Ada Lovelace");
        assertThat(html).doesNotContain("{{recipientName}}");
        assertThat(html).contains("<!DOCTYPE html>");
    }

    @Test
    void render_throwsIllegalState_whenTemplateFileDoesNotExist() {
        assertThatThrownBy(() -> renderer.render("does-not-exist", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void render_leavesUnknownPlaceholdersAlone_whenNoValueSupplied() {
        String html = renderer.render("password-changed", Map.of("recipientName", "Ada"));

        // changedAt/appUrl/etc. weren't supplied — they should still be in the output
        // as literal placeholders, not blanked out or throw.
        assertThat(html).contains("{{changedAt}}");
    }
}