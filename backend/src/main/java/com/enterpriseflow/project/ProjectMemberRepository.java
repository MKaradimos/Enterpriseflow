package com.enterpriseflow.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    // The basic "is this user on the project?" check.
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(UUID projectId);
}
