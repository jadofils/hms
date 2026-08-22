package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.dto.user.AdminCreateUserRequest;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.event.AdminCreatedUserEvent;
import amalitech.hospital.management.event.UserRegisteredEvent;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.UserRole;
import amalitech.hospital.management.model.user.UserRoleId;
import amalitech.hospital.management.model.user.role.Role;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.UserRoleRepository;
import amalitech.hospital.management.repository.user.role.RoleRepository;
import amalitech.hospital.management.utils.filters.PagedRawResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User CRUD + role assignment.
 *
 * Single-item lookups are cached in Redis under the "users" cache (see
 * {@link amalitech.hospital.management.config.CacheConfig}); every write invalidates
 * the affected entry so a stale user is never served after an update/delete.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String EMAIL_VERIFY_TOKEN_PREFIX = "email-verify:";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    // Reused (not duplicated) for eager-loading — RoleService.getRolePermissions gives
    // getUserRoles' RoleResponse objects their permissions, and DoctorService.getDoctor
    // gives getUser's linked-doctor object its departments too, the exact same fully
    // eager-loaded shape either service's own getById endpoint would return. Neither
    // service depends back on UserService, so this creates no circular dependency.
    private final RoleService roleService;
    private final DoctorService doctorService;
    private final PasswordEncoder passwordEncoder;
    // Publishes UserRegisteredEvent/AdminCreatedUserEvent instead of calling MailService
    // directly (HMS v5) — MailEventListener sends the actual email, deferred to after
    // this method's own transaction commits and off the request thread. See
    // MailEventListener's own Javadoc for why.
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

    @Value("${app.email-verification-ttl-hours}")
    private final long emailVerificationTtlHours;

    /**
     * Self-injected proxy reference, used only to call this class's own
     * {@code @FindUserData}-annotated method through the Spring AOP proxy — see
     * {@link #findUsersPage}. {@code @Lazy} breaks the circular dependency this creates
     * at bean-creation time.
     */
    @Lazy
    private final UserService self;

    /**
     * Listing is served through {@link #findUsersPage}, an {@code @FindUserData}-annotated
     * method (AOP-driven native SQL — see {@link amalitech.hospital.management.aop.FindUserDataAspect})
     * rather than {@code userRepository.findAll(pageable)}, per request to actually exercise
     * the project's custom annotation pattern for pagination.
     *
     * A frontend column sort (Spring's standard {@code ?sort=property,direction} query
     * param, already bound onto {@code pageable} — no extra parameter needed) is passed
     * through as plain strings; only the first {@code Sort.Order} is honored today
     * (multi-column sort isn't wired up). {@code FindUserDataAspect} is what actually
     * validates the column against this domain's own SELECT list before it ever reaches
     * the query.
     *
     * <p>Each row's {@code roles} (and, where the user has one linked, {@code doctor} —
     * departments included) are then eager-loaded via {@link #attachRolesAndDoctors} —
     * two extra queries scoped to just this page's user IDs, not one query per row, so
     * the listing's cost stays flat regardless of page size. This is the one listing in
     * the codebase that eager-loads this deep; every other paginated listing
     * (`DoctorService.getDoctors`, `PatientService.getPatients`, etc.) deliberately
     * doesn't, since a caller here specifically needs it.
     */
    public PagedModel<UserResponse> getUsers(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        String sortBy = order != null ? order.getProperty() : null;
        String sortDir = order != null ? order.getDirection().name() : null;
        PagedRawResult raw = self.findUsersPage(pageable.getPageNumber(), pageable.getPageSize(), sortBy, sortDir);
        List<UserResponse> content = raw.rows().stream()
                .map(row -> (Object[]) row)
                .map(cols -> {
                    UserResponse response = new UserResponse();
                    response.setUserId((String) cols[0]);
                    response.setUsername((String) cols[1]);
                    response.setEmail((String) cols[2]);
                    response.setIsActive((Boolean) cols[3]);
                    return response;
                })
                .toList();
        attachRolesAndDoctors(content);
        Page<UserResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
    }

    /**
     * Batches {@code roles} and linked {@code doctor} (with that doctor's own
     * departments) onto every {@link UserResponse} in {@code users} — exactly two
     * queries total (one grouped-by-user roles fetch, one batched user re-fetch to read
     * each one's linked {@code doctor_id}), not one per row, so a page of 50 users costs
     * the same two round trips as a page of 5.
     *
     * <p>Each role is {@link #toShallowRoleResponse}, not {@link #toRoleResponse} — id/
     * name/description only, no nested {@code permissions} — deliberately shallower than
     * the single-item {@link #getUser}/{@link #getUserRoles}, which do include each
     * role's permissions. A listing of many users is the wrong place to also expand
     * every one of their roles' full granted-permission lists; a caller that needs a
     * given role's permissions already has {@code GET /api/v1/roles/{roleId}}. Roles are
     * deduplicated by role ID within the batch regardless — computed once per distinct
     * role actually present on this page, not once per (user, role) pair, since the same
     * handful of roles (Admin/Doctor/Receptionist/...) is typically held by many users at
     * once. Distinct linked doctors are memoized the same way through
     * {@code DoctorService.getDoctor}, which is itself {@code @Cacheable} — so a doctor
     * shared by name across pages is usually a Redis hit rather than a DB round trip.
     */
    private void attachRolesAndDoctors(List<UserResponse> users) {
        if (users.isEmpty()) {
            return;
        }
        List<String> userIds = users.stream().map(UserResponse::getUserId).toList();

        Map<String, RoleResponse> roleResponseCache = new HashMap<>();
        Map<String, List<RoleResponse>> rolesByUserId = userRoleRepository
                .findByIdUserIdInAndRevokedAtIsNull(userIds).stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getId().getUserId(),
                        Collectors.mapping(
                                ur -> roleResponseCache.computeIfAbsent(
                                        ur.getRole().getRoleId(), id -> toShallowRoleResponse(ur.getRole())),
                                Collectors.toList())));

        Map<String, String> doctorIdByUserId = userRepository.findAllById(userIds).stream()
                .filter(user -> user.getDoctor() != null)
                .collect(Collectors.toMap(User::getUserId, user -> user.getDoctor().getDoctorId()));
        Map<String, DoctorResponse> doctorResponseCache = new HashMap<>();

        for (UserResponse user : users) {
            user.setRoles(rolesByUserId.getOrDefault(user.getUserId(), List.of()));
            String doctorId = doctorIdByUserId.get(user.getUserId());
            if (doctorId != null) {
                user.setDoctor(doctorResponseCache.computeIfAbsent(doctorId, doctorService::getDoctor));
            }
        }
    }

    /**
     * AOP entry point for {@code FindUserDataAspect} — must be called via {@link #self},
     * never as {@code this.findUsersPage(...)}: Spring AOP proxies only intercept calls
     * made through the proxy, so a same-class call would bypass the aspect and fall
     * through to the body below.
     */
    @FindUserData(domain = "user")
    public PagedRawResult findUsersPage(int page, int size, String sortBy, String sortDir) {
        throw new IllegalStateException("FindUserDataAspect did not intercept this call");
    }

    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUser(String userId) {
        User user = findUserOrThrow(userId);
        UserResponse response = toResponse(user);
        response.setRoles(getUserRoles(userId));
        if (user.getDoctor() != null) {
            response.setDoctor(doctorService.getDoctor(user.getDoctor().getDoctorId()));
        }
        return response;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        UserResponse response = toResponse(userRepository.save(user));

        // Email is mandatory (see UserRequest), so every self-registration always sends
        // the verification link — AuthService.login's gate applies unconditionally now.
        sendVerificationEmail(user);

        return response;
    }

    /** Generates a single-use, Redis-backed verification token (mirrors
     *  {@code AuthService.forgotPassword}'s own reset-token pattern exactly) and, once
     *  it's written, publishes {@link UserRegisteredEvent} rather than emailing directly
     *  (HMS v5) — {@code MailEventListener} sends the real email after this method's
     *  transaction commits. Consumed by {@code AuthService.verifyEmail}. */
    private void sendVerificationEmail(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(EMAIL_VERIFY_TOKEN_PREFIX + token, user.getUserId(),
                Duration.ofHours(emailVerificationTtlHours));
        String verifyUrl = frontendBaseUrl + "/verify-email?token=" + token;
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getEmail(), user.getUsername(), verifyUrl,
                (int) emailVerificationTtlHours));
    }

    /**
     * Admin-provisioned account — unlike {@link #createUser} (self-registration, caller
     * sets their own password), the caller here can't set one at all (see
     * {@link AdminCreateUserRequest}'s own Javadoc): a strong password is always
     * generated and emailed. Sets {@code emailVerifiedAt} immediately rather than going
     * through the link flow — receiving the generated password at that address already
     * proves deliverability, so there's nothing more to verify. Still has no role, same
     * as a self-registered account — an administrator assigns one via the existing
     * {@link #assignRole} endpoint.
     */
    @Transactional
    public UserResponse createUserByAdmin(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }

        String generatedPassword = generateRandomPassword();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        user.setIsActive(true);
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        UserResponse response = toResponse(userRepository.save(user));

        // Never returned in the API response, never logged — the only place this
        // plaintext value ever appears is the AdminCreatedUserEvent below, alive only
        // as long as it takes MailEventListener to email it (HMS v5 — see that class's
        // own Javadoc for why this publishes rather than calling MailService directly).
        eventPublisher.publishEvent(new AdminCreatedUserEvent(user.getEmail(), user.getUsername(), generatedPassword));

        return response;
    }

    private static final String PASSWORD_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String PASSWORD_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "0123456789";
    /** Same allowed special-character set {@code UserRequest.password}'s own regex requires. */
    private static final String PASSWORD_SPECIAL = "@$!%*?&";
    private static final String PASSWORD_ALL_CHARS =
            PASSWORD_UPPER + PASSWORD_LOWER + PASSWORD_DIGITS + PASSWORD_SPECIAL;
    private static final int GENERATED_PASSWORD_LENGTH = 12;

    /** Builds one character from each required class first (guaranteeing the result
     *  always satisfies the same complexity policy every other password on this system
     *  is held to), fills the rest randomly, then shuffles so the guaranteed characters
     *  aren't always in the same positions. */
    private String generateRandomPassword() {
        List<Character> chars = new ArrayList<>(GENERATED_PASSWORD_LENGTH);
        chars.add(PASSWORD_UPPER.charAt(secureRandom.nextInt(PASSWORD_UPPER.length())));
        chars.add(PASSWORD_LOWER.charAt(secureRandom.nextInt(PASSWORD_LOWER.length())));
        chars.add(PASSWORD_DIGITS.charAt(secureRandom.nextInt(PASSWORD_DIGITS.length())));
        chars.add(PASSWORD_SPECIAL.charAt(secureRandom.nextInt(PASSWORD_SPECIAL.length())));
        while (chars.size() < GENERATED_PASSWORD_LENGTH) {
            chars.add(PASSWORD_ALL_CHARS.charAt(secureRandom.nextInt(PASSWORD_ALL_CHARS.length())));
        }
        Collections.shuffle(chars, secureRandom);
        StringBuilder builder = new StringBuilder(chars.size());
        chars.forEach(builder::append);
        return builder.toString();
    }

    @Transactional
    @CachePut(value = "users", key = "#userId")
    public UserResponse updateUser(String userId, UserRequest request) {
        User user = findUserOrThrow(userId);

        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken");
        }
        if (!request.getEmail().equals(user.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        User user = findUserOrThrow(userId);
        user.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        userRepository.save(user);
    }

    // ── Role assignment ────────────────────────────────────────────────────────

    public List<RoleResponse> getUserRoles(String userId) {
        findUserOrThrow(userId);
        return userRoleRepository.findByIdUserId(userId).stream()
                .filter(ur -> ur.getRevokedAt() == null)
                .map(ur -> toRoleResponse(ur.getRole()))
                .toList();
    }

    @Transactional
    public void assignRole(String userId, String roleId) {
        User user = findUserOrThrow(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        UserRole existing = userRoleRepository.findByIdUserIdAndIdRoleId(userId, roleId).orElse(null);
        if (existing != null) {
            if (existing.getRevokedAt() == null) {
                throw new ConflictException("User already holds this role");
            }
            // Re-assigning a previously revoked role updates the existing join row —
            // (userId, roleId) is the composite PK, so a second insert would collide.
            existing.setRevokedAt(null);
            existing.setUpdatedAt(now);
            userRoleRepository.save(existing);
            return;
        }

        UserRoleId id = new UserRoleId();
        id.setUserId(userId);
        id.setRoleId(roleId);

        UserRole userRole = new UserRole();
        userRole.setId(id);
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(now);
        userRole.setUpdatedAt(now);
        userRoleRepository.save(userRole);
    }

    @Transactional
    public void revokeRole(String userId, String roleId) {
        UserRole userRole = userRoleRepository.findByIdUserIdAndIdRoleId(userId, roleId)
                .filter(ur -> ur.getRevokedAt() == null)
                .orElseThrow(() -> new NotFoundException("User does not hold this role"));
        userRole.setRevokedAt(LocalDateTime.now(ZoneId.systemDefault()));
        userRoleRepository.save(userRole);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User findUserOrThrow(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("User not found: " + userId);
        }
        return user;
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setIsActive(user.getIsActive());
        return response;
    }

    /**
     * Eager-loads {@code description}/{@code permissions} too, not just id/name — reuses
     * {@code RoleService.getRolePermissions} rather than a second copy of that query, so a
     * user's roles come back exactly as deep as {@code RoleService.getRole} would return
     * each one on its own.
     */
    private RoleResponse toRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        response.setPermissions(roleService.getRolePermissions(role.getRoleId()));
        return response;
    }

    /** Id/name/description only, no {@code permissions} — see
     *  {@link #attachRolesAndDoctors} for why the paginated listing uses this instead
     *  of {@link #toRoleResponse}. */
    private static RoleResponse toShallowRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        return response;
    }
}
