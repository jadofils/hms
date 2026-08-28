package amalitech.hospital.management.config.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests via Mockito, per CLAUDE.md's Testing convention — {@code doFilterInternal}
 * is package-visible ({@code protected}, same package), so it's called directly rather than
 * through a real servlet container.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_leavesRequestUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void headerWithoutBearerPrefix_leavesRequestUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validNonBlocklistedToken_populatesSecurityContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        JwtService.Identity identity = new JwtService.Identity(
                "user-1", "alice", List.of("ADMIN"), "jti-1", Instant.now().plusSeconds(60));
        when(jwtService.verify("valid-token")).thenReturn(identity);
        when(jwtService.isBlocklisted("jti-1")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser("user-1", "alice", List.of("ADMIN")));
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void multipleRoles_populatesOneAuthorityPerRole() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        JwtService.Identity identity = new JwtService.Identity(
                "user-1", "alice", List.of("ADMIN", "DOCTOR"), "jti-1", Instant.now().plusSeconds(60));
        when(jwtService.verify("valid-token")).thenReturn(identity);
        when(jwtService.isBlocklisted("jti-1")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DOCTOR");
    }

    @Test
    void blocklistedToken_clearsSecurityContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-token");
        JwtService.Identity identity = new JwtService.Identity(
                "user-1", "alice", List.of("ADMIN"), "jti-1", Instant.now().plusSeconds(60));
        when(jwtService.verify("revoked-token")).thenReturn(identity);
        when(jwtService.isBlocklisted("jti-1")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_clearsSecurityContext_andNeverThrows() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage-token");
        when(jwtService.verify("garbage-token")).thenThrow(new JWTVerificationException("malformed"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).isBlocklisted(anyString());
    }
}
