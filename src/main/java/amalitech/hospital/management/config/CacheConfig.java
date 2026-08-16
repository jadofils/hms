package amalitech.hospital.management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis-backed caching for read-mostly lookups (see {@code @Cacheable} usages in the
 * {@code service} package). Spring Boot's auto-configured {@code RedisCacheManager}
 * picks up this bean as its default cache configuration.
 *
 * Values are JSON-serialized rather than JDK-serialized — the entities/DTOs cached here
 * don't implement {@code Serializable}, and JSON is what you actually want if you ever
 * inspect the cache directly (e.g. via redis-cli).
 *
 * {@code GenericJackson2JsonRedisSerializer} is {@code @Deprecated(forRemoval = true)}
 * as of spring-data-redis 4.0, in favor of {@code GenericJacksonJsonRedisSerializer} —
 * deliberately NOT switched to that replacement here: it's built on
 * {@code tools.jackson.databind.ObjectMapper} (Jackson 3.x), a different major Jackson
 * version than the Jackson 2.x (`com.fasterxml.jackson.*`) this project already depends
 * on everywhere else (web JSON (de)serialization, `jackson-core` in {@code pom.xml}).
 * Migrating just this one cache config would mean running two incompatible Jackson
 * major versions side by side — a real dependency-graph decision, not a mechanical
 * rename, and out of scope for a lint cleanup. Revisit once spring-data-redis ships a
 * Jackson-2-compatible non-deprecated replacement, or once the whole project is ready to
 * move to Jackson 3.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Builds its own {@code ObjectMapper} with {@code JavaTimeModule} registered, rather
     * than letting {@code GenericJackson2JsonRedisSerializer}'s no-arg constructor build
     * one from scratch (no module registered) — or autowiring a Spring-managed
     * {@code ObjectMapper} bean, which doesn't exist in this project's configuration at
     * all (this app has no {@code spring-boot-starter-json}-style Jackson
     * auto-configuration active; Spring MVC's own JSON conversion uses an internal
     * default mapper that's never exposed as a bean). Without {@code JavaTimeModule},
     * caching any DTO with a {@code java.time.*} field (e.g. {@code PatientResponse.dob},
     * {@code AppointmentResponse.appointmentDate}) threw {@code SerializationException}
     * the moment a real cache write was actually exercised — only ever discovered via a
     * real HTTP integration test, since every prior service-layer unit test mocks the
     * repository and never touches Redis at all.
     */
    @Bean
    @SuppressWarnings({"java:S1874", "removal"})
    public RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper cacheMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper)));
    }
}
