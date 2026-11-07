package com.enterpriseflow.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Project membership queries; membership is the basis of project-level access control. */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    /** The core access check: is this user a member of this project? */
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(UUID projectId);
}
