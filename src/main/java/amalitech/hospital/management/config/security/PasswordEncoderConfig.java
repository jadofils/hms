package amalitech.hospital.management.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code PasswordEncoder} used to live as a {@code @Bean} method directly on
 * {@link SecurityConfig} — moved out into its own dependency-free config class (HMS v4)
 * because {@link SecurityConfig} now takes {@link OAuth2LoginSuccessHandler}/
 * {@link OAuth2LoginFailureHandler} as constructor dependencies, and both of those need
 * {@code AuthService}, which needs {@code PasswordEncoder} back. A {@code @Bean} method
 * can only run once its declaring {@code @Configuration} instance itself has been
 * constructed — so with {@code passwordEncoder()} still on {@code SecurityConfig},
 * building it required {@code SecurityConfig} to already exist, which required
 * {@code OAuth2LoginSuccessHandler} to already exist, which required {@code AuthService}
 * to already exist, which required {@code passwordEncoder()} again: an unresolvable
 * circular bean reference ({@code BeanCurrentlyInCreationException}). This class has no
 * constructor dependencies of its own, so Spring can create it (and therefore this bean)
 * independently of that whole chain.
 */
@Configuration
public class PasswordEncoderConfig {

    /** Strength comes from {@code BCRYPT_ROUNDS} (.env) via {@code security.bcrypt.rounds}. */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${security.bcrypt.rounds}") int bcryptRounds) {
        return new BCryptPasswordEncoder(bcryptRounds);
    }
}
