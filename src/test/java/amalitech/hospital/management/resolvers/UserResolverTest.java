package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.user.UserResponse;
import amalitech.hospital.management.dto.user.role.RoleResponse;
import amalitech.hospital.management.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice test for {@link UserResolver} — the GraphQL analogue of a {@code @WebMvcTest}
 * REST controller test: loads just enough context for this one resolver, mocking the
 * underlying {@link UserService} the same way this project's other layer tests do (see
 * CLAUDE.md's Testing section). Business logic itself is already covered by
 * {@code UserServiceTest}; this only exercises the schema <-> resolver mapping.
 */
@GraphQlTest(UserResolver.class)
@Import(GraphQlConfig.class)
class UserResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private UserService userService;

    private UserResponse existingUser() {
        UserResponse response = new UserResponse();
        response.setUserId("user-1");
        response.setUsername("alice");
        response.setEmail("alice@example.com");
        response.setIsActive(true);
        return response;
    }

    @Test
    void user_returnsMappedResponse() {
        when(userService.getUser("user-1")).thenReturn(existingUser());

        graphQlTester.document("{ user(userId: \"user-1\") { userId username isActive } }")
                .execute()
                .path("user.userId").entity(String.class).isEqualTo("user-1")
                .path("user.username").entity(String.class).isEqualTo("alice")
                .path("user.isActive").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void user_roles_delegatesToGetUserRoles() {
        when(userService.getUser("user-1")).thenReturn(existingUser());
        RoleResponse role = new RoleResponse();
        role.setRoleId("role-1");
        role.setRoleName("Admin");
        when(userService.getUserRoles("user-1")).thenReturn(List.of(role));

        graphQlTester.document("{ user(userId: \"user-1\") { roles { roleId roleName } } }")
                .execute()
                .path("user.roles[0].roleName").entity(String.class).isEqualTo("Admin");
    }

    @Test
    void createUser_delegatesToCreateUserByAdmin() {
        UserResponse created = existingUser();
        when(userService.createUserByAdmin(any())).thenReturn(created);

        graphQlTester.document(
                        "mutation { createUser(input: { username: \"alice\", email: \"alice@example.com\" }) { userId } }")
                .execute()
                .path("createUser.userId").entity(String.class).isEqualTo("user-1");
    }

    @Test
    void assignRole_callsServiceThenReturnsRefreshedUser() {
        when(userService.getUser("user-1")).thenReturn(existingUser());

        graphQlTester.document("mutation { assignRole(userId: \"user-1\", roleId: \"role-1\") { userId } }")
                .execute()
                .path("assignRole.userId").entity(String.class).isEqualTo("user-1");

        verify(userService).assignRole(eq("user-1"), eq("role-1"));
    }

    @Test
    void deleteUser_returnsTrue() {
        graphQlTester.document("mutation { deleteUser(userId: \"user-1\") }")
                .execute()
                .path("deleteUser").entity(Boolean.class).isEqualTo(true);

        verify(userService).deleteUser("user-1");
    }
}
