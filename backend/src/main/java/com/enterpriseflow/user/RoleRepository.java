package com.enterpriseflow.user;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Access to the fixed set of roles seeded by the first migration. */
public interface RoleRepository extends JpaRepository<Role, Short> {

    List<Role> findByNameIn(Collection<RoleName> names);
}
