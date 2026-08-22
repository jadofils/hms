package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.aop.SystemLogWriter;
import amalitech.hospital.management.config.security.JwtService;
import amalitech.hospital.management.dto.auth.ChangePasswordRequest;
import amalitech.hospital.management.dto.auth.ForgotPasswordRequest;
import amalitech.hospital.management.dto.auth.LoginRequest;
import amalitech.hospital.management.dto.auth.LoginResponse;
import amalitech.hospital.management.dto.auth.ResetPasswordRequest;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.exception.runtime.UnauthorizedException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.UserRole;
import amalitech.hospital.management.model.user.UserSession;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.UserRoleRepository;
import amalitech.hospital.management.repository.user.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Login/logout/password-reset flows.
 *
 * Sessions are tracked two ways: a {@link UserSession} row per login (durable audit
 * trail — who logged in, from where, when they logged out), and a Redis blocklist keyed
 * by the token's jti (fast, stateless-friendly revocation check on every request — see
 * {@link JwtService}). Logging out, or resetting a password, updates both.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String RESET_TOKEN_PREFIX = "password-reset:";
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    /** Same Redis key prefix {@code UserService.createUser} writes under — see
     *  {@link #verifyEmail}. */
    private static final String EMAIL_VERIFY_TOKEN_PREFIX = "email-verify:";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final SystemLogWriter systemLogWriter;
    private final StringRedisTemplate redisTemplate;

    /** Self-injected proxy reference, used only to call this class's own
     *  {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     *  {@link #findUserByEmail}. {@code @Lazy} breaks the circular dependency this
     *  creates at bean-creation time. */
    @Lazy
    private final AuthService self;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

    /**
     * Wraps the whole attempt in a security-event log (HMS v4, Epic 5.2) — success or
     * failure, always naming the attempted identifier and source IP, never the password.
     * {@code LoggingAspect}'s own generic failure log for this same method fires too
     * (it wraps every service method); this one exists because that generic log is
     * deliberately argument-blind (see its own Javadoc) and so can never say
     * <em>which</em> account a failed attempt targeted — exactly the detail brute-force
     * detection needs.
     */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            // Accepts either a username or an email in the same field — tries username
            // first (the common case) and only looks up by email if that misses, rather
            // than requiring the caller to say which kind of identifier they're sending.
            User user = userRepository.findByUsername(request.getUsername())
                    .or(() -> userRepository.findByEmail(request.getUsername()))
                    .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

            if (user.getDeletedAt() != null
                    || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException(INVALID_CREDENTIALS);
            }
            LoginResponse response = completeLogin(user, httpRequest);
            logSecurityEvent("INFO", request.getUsername(), httpRequest, "Successful login");
            return response;
        } catch (UnauthorizedException ex) {
            logSecurityEvent("WARNING", request.getUsername(), httpRequest, "Failed login: " + ex.getMessage());
            throw ex;
        }
    }

    /**
     * Google OAuth2 login (HMS v4, Epic 4.1) — called by
     * {@code OAuth2LoginSuccessHandler} once Google's own handshake has already proven
     * the caller owns {@code email}. Finds the matching account by email, or creates one
     * on the fly if this is the first time this Google identity has ever logged in —
     * same no-default-role rule as {@link amalitech.hospital.management.service.UserService#createUser}'s
     * self-registration (an administrator still has to grant a role via
     * {@code POST /api/v1/users/{userId}/roles/{roleId}} before the account can do
     * anything), so a brand-new Google login throws the exact same "no assigned role"
     * {@link UnauthorizedException} a brand-new password self-registration would.
     *
     * <p>{@code passwordHash} gets a random, never-communicated BCrypt hash — this
     * account was never given a password to begin with, so there's nothing valid for
     * {@code POST /auth/login} to ever match against; the account can only authenticate
     * via Google unless it later runs {@code POST /auth/forgot-password} to set a real
     * one (the same reset flow any account can use, not something OAuth-specific).
     */
    @Transactional
    public LoginResponse loginWithGoogle(String email, String displayName, HttpServletRequest httpRequest) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> createGoogleProvisionedUser(email, displayName));

            if (user.getDeletedAt() != null) {
                throw new UnauthorizedException("This account has been deactivated");
            }
            // Google's own OAuth2 handshake already re-proves ownership of this exact
            // email address — that's a strictly stronger signal than the click-the-link
            // flow self-registration otherwise requires, so a login via Google satisfies
            // (and, for a pre-existing password account that never finished the email
            // link, retroactively satisfies) the same verification gate the password
            // path checks.
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(LocalDateTime.now(ZoneId.systemDefault()));
                userRepository.save(user);
            }
            LoginResponse response = completeLogin(user, httpRequest);
            logSecurityEvent("INFO", email, httpRequest, "Successful Google OAuth2 login");
            return response;
        } catch (UnauthorizedException ex) {
            logSecurityEvent("WARNING", email, httpRequest, "Failed Google OAuth2 login: " + ex.getMessage());
            throw ex;
        }
    }

    /** {@code identifier} is the attempted username/email — never the password — see
     *  {@code SystemLogWriter}'s own Javadoc for why this exists alongside
     *  {@code LoggingAspect}'s generic failure log. */
    private void logSecurityEvent(String logLevel, String identifier, HttpServletRequest httpRequest, String outcome) {
        systemLogWriter.record(logLevel, "AuthService.login.security-event",
                outcome + " for '" + identifier + "' from " + httpRequest.getRemoteAddr());
    }

    private User createGoogleProvisionedUser(String email, String displayName) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        User user = new User();
        user.setUsername(uniqueUsernameFrom(email, displayName));
        user.setEmail(email);
        // Random, never returned/logged/usable-as-a-real-password — see this method's
        // caller's own Javadoc for why an OAuth-only account still needs some value here
        // (the column is NOT NULL) without ever being a real credential.
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        user.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(randomBytes)));
        user.setIsActive(true);
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    /** {@code username} is required and unique, but Google only gives us an email and a
     *  display name — derives a candidate from the email's local part (falling back to
     *  the display name if that's somehow blank) and appends digits until it's free,
     *  the same collision-handling shape {@code DataSeeder}'s own bulk user creation uses. */
    private String uniqueUsernameFrom(String email, String displayName) {
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String base = (localPart.isBlank() ? displayName : localPart).replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    /**
     * Shared by both {@link #login} and {@link #loginWithGoogle} once the caller's
     * identity is already established (password verified, or Google's own handshake
     * already vouched for the email) — active/verified/role checks, session creation,
     * and JWT issuance are identical either way.
     */
    private LoginResponse completeLogin(User user, HttpServletRequest httpRequest) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("This account has been deactivated");
        }
        // Email is mandatory (see UserRequest), so this gate is unconditional — every
        // account must either click its verification link (self-registration), have been
        // pre-verified at creation (UserService.createUserByAdmin, DataSeeder), or have
        // just logged in via Google (loginWithGoogle sets this immediately above).
        if (user.getEmailVerifiedAt() == null) {
            throw new UnauthorizedException("Please verify your email before logging in");
        }

        String role = primaryRole(user.getUserId())
                .orElseThrow(() -> new UnauthorizedException("This account has no assigned role"));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        // sessionId is DB-generated (@GeneratedValue) — save first, THEN use the id
        // Hibernate assigns as the token's jti. Setting it ourselves before the first
        // save would make Spring Data's isNew() check think this is an existing row
        // and issue an UPDATE instead of an INSERT.
        UserSession session = new UserSession();
        session.setUser(user);
        session.setLoginAt(now);
        session.setExpiresAt(now.plusHours(jwtService.getExpiryHours()));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        session.setIsActive(true);
        session.setUpdatedAt(now);
        session = userSessionRepository.save(session);

        String token = jwtService.generateToken(user.getUserId(), user.getUsername(), role, session.getSessionId());
        return new LoginResponse(token, user.getUserId(), user.getUsername(), role);
    }

    @Transactional
    public void logout(String token) {
        JwtService.Identity identity = jwtService.verify(token);
        jwtService.blocklist(identity.jti(), identity.expiresAt());

        userSessionRepository.findById(identity.jti()).ifPresent(session -> {
            session.setLogoutAt(LocalDateTime.now(ZoneId.systemDefault()));
            session.setIsActive(false);
            session.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
            userSessionRepository.save(session);
        });
    }

    /** Reports outright whether the email is on file — see the Javadoc inside for why
     *  this deliberately departs from the usual anti-enumeration pattern. */
    public void forgotPassword(ForgotPasswordRequest request) {
        // Checked first, via the @FindUserData-backed existence lookup, so a caller is
        // told outright whether that email is on file — a deliberate departure from the
        // usual anti-account-enumeration pattern (a generic "if that email exists..."
        // response regardless of outcome), by explicit request.
        if (self.findUserByEmail(request.getEmail()).isEmpty()) {
            throw new NotFoundException("No account found with that email");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("No account found with that email"));
        String token = generateResetToken();
        redisTemplate.opsForValue().set(RESET_TOKEN_PREFIX + token, user.getUserId(), RESET_TOKEN_TTL);
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        mailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), token, resetUrl,
                (int) RESET_TOKEN_TTL.toMinutes());
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findUserByEmail(...)}: Spring AOP proxies only intercept calls
     * made through the proxy, so a same-class call would bypass the aspect and fall
     * through to the body below. Returns the raw matching row(s) for the email; callers
     * only check emptiness, not the row shape.
     */
    @FindUserData(domain = "user")
    public List<?> findUserByEmail(String email) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key = RESET_TOKEN_PREFIX + request.getToken();
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new BadRequestException("Invalid or expired reset token");
        }
        redisTemplate.delete(key); // single-use

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(now);
        userRepository.save(user);

        revokeAllSessions(userId);
        // Notifies whoever holds the mailbox — including an attacker who reset a
        // compromised password — so a legitimate owner locked out can tell something
        // happened. Unconditional now that email is mandatory (see UserRequest).
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getUsername(), now);
    }

    /** Consumes the single-use token {@code UserService.createUser} emailed as a link.
     *  Same shape as {@link #resetPassword}'s token handling: look up, delete
     *  immediately (single-use), then act on the resolved user. */
    @Transactional
    public void verifyEmail(String token) {
        String key = EMAIL_VERIFY_TOKEN_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new BadRequestException("Invalid or expired verification token");
        }
        redisTemplate.delete(key);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.setEmailVerifiedAt(LocalDateTime.now(ZoneId.systemDefault()));
        user.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(now);
        userRepository.save(user);
        // Unconditional now that email is mandatory (see UserRequest) — see resetPassword's
        // identical notification above.
        mailService.sendPasswordChangedEmail(user.getEmail(), user.getUsername(), now);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** A user can hold multiple roles; the token carries one, so the longest-held
     *  (earliest-assigned) active role is treated as primary. */
    private Optional<String> primaryRole(String userId) {
        return userRoleRepository.findByIdUserId(userId).stream()
                .filter(ur -> ur.getRevokedAt() == null)
                .min(Comparator.comparing(UserRole::getAssignedAt))
                .map(ur -> ur.getRole().getRoleName());
    }

    /** A password reset invalidates every session issued before it — the old password
     *  might be compromised, so anything logged in under it should be kicked out too. */
    private void revokeAllSessions(String userId) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        userSessionRepository.findByUser_UserIdAndIsActiveTrue(userId).forEach(session -> {
            jwtService.blocklist(session.getSessionId(), session.getExpiresAt());
            session.setIsActive(false);
            session.setLogoutAt(now);
            session.setUpdatedAt(now);
            userSessionRepository.save(session);
        });
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
