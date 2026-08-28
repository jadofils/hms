package amalitech.hospital.management.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed-window request-rate limiter, keyed per client IP across every endpoint combined
 * (not per-endpoint) — the goal is stopping one source from overwhelming the app at all,
 * not policing any single route, so a client can't dodge the limit by spreading requests
 * across different paths. Uses Redis (already a hard dependency for this app — JWT
 * blocklist/caching, see {@code JwtService}/{@code CacheConfig}) as the shared counter
 * store, the same {@code INCR}+{@code EXPIRE} pattern {@code JwtService.blocklist} already
 * uses for a self-expiring key: {@code INCR} both creates the key at 1 and returns the
 * new count atomically, and the very first request in a window is the one that sets the
 * TTL, so the key (and the window) automatically expires on its own — no separate cleanup
 * job needed. This is a deliberately simple fixed-window counter, not a sliding-window
 * or token-bucket algorithm — it allows a burst up to 2x the limit right at a window
 * boundary (worst case), which is an acceptable, well-known trade-off for a project at
 * this scale in exchange for a trivial, easily-reasoned-about implementation.
 *
 * <p><b>Deliberately NOT a {@code @Component}.</b> A filter that's both a
 * {@code @Component} bean AND wired into {@code HttpSecurity} via
 * {@code .addFilterBefore(...)} (the way {@link JwtAuthenticationFilter} is) ends up
 * registered twice — once inside Spring Security's own filter chain, and a second time
 * by Spring Boot's generic "any {@code Filter} bean gets auto-registered as its own
 * servlet filter" behavior — which would double-count every request against the limit.
 * {@link JwtAuthenticationFilter}'s own double-registration is harmless (parsing the same
 * header twice is a no-op), but a counter is not idempotent under double invocation.
 * Constructed instead as a plain object, registered exactly once via
 * {@code RateLimitFilterConfig}'s {@code FilterRegistrationBean}, entirely outside
 * {@code HttpSecurity} — which also means one registration protects every endpoint,
 * including {@code /docs/csrf-demo} (a second, separate {@code SecurityFilterChain} —
 * see {@code CsrfDemoSecurityConfig}), without wiring it into both chains individually.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final int windowSeconds;

    public RateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                            int maxRequests, int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String key = KEY_PREFIX + clientId(request);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxRequests) {
            Long ttl = redisTemplate.getExpire(key);
            writeTooManyRequests(response, ttl != null && ttl > 0 ? ttl : windowSeconds);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * {@code X-Forwarded-For}'s first entry when present (this app sitting behind a
     * reverse proxy/load balancer in front of it), falling back to the direct socket
     * address for local/dev use where nothing sits in front of it. Deliberately not
     * per-authenticated-user: this filter runs ahead of {@link JwtAuthenticationFilter}
     * (see {@code RateLimitFilterConfig}'s ordering) specifically so an unauthenticated
     * flood is rejected before the app spends any effort verifying a token at all — by
     * that point in the chain there's no {@code SecurityContext} populated yet to key on.
     */
    private String clientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Same {status, error, message} shape as GlobalExceptionHandler's ErrorResponse —
        // built by hand rather than reusing that class directly, since this filter runs
        // ahead of the DispatcherServlet entirely (GlobalExceptionHandler only ever sees
        // exceptions a controller throws) and has no need for that class's timestamp field.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded — try again in " + retryAfterSeconds + " seconds");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
