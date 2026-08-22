package amalitech.hospital.management.docs;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the CSRF token mechanism demo at {@code GET /docs/csrf-demo} (HMS v4, Epic 3.1) —
 * see {@code amalitech.hospital.management.config.security.CsrfDemoSecurityConfig} for why
 * this one path (and only this one) still has Spring Security's default CSRF protection
 * turned on, unlike the rest of this stateless JWT API.
 *
 * <p>The page renders two forms POSTing to the same {@code /docs/csrf-demo} endpoint: one
 * carries the real, session-bound token Spring Security handed this GET request (hidden
 * input, read off the {@code CsrfToken} request attribute {@link org.springframework.security.web.csrf.CsrfFilter}
 * always populates); the other is missing it entirely, standing in for a forged
 * cross-site submission. Submitting the first reaches {@link #submit} and renders a
 * success banner; submitting the second never reaches this controller at all — Spring
 * Security's {@code CsrfFilter} itself rejects it with a 403 before dispatch, which is the
 * actual mechanism being illustrated, not something this class implements itself.
 *
 * <p>A plain {@code @Controller} returning view names, same shape as
 * {@link ApiComparisonController} — see that class's own Javadoc for why this is a
 * {@code docs}-package Thymeleaf page rather than a {@code controller}-package REST
 * endpoint.
 */
@Controller
@RequestMapping("/docs/csrf-demo")
public class CsrfDemoController {

    @GetMapping
    public String showForm(HttpServletRequest request, Model model) {
        addCsrfToken(request, model);
        return "csrf-demo";
    }

    @PostMapping
    public String submit(@RequestParam String note, HttpServletRequest request, Model model) {
        model.addAttribute("submittedNote", note);
        addCsrfToken(request, model);
        return "csrf-demo";
    }

    private void addCsrfToken(HttpServletRequest request, Model model) {
        model.addAttribute("csrfToken", request.getAttribute(CsrfToken.class.getName()));
    }
}
