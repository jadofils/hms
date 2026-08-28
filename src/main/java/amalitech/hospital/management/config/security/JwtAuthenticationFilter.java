package amalitech.hospital.management.config.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Parses a Bearer JWT (if present) into the Spring Security context for the current
 * request. A missing or invalid token is never a hard failure here — it just leaves
 * the request unauthenticated, and route-level authorization rules in
 * {@link SecurityConfig} decide from there whether that's allowed.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            try {
                JwtService.Identity identity = jwtService.verify(header.substring(PREFIX.length()));
                if (jwtService.isBlocklisted(identity.jti())) {
                    SecurityContextHolder.clearContext();
                } else {
                    var principal = new AuthenticatedUser(identity.userId(), identity.username(), identity.roles());
                    List<SimpleGrantedAuthority> authorities = identity.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JWTVerificationException _) {
                SecurityContextHolder.clearContext();
            }
        }
       filterChain.doFilter(request, response);
    }
}
