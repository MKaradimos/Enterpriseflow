package com.enterpriseflow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    // The entity graph loads roles in the same query; without it, each user.getRoles() call would
    // trigger one extra SELECT (the N+1 problem).
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    boolean existsByEmail(String email);

    // Used to make sure the last admin can't be demoted or deactivated.
    @Query("select count(u) from User u join u.roles r where r.name = com.enterpriseflow.user.RoleName.ADMIN and u.active = true")
    long countActiveAdmins();

    @Query("select u from User u join u.roles r where r.name = com.enterpriseflow.user.RoleName.ADMIN and u.active = true")
    List<User> findActiveAdmins();
}
