package amalitech.hospital.management.config.security;

import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.service.AuthService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs once Spring Security's own OAuth2 handshake with Google has already succeeded —
 * the caller has proven they own the Google account, but hasn't been issued an HMS
 * session/JWT yet. That's what {@link AuthService#loginWithGoogle} does: find-or-create
 * the matching {@code User} row, then hand back this app's own token exactly the way
 * {@code /auth/login} would.
 *
 * <p>Writes the JSON response body directly rather than redirecting to a frontend —
 * there is no frontend deployed at {@code app.frontend-base-url} for this project, so
 * redirecting there (as an earlier pass did) just ends in the browser's own
 * "connection refused" page with the token unreadable in the address bar. Google's
 * own redirect back to {@code /login/oauth2/code/google} is still a real full-page
 * browser navigation (unavoidable — that's how OAuth2 authorization-code flow works),
 * but *this* handler is the end of that chain, running inside a normal servlet request
 * with a real {@link HttpServletResponse} to write to — so it can return the same
 * {@code ApiResult<LoginResponse>} JSON shape {@code POST /auth/login} does, and the
 * browser just renders it as a JSON page. Confidentiality-sensitive claims
 * (userId/username/role) are protected the same way they are for every other JWT this
 * app issues — {@code JwtService} AES-256-GCM-encrypts them before embedding them in
 * {@code token}; this handler doesn't need to do anything extra for that.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthService authService;
    // Read-only, used only in the catch block below to hand the caller a userId
    // alongside the error reason — see that block's own comment for why.
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        try {
            LoginResponse loginResponse = authService.loginWithGoogle(email, name, request);
            log.info("Google OAuth2 login succeeded for {}", email);
            writeJson(response, HttpServletResponse.SC_OK,
                    ApiResult.of("Google sign-in successful", loginResponse));
        } catch (UnauthorizedException ex) {
            // "Deactivated" or (now rare, since a brand-new account auto-gets Guest —
            // see AuthService.createGoogleProvisionedUser) "no assigned role" — not a
            // real authentication failure (Google already vouched for the identity).
            // Includes the account's own userId alongside the error (not just the bare
            // message) so the caller/an admin has something concrete to act on rather
            // than a dead-end string — best-effort only: the account genuinely might
            // not exist at all if Google's own handshake itself failed before ever
            // reaching AuthService.
            log.warn("Google OAuth2 login blocked for {}: {}", email, ex.getMessage());
            String userId = userRepository.findByEmail(email).map(u -> u.getUserId()).orElse(null);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("error", "Unauthorized");
            body.put("message", ex.getMessage());
            if (userId != null) {
                body.put("userId", userId);
            }
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, body);
        }
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
