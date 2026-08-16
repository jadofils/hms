package amalitech.hospital.management.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security policy for the API — stateless, JWT-based.
 *
 * {@link JwtAuthenticationFilter} runs ahead of Spring Security's own authentication
 * filter and populates the SecurityContext from any valid Bearer token, so protected
 * routes can rely on {@code SecurityContextHolder}.
 *
 * URL-level route authorization is still left open ({@code anyRequest().permitAll()}) —
 * deliberately, so Swagger's "Try it out" can always reach every route and get a real
 * {@code 401}/{@code 403} response body back instead of being blocked earlier by a
 * generic Spring Security page. Authorization itself is enforced one level down instead:
 * {@code @RequirePermission} on individual controller methods, checked by
 * {@code aop.AuthorizationAspect} against the caller's role via the
 * {@code Role}/{@code Permission}/{@code RolePermission} tables — see that class's
 * Javadoc. This still relies on {@code SecurityContextHolder} being populated the same
 * way {@code authorizeHttpRequests(...).anyRequest().authenticated()} would use it; it's
 * just checked by an {@code @Aspect} instead of Spring Security's own filter-chain rule.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Strength comes from {@code BCRYPT_ROUNDS} (.env) via {@code security.bcrypt.rounds}. */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${security.bcrypt.rounds}") int bcryptRounds) {
        return new BCryptPasswordEncoder(bcryptRounds);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
