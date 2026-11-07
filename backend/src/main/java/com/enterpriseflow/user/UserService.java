package com.enterpriseflow.user;

import com.enterpriseflow.audit.AuditAction;
import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.error.BadRequestException;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.common.persistence.SpecificationUtils;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.dto.UserRequests.ChangePasswordRequest;
import com.enterpriseflow.user.dto.UserRequests.CreateUserRequest;
import com.enterpriseflow.user.dto.UserRequests.UpdateProfileRequest;
import com.enterpriseflow.user.dto.UserResponse;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts, roles and passwords. Any change that should end existing sessions publishes an event. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       AuditService auditService, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.events = events;
    }

    // ---- Creation ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse registerEmployee(String email, String fullName, String rawPassword) {
        User user = createUser(email, fullName, null, rawPassword, Set.of(RoleName.EMPLOYEE));
        auditService.record(user.getId(), AuditAction.USER_REGISTERED, AuditEntityType.USER, user.getId(), null);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createByAdmin(CurrentUser actor, CreateUserRequest request) {
        User user = createUser(request.email(), request.fullName(), request.jobTitle(), request.password(),
                request.roles());
        auditService.record(actor.id(), AuditAction.USER_CREATED, AuditEntityType.USER, user.getId(), null,
                Map.of("email", user.getEmail(), "roles", sortedNames(user.roleNames())));
        return UserResponse.from(user);
    }

    /** Also used by the demo seeder and the admin bootstrap, which run without an authenticated actor. */
    @Transactional
    public User createUser(String email, String fullName, String jobTitle, String rawPassword,
                           Collection<RoleName> roleNames) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new ConflictException("A user with this email already exists.");
        }
        User user = new User(normalized, passwordEncoder.encode(rawPassword), fullName, resolveRoles(roleNames));
        user.updateProfile(fullName, jobTitle);
        return userRepository.saveAndFlush(user);
    }

    // ---- Administration ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, RoleName role, Boolean active, Pageable pageable) {
        return PageResponse.from(userRepository.findAll(searchSpec(query, role, active), pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(findWithRoles(id));
    }

    @Transactional
    public UserResponse updateRoles(CurrentUser actor, UUID id, Set<RoleName> roleNames) {
        User user = findWithRoles(id);
        Set<RoleName> before = user.roleNames();
        boolean losesAdmin = before.contains(RoleName.ADMIN) && !roleNames.contains(RoleName.ADMIN);

        // Lockout protection: an admin can't demote themselves, and the organization always keeps an active admin.
        if (losesAdmin && user.getId().equals(actor.id())) {
            throw new ConflictException("You cannot remove your own ADMIN role.");
        }
        if (losesAdmin && user.isActive() && userRepository.countActiveAdmins() <= 1) {
            throw new ConflictException("At least one active administrator is required.");
        }

        user.replaceRoles(resolveRoles(roleNames));
        auditService.record(actor.id(), AuditAction.USER_ROLES_CHANGED, AuditEntityType.USER, user.getId(), null,
                Map.of("from", sortedNames(before), "to", sortedNames(roleNames)));
        // Roles live in the access token; ending sessions forces a fresh token with the new roles.
        events.publishEvent(new UserCredentialsInvalidatedEvent(user.getId()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setActive(CurrentUser actor, UUID id, boolean active) {
        User user = findWithRoles(id);
        if (user.isActive() == active) {
            return UserResponse.from(user);
        }
        if (!active) {
            if (user.getId().equals(actor.id())) {
                throw new ConflictException("You cannot deactivate your own account.");
            }
            if (user.isAdmin() && userRepository.countActiveAdmins() <= 1) {
                throw new ConflictException("At least one active administrator is required.");
            }
            user.deactivate();
            events.publishEvent(new UserCredentialsInvalidatedEvent(user.getId()));
        } else {
            user.activate();
        }
        auditService.record(actor.id(), active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntityType.USER, user.getId(), null);
        return UserResponse.from(user);
    }

    /** Lightweight search for pickers (adding project members, assigning tasks). Active users only. */
    @Transactional(readOnly = true)
    public List<UserSummary> lookup(String query, int limit) {
        Specification<User> spec = searchSpec(query, null, true);
        Pageable page = PageRequest.of(0, Math.clamp(limit, 1, 50), Sort.by("fullName"));
        return userRepository.findAll(spec, page).map(UserSummary::from).getContent();
    }

    // ---- Own profile ------------------------------------------------------------------------------------

    @Transactional
    public UserResponse updateProfile(CurrentUser actor, UpdateProfileRequest request) {
        User user = findWithRoles(actor.id());
        user.updateProfile(request.fullName(), request.jobTitle());
        return UserResponse.from(user);
    }

    // Needs the current password and signs the user out everywhere.
    @Transactional
    public void changePassword(CurrentUser actor, ChangePasswordRequest request) {
        User user = findWithRoles(actor.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must differ from the current one.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        events.publishEvent(new UserCredentialsInvalidatedEvent(user.getId()));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public User findWithRoles(UUID id) {
        return userRepository.findWithRolesById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Set<Role> resolveRoles(Collection<RoleName> names) {
        List<Role> roles = roleRepository.findByNameIn(names);
        if (roles.size() != Set.copyOf(names).size()) {
            throw new BadRequestException("Unknown role in " + names);
        }
        return Set.copyOf(roles);
    }

    private static List<String> sortedNames(Collection<RoleName> roles) {
        return roles.stream().map(Enum::name).sorted().toList();
    }

    private static Specification<User> searchSpec(String query, RoleName role, Boolean active) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                predicates.add(cb.or(
                        SpecificationUtils.containsIgnoreCase(cb, root.get("fullName"), query),
                        SpecificationUtils.containsIgnoreCase(cb, root.get("email"), query)));
            }
            if (role != null) {
                Join<User, Role> roles = root.join("roles");
                predicates.add(cb.equal(roles.get("name"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
