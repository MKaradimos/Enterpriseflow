package com.enterpriseflow.user;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {

    List<Role> findByNameIn(Collection<RoleName> names);
}
