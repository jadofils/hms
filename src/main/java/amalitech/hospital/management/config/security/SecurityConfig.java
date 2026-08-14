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
 * routes can rely on {@code SecurityContextHolder} once they exist.
 *
 * Route authorization itself is still left open ({@code anyRequest().permitAll()}) —
 * there is no login endpoint yet to issue a token against, so requiring authentication
 * now would make the still-stubbed {@code /api/v1/**} endpoints (and Swagger's
 * "Try it out") unreachable. Once a real login flow exists, tighten this to
 * {@code anyRequest().authenticated()} with explicit {@code permitAll} for {@code "/"},
 * {@code "/swagger-ui/**"}, {@code "/v3/api-docs/**"}, and the login route.
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
