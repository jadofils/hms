package amalitech.hospital.management.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HMS v4, Epic 3.1 — "CSRF token mechanism demonstrated in one form endpoint for
 * illustration." The rest of this API is a stateless, bearer-JWT REST/GraphQL service,
 * where CSRF protection doesn't apply (see {@link SecurityConfig}'s
 * {@code .csrf(csrf -> csrf.disable())}: CSRF exploits a browser automatically attaching
 * a session cookie to a forged cross-site request, and this app never authenticates via
 * a cookie) — so there's no real endpoint anywhere else in the app to hang this on
 * without it being a fabricated example.
 *
 * <p>Rather than touch that chain, this is its own narrowly-{@link HttpSecurity#securityMatcher
 * securityMatcher}-scoped {@code SecurityFilterChain}, evaluated before ({@code @Order(1)},
 * a lower number wins) {@link SecurityConfig#securityFilterChain}, covering only
 * {@code /docs/csrf-demo/**} — see {@code amalitech.hospital.management.docs.CsrfDemoController}
 * for the page itself. CSRF protection is left at Spring Security's own default here
 * (session-backed {@code CsrfTokenRepository}) simply by never calling {@code .csrf(...)}
 * at all — it's only ever disabled explicitly, never enabled explicitly, so the absence
 * of that call is enough. The session it needs to store the token in is exactly what
 * {@link SecurityConfig}'s {@code SessionCreationPolicy.IF_REQUIRED} already allows for.
 */
@Configuration
public class CsrfDemoSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain csrfDemoSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/docs/csrf-demo/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
