package amalitech.hospital.management.config.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-32-characters-long";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 8, redisTemplate);
    }

    @Test
    void generateToken_thenVerify_roundTripsDecryptedIdentity() {
        String token = jwtService.generateToken("user-1", "alice", "ADMIN", "jti-1");

        JwtService.Identity identity = jwtService.verify(token);

        assertThat(identity.userId()).isEqualTo("user-1");
        assertThat(identity.username()).isEqualTo("alice");
        assertThat(identity.role()).isEqualTo("ADMIN");
        assertThat(identity.jti()).isEqualTo("jti-1");
        assertThat(identity.expiresAt()).isAfter(new Date());
    }

    @Test
    void generateToken_withoutExplicitJti_generatesARandomOne() {
        String token = jwtService.generateToken("user-1", "alice", "ADMIN");
        JwtService.Identity identity = jwtService.verify(token);
        assertThat(identity.jti()).isNotBlank();
    }

    @Test
    void generateToken_neverEmbedsIdentityClaimsAsPlaintext() {
        String token = jwtService.generateToken("user-1", "alice", "ADMIN", "jti-1");

        assertThat(token).doesNotContain("user-1");
        assertThat(token).doesNotContain("alice");
        assertThat(token).doesNotContain("ADMIN");
    }

    @Test
    void verify_throwsJWTVerificationException_whenSignatureTampered() {
        String token = jwtService.generateToken("user-1", "alice", "ADMIN", "jti-1");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.verify(tampered))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void verify_throwsJWTVerificationException_whenSignedByADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-secret-32chars!!", 8, redisTemplate);
        String token = otherService.generateToken("user-1", "alice", "ADMIN", "jti-1");

        assertThatThrownBy(() -> jwtService.verify(token))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void isBlocklisted_returnsFalse_whenKeyAbsent() {
        when(redisTemplate.hasKey("jwt:blocklist:jti-1")).thenReturn(false);
        assertThat(jwtService.isBlocklisted("jti-1")).isFalse();
    }

    @Test
    void isBlocklisted_returnsTrue_whenKeyPresent() {
        when(redisTemplate.hasKey("jwt:blocklist:jti-1")).thenReturn(true);
        assertThat(jwtService.isBlocklisted("jti-1")).isTrue();
    }

    @Test
    void blocklist_storesKeyWithTtlDerivedFromExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000); // 60s from now

        jwtService.blocklist("jti-1", expiresAt);

        verify(valueOperations).set(eq("jwt:blocklist:jti-1"), eq("1"), any(Duration.class));
    }

    @Test
    void blocklist_clampsTtlToAtLeastOneSecond_whenAlreadyExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Date alreadyExpired = new Date(System.currentTimeMillis() - 60_000);

        jwtService.blocklist("jti-1", alreadyExpired);

        verify(valueOperations).set(eq("jwt:blocklist:jti-1"), eq("1"), eq(Duration.ofSeconds(1)));
    }

    @Test
    void getExpiryHours_returnsConfiguredValue() {
        assertThat(jwtService.getExpiryHours()).isEqualTo(8);
    }
}
