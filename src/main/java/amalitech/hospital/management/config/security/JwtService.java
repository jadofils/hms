package amalitech.hospital.management.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and verifies JWTs for authenticated sessions.
 *
 * java-jwt (com.auth0) only signs tokens (JWS) — it has no built-in encryption (JWE) —
 * so the identity claims (userId, username, role) are individually AES-256-GCM encrypted
 * before being embedded, and decrypted again on verify. Structural claims (iat/exp/jti)
 * stay plain so java-jwt's own expiry check runs before we ever touch the encrypted payload.
 *
 * The AES key and the HMAC signing secret are both derived (SHA-256, domain-separated by
 * prefix) from the single {@code security.encryption-key} value already configured in
 * application.yaml, rather than managing a second secret.
 *
 * Logout support: since JWTs are otherwise stateless (nothing to revoke server-side),
 * every token carries a random {@code jti}. Logging out adds that jti to a Redis
 * blocklist with a TTL equal to the token's own remaining lifetime — the entry expires
 * by itself exactly when the token would have anyway, so nothing needs cleaning up.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

    private final SecretKeySpec claimKey;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expiryHours;
    private final long expiryMs;
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redisTemplate;

    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    public JwtService(
            @Value("${security.encryption-key}") String encryptionKey,
            @Value("${security.jwt.expiry-hours}") long expiryHours,
            StringRedisTemplate redisTemplate) {
        this.claimKey = deriveKey("jwt-claims:", encryptionKey);
        this.algorithm = Algorithm.HMAC256(deriveKey("jwt-signature:", encryptionKey).getEncoded());
        this.verifier = JWT.require(algorithm).build();
        this.expiryHours = expiryHours;
        this.expiryMs = expiryHours * 60 * 60 * 1000;
        this.redisTemplate = redisTemplate;
    }

    /** Decrypted identity extracted from a verified token, plus its jti/expiry for logout. */
    public record Identity(String userId, String username, String role, String jti, Instant expiresAt) {}

    public long getExpiryHours() {
        return expiryHours;
    }

    /** Builds a signed token with userId/username/role embedded as encrypted claims. */
    public String generateToken(String userId, String username, String role, String jti) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expiryMs);
        return JWT.create()
                .withJWTId(jti)
                .withClaim(CLAIM_USER_ID, encrypt(userId))
                .withClaim(CLAIM_USERNAME, encrypt(username))
                .withClaim(CLAIM_ROLE, encrypt(role))
                .withIssuedAt(now)
                .withExpiresAt(expiry)
                .sign(algorithm);
    }

    /** Convenience overload — generates its own random jti. */
    public String generateToken(String userId, String username, String role) {
        return generateToken(userId, username, role, UUID.randomUUID().toString());
    }

    /**
     * Verifies the signature and expiry, then decrypts the identity claims.
     * Does NOT check the blocklist — callers that care about logout (the auth filter)
     * must call {@link #isBlocklisted} themselves after this succeeds.
     *
     * @throws JWTVerificationException if the token is malformed, wasn't signed by us,
     *                                   or has expired (thrown as its subtype {@code TokenExpiredException})
     */
    public Identity verify(String token) {
        DecodedJWT jwt = verifier.verify(token);
        return new Identity(
                decrypt(jwt.getClaim(CLAIM_USER_ID).asString()),
                decrypt(jwt.getClaim(CLAIM_USERNAME).asString()),
                decrypt(jwt.getClaim(CLAIM_ROLE).asString()),
                jwt.getId(),
                jwt.getExpiresAtAsInstant());
    }

    // ── Logout / revocation ──────────────────────────────────────────────────

    public boolean isBlocklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_PREFIX + jti));
    }

    public void blocklist(String jti, Instant expiresAt) {
        long ttlSeconds = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        redisTemplate.opsForValue().set(BLOCKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public void blocklist(String jti, LocalDateTime expiresAt) {
        blocklist(jti, expiresAt.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ── AES-256-GCM for individual claim values ────────────────────────────────

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claimKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[GCM_IV_LEN + cipherBytes.length];
            System.arraycopy(iv, 0, out, 0, GCM_IV_LEN);
            System.arraycopy(cipherBytes, 0, out, GCM_IV_LEN, cipherBytes.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt JWT claim", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            byte[] input = Base64.getUrlDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(input, 0, iv, 0, GCM_IV_LEN);
            byte[] cipherBytes = new byte[input.length - GCM_IV_LEN];
            System.arraycopy(input, GCM_IV_LEN, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claimKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt JWT claim — token may be tampered", e);
        }
    }

    private static SecretKeySpec deriveKey(String domain, String masterSecret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest((domain + masterSecret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Key derivation failed for domain '" + domain + "'", e);
        }
    }
}
