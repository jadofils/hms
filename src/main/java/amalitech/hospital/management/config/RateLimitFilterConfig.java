package amalitech.hospital.management.config;

import amalitech.hospital.management.config.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers {@link RateLimitFilter} as its own standalone servlet filter — see that
 * class's own Javadoc for why it's built here via {@link FilterRegistrationBean} instead
 * of a {@code @Component} wired into {@code HttpSecurity}.
 *
 * <p>{@code setOrder(Ordered.HIGHEST_PRECEDENCE)} places this ahead of Spring Security's
 * own filter chain ({@code DelegatingFilterProxyRegistrationBean}, registered internally
 * at {@code SecurityProperties.DEFAULT_FILTER_ORDER = -100}) — a client that's already
 * over the limit is rejected before CORS, CSRF, or JWT verification do any work at all,
 * and the same one registration covers both {@code SecurityFilterChain} beans
 * ({@code SecurityConfig} and {@code CsrfDemoSecurityConfig}) uniformly, since neither
 * one gets a say before this filter runs.
 *
 * <p>{@code app.rate-limit.enabled} defaults to {@code true} everywhere except the
 * {@code test} profile ({@code application-test.yaml} sets it {@code false}) — the
 * ~37 {@code @SpringBootTest @AutoConfigureMockMvc} controller test classes
 * (see {@code AbstractControllerTest}) collectively fire far more than any sane per-
 * minute limit through the real filter chain, all simulated from MockMvc's fixed
 * {@code 127.0.0.1}, which would otherwise fail the suite with 429s that have nothing to
 * do with the behavior under test. {@code FilterRegistrationBean.setEnabled(false)} is
 * the standard, Spring-Boot-native way to skip a filter's registration entirely (both in
 * the real embedded servlet container and under {@code @AutoConfigureMockMvc}) rather
 * than adding an if-check inside the filter itself. For the same underlying reason, the
 * {@code k6}/JMeter load tests (HMS v5 — see {@code docs/v5/k6-guide.md}) intentionally
 * exceed any reasonable per-minute-per-IP limit from one machine by design; raise
 * {@code APP_RATE_LIMIT_MAX_REQUESTS} or set {@code APP_RATE_LIMIT_ENABLED=false} in
 * {@code .env} before running one, then restore it afterward.
 */
@Configuration
public class RateLimitFilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.max-requests:100}") int maxRequests,
            @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(redisTemplate, objectMapper, maxRequests, windowSeconds));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setEnabled(enabled);
        return registration;
    }
}
