package amalitech.hospital.management.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Runs when the OAuth2 handshake with Google itself fails — the caller denied consent,
 * Google rejected the request, or the network call to Google failed. Distinct from
 * {@link OAuth2LoginSuccessHandler}'s own {@code UnauthorizedException} branch: that one
 * fires *after* Google has already vouched for the identity but this app's own rules
 * (no role yet, deactivated) block it; this one fires when Google's own handshake never
 * completed at all.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Google OAuth2 handshake failed: {}", exception.getMessage());
        response.sendRedirect(frontendBaseUrl + "/oauth2/callback?error="
                + URLEncoder.encode("Google sign-in failed — please try again", StandardCharsets.UTF_8));
    }
}
