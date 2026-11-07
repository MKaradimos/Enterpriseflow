package com.enterpriseflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterpriseflow.audit.AuditService;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure unit test: collaborators are Mockito mocks, so the lockout rules are verified without Spring or a database. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private UserService userService;

    private Role adminRole;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        adminRole = role(RoleName.ADMIN);
        employeeRole = role(RoleName.EMPLOYEE);
    }

    @Test
    void anAdminCannotRemoveTheirOwnAdminRole() {
        User admin = user(adminRole);
        CurrentUser self = new CurrentUser(admin.getId(), admin.getEmail(), Set.of(RoleName.ADMIN));
        when(userRepository.findWithRolesById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateRoles(self, admin.getId(), Set.of(RoleName.EMPLOYEE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("own ADMIN role");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void theLastActiveAdminCannotBeDeactivated() {
        User lastAdmin = user(adminRole);
        CurrentUser otherAdmin = new CurrentUser(UUID.randomUUID(), "other@test.local", Set.of(RoleName.ADMIN));
        when(userRepository.findWithRolesById(lastAdmin.getId())).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> userService.setActive(otherAdmin, lastAdmin.getId(), false))
                .isInstanceOf(ConflictException.class);
        assertThat(lastAdmin.isActive()).isTrue();
    }

    @Test
    void deactivationEndsAllSessionsOfTheUser() {
        User employee = user(employeeRole);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@test.local", Set.of(RoleName.ADMIN));
        when(userRepository.findWithRolesById(employee.getId())).thenReturn(Optional.of(employee));

        userService.setActive(admin, employee.getId(), false);

        assertThat(employee.isActive()).isFalse();
        verify(events).publishEvent(new UserCredentialsInvalidatedEvent(employee.getId()));
    }

    @Test
    void usersCannotDeactivateThemselves() {
        User admin = user(adminRole);
        CurrentUser self = new CurrentUser(admin.getId(), admin.getEmail(), Set.of(RoleName.ADMIN));
        when(userRepository.findWithRolesById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.setActive(self, admin.getId(), false))
                .isInstanceOf(ConflictException.class);
    }

    private static Role role(RoleName name) {
        Role role = mock(Role.class);
        // lenient: not every test reads every role's name.
        lenient().when(role.getName()).thenReturn(name);
        return role;
    }

    private static User user(Role role) {
        User user = new User("u-" + UUID.randomUUID() + "@test.local", "hash", "Some User", List.of(role));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
