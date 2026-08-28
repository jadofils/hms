package amalitech.hospital.management.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security policy for the API — mostly stateless, JWT-based, with one deliberate
 * exception for OAuth2 login (see {@link #securityFilterChain} below).
 *
 * {@link JwtAuthenticationFilter} runs ahead of Spring Security's own authentication
 * filter and populates the SecurityContext from any valid Bearer token, so protected
 * routes can rely on {@code SecurityContextHolder}.
 *
 * URL-level route authorization is still left open ({@code anyRequest().permitAll()}) —
 * deliberately, so Swagger's "Try it out" can always reach every route and get a real
 * {@code 401}/{@code 403} response body back instead of being blocked earlier by a
 * generic Spring Security page. Authorization itself is enforced two levels down
 * instead, and this project is deliberately <b>permission</b>-based access control, not
 * role-based — a role is just a named, admin-editable bundle of permissions, and nothing
 * is ever gated on which bundle a caller happens to hold:
 * {@code @RequirePermission} on individual controller methods (checked by
 * {@code aop.AuthorizationAspect} against the {@code Role}/{@code Permission}/
 * {@code RolePermission} tables), and, on a handful of genuinely sensitive operations,
 * {@code @PreAuthorize("@permissionCheck.has(resource, action)")} (enabled here via
 * {@code @EnableMethodSecurity} — HMS v4's Epic 4.2 asks for {@code @PreAuthorize}/
 * {@code @Secured} demonstrated explicitly) as a second, independently-implemented check
 * of that <em>same</em> permission — see {@link PermissionExpressions}' own Javadoc for
 * why that bean checks a granted permission rather than {@code hasRole(...)}. Both still
 * rely on {@code SecurityContextHolder} being populated the same way
 * {@code authorizeHttpRequests(...).anyRequest().authenticated()} would use it.
 *
 * {@code PasswordEncoder} is deliberately NOT a {@code @Bean} on this class — see
 * {@link PasswordEncoderConfig}'s own Javadoc for the circular-bean-creation problem
 * that constructor-injecting {@link OAuth2LoginSuccessHandler}/
 * {@link OAuth2LoginFailureHandler} here would otherwise cause.
 *
 * {@code @Order(2)} on {@link #securityFilterChain} — HMS v4's Epic 3.1 added a second,
 * narrowly-scoped chain ({@link CsrfDemoSecurityConfig}, {@code @Order(1)}, matched only
 * to {@code /docs/csrf-demo/**}) that leaves CSRF protection <em>on</em> to illustrate the
 * token mechanism, in deliberate contrast to this chain disabling it for the actual JWT
 * API below. Spring Security requires an explicit order across multiple
 * {@code SecurityFilterChain} beans; the lower number is tried first, and a request only
 * falls through to this one once it fails that narrower matcher.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    /**
     * HMS v4, Epic 1.2 — explicit allow-list (never a bare {@code "*"}, which the
     * browser itself refuses to combine with credentialed requests anyway). Every value
     * here comes from {@code app.cors-*} (comma-separated where a list, {@code .env}-
     * driven) rather than being hardcoded, so a deployment can widen/narrow any of them —
     * origins, methods, headers, or whether credentials are allowed — without a rebuild.
     * Defaults match this project's original hardcoded values, so leaving the {@code .env}
     * entries unset reproduces the exact same behavior as before. Applies to every route
     * uniformly — narrower, per-route CORS rules aren't needed since this API has no
     * routes that are simultaneously public *and* meant to be unreachable from a browser.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors-allowed-origins}") List<String> allowedOrigins,
            @Value("${app.cors-allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") List<String> allowedMethods,
            @Value("${app.cors-allowed-headers:Authorization,Content-Type,Accept}") List<String> allowedHeaders,
            @Value("${app.cors-allow-credentials:true}") boolean allowCredentials) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(allowCredentials);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            // IF_REQUIRED, not STATELESS: Google's OAuth2 authorization-code redirect
            // needs somewhere to stash the state/PKCE parameters across the round trip
            // to Google and back (Spring Security's own HttpSessionOAuth2AuthorizationRequestRepository) —
            // a fully stateless filter chain has nowhere for that to live. This doesn't
            // reintroduce session-based auth for the rest of the API: nothing else in
            // this app ever calls request.getSession(), so no session is ever created
            // for a normal JWT-bearing request: IF_REQUIRED only creates one when
            // something actually asks for it, which today is exclusively the OAuth2
            // login handshake itself.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
