package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.user.AdminCreateUserRequest;
import amalitech.hospital.management.dto.user.PatchUserRequest;
import amalitech.hospital.management.dto.user.UserRequest;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import amalitech.hospital.management.utils.GraphQlPaging;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import io.micrometer.core.annotation.Timed;

/**
 * GraphQL front door for {@link UserService} — the GraphQL analogue of
 * {@code UserController}, delegating to the exact same service so REST and GraphQL never
 * duplicate business logic (see {@code docs/story-4.1-graphql.md}). Reuses the same
 * request DTOs ({@link UserRequest}/{@link AdminCreateUserRequest}) as their REST
 * counterparts — Bean Validation still runs on them (see {@code @Validated} on this
 * class + {@code @Valid} below), rather than a separate, unvalidated GraphQL-only input
 * path.
 *
 * <p>{@code assignRole}/{@code revokeRole} mirror {@code UserController}'s own endpoints,
 * which return no body (204) — here they return the refreshed {@code User} instead,
 * since a GraphQL mutation is expected to return *something* selectable.
 *
 * <p>{@code roles} and {@code doctor} on the {@code User} GraphQL type need no
 * {@code @SchemaMapping} of their own — {@link UserService#getUsers} and
 * {@link UserService#getUser} both already eager-load them onto {@link UserResponse}
 * (see {@code UserService.attachRolesAndDoctors}), so Spring GraphQL's default
 * property-based data fetcher reads {@code UserResponse.getRoles()}/{@code getDoctor()}
 * directly. A dedicated per-row {@code @SchemaMapping} used to exist for {@code roles}
 * here; it was removed because it re-fetched every user's roles one row at a time,
 * reintroducing the exact N+1 the batched service-level fetch exists to avoid.
 */
@Controller
@Validated
@Timed(value = "hms.graphql.requests", extraTags = {"layer", "graphql"}, percentiles = {0.5, 0.95, 0.99})
@RequiredArgsConstructor
public class UserResolver {

    private final UserService userService;

    @QueryMapping
    public List<UserResponse> users(@Argument int page, @Argument int size, @Argument String sort) {
         return userService.getUsers(GraphQlPaging.of(page, size, sort)).getContent();
    }

    @QueryMapping
    public UserResponse user(@Argument String userId) {
        return userService.getUser(userId);
    }

    @MutationMapping
    public UserResponse createUser(@Argument @Valid AdminCreateUserRequest input) {
        return userService.createUserByAdmin(input);
    }

    @MutationMapping
    public UserResponse updateUser(@Argument String userId, @Argument @Valid UserRequest input) {
        return userService.updateUser(userId, input);
    }

    @MutationMapping
    public UserResponse patchUser(@Argument String userId, @Argument @Valid PatchUserRequest input) {
        return userService.patchUser(userId, input);
    }

    @MutationMapping
    public boolean deleteUser(@Argument String userId) {
        userService.deleteUser(userId);
        return true;
    }

    @MutationMapping
    public UserResponse assignRole(@Argument String userId, @Argument String roleId) {
        userService.assignRole(userId, roleId);
        return userService.getUser(userId);
    }

    @MutationMapping
    public UserResponse revokeRole(@Argument String userId, @Argument String roleId) {
        userService.revokeRole(userId, roleId);
        return userService.getUser(userId);
    }
}
