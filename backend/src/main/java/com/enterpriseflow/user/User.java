package com.enterpriseflow.user;

import com.enterpriseflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Collection;
import org.hibernate.annotations.BatchSize;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An account in the organization. Emails are unique and stored in lower case; only the BCrypt hash of the
 * password is stored. Users are deactivated rather than deleted, so their history stays attributable.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    // LAZY: single-user lookups load roles with an @EntityGraph. Paged lists can't fetch-join a collection
    // (Hibernate would paginate in memory), so @BatchSize loads the roles of a whole page in one extra query.
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
        // Required by JPA
    }

    public User(String email, String passwordHash, String fullName, Collection<Role> roles) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.roles = new HashSet<>(roles);
    }

    /** Single place where emails are normalized; the database CHECK constraint backs it up. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean hasRole(RoleName role) {
        return roles.stream().anyMatch(r -> r.getName() == role);
    }

    public boolean isAdmin() {
        return hasRole(RoleName.ADMIN);
    }

    /** Managers can lead projects; admins can do everything a manager can. */
    public boolean canManageProjects() {
        return hasRole(RoleName.MANAGER) || isAdmin();
    }

    public Set<RoleName> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }

    /** Fields a user may change on their own profile. */
    public void updateProfile(String fullName, String jobTitle) {
        this.fullName = fullName.trim();
        this.jobTitle = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.trim();
    }

    /** Expects an already hashed password; hashing happens in the service. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** Replaces all roles at once (admin action). */
    public void replaceRoles(Collection<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public void activate() {
        this.active = true;
    }

    /** A deactivated user can no longer log in or refresh tokens. */
    public void deactivate() {
        this.active = false;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
