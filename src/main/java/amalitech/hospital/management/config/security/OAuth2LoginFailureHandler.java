package amalitech.hospital.management.config.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs when the OAuth2 handshake with Google itself fails — the caller denied consent,
 * Google rejected the request, or the network call to Google failed. Distinct from
 * {@link OAuth2LoginSuccessHandler}'s own {@code UnauthorizedException} branch: that one
 * fires *after* Google has already vouched for the identity but this app's own rules
 * (no role yet, deactivated) block it; this one fires when Google's own handshake never
 * completed at all.
 *
 * <p>Writes the JSON error body directly, same reasoning as
 * {@link OAuth2LoginSuccessHandler} — there is no frontend at {@code app.frontend-base-url}
 * to redirect to.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Google OAuth2 handshake failed: {}", exception.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Google sign-in failed — please try again");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
