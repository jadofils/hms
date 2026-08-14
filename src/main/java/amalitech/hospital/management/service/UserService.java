package amalitech.hospital.management.service;

import amalitech.hospital.management.annotation.FindUserData;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.frontend-base-url}")
    private final String frontendBaseUrl;

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
        Page<UserResponse> page = new PageImpl<>(content, pageable, raw.total());
        return new PagedModel<>(page);
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
        return toResponse(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        UserResponse response = toResponse(userRepository.save(user));

        // Registration can't require an email (see the null-guarded existsByEmail check
        // above), so this is skipped rather than sent to a null address.
        if (user.getEmail() != null) {
            mailService.sendNotificationEmail(user.getEmail(), user.getUsername(), "Welcome to HMS",
                    "Welcome to HMS",
                    "Your account has been created. An administrator needs to assign you a role "
                            + "before you can log in — you'll be notified once that's done.",
                    "Open HMS", frontendBaseUrl);
        }

        return response;
    }

    @Transactional
    @CachePut(value = "users", key = "#userId")
    public UserResponse updateUser(String userId, UserRequest request) {
        User user = findUserOrThrow(userId);

        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already taken");
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        User user = findUserOrThrow(userId);
        user.setDeletedAt(LocalDateTime.now());
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

        LocalDateTime now = LocalDateTime.now();
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
        userRole.setRevokedAt(LocalDateTime.now());
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

    private RoleResponse toRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setRoleId(role.getRoleId());
        response.setRoleName(role.getRoleName());
        return response;
    }
}
