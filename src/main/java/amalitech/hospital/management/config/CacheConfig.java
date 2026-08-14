package amalitech.hospital.management.config;

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
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
