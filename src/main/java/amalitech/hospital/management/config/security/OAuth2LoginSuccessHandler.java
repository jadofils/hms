package amalitech.hospital.management.config.security;

import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Runs once Spring Security's own OAuth2 handshake with Google has already succeeded —
 * the caller has proven they own the Google account, but hasn't been issued an HMS
 * session/JWT yet. That's what {@link AuthService#loginWithGoogle} does: find-or-create
 * the matching {@code User} row, then hand back this app's own token exactly the way
 * {@code /auth/login} would.
 *
 * <p>Redirects rather than returning JSON directly — this handler runs at the end of a
 * real browser redirect chain (the user's browser navigated to Google and back), not an
 * AJAX/fetch call a frontend could read a JSON body from directly. The token rides in a
 * query param on the redirect back to {@code app.frontend-base-url}, for the SPA there to
 * pick up and store the same way it would a normal login response's token.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthService authService;

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        try {
            LoginResponse loginResponse = authService.loginWithGoogle(email, name, request);
            log.info("Google OAuth2 login succeeded for {}", email);
            response.sendRedirect(frontendBaseUrl + "/oauth2/callback?token="
                    + URLEncoder.encode(loginResponse.getToken(), StandardCharsets.UTF_8));
        } catch (UnauthorizedException ex) {
            // Same "no assigned role"/"deactivated" outcomes a password login can hit —
            // not a real authentication failure (Google already vouched for the
            // identity), so this redirects with an error reason instead of 401ing a
            // request that has no JSON response body a browser redirect could show
            // anyway.
            log.warn("Google OAuth2 login blocked for {}: {}", email, ex.getMessage());
            response.sendRedirect(frontendBaseUrl + "/oauth2/callback?error="
                    + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8));
        }
    }
}
