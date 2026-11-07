package com.enterpriseflow.project;

import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project-level authorization, checked server-side on every request that touches a project or its tasks.
 *
 * <ul>
 *   <li>view: ADMIN, or a member of the project</li>
 *   <li>manage: ADMIN, or the project's manager</li>
 * </ul>
 *
 * <p>A user who cannot view a project gets 404, not 403, so project ids cannot be probed. A member who can view
 * but not manage gets 403, because they already know the project exists.
 */
@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    /** Loads a project the caller may see, or throws 404 (also when it exists but is not visible). */
    @Transactional(readOnly = true)
    public Project requireViewable(UUID projectId, CurrentUser user) {
        Project project = projectRepository.findWithManagerById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!canView(project, user)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return project;
    }

    /** Loads a project the caller may change: 404 if they cannot see it, 403 if they can see but not manage it. */
    @Transactional(readOnly = true)
    public Project requireManageable(UUID projectId, CurrentUser user) {
        Project project = requireViewable(projectId, user);
        if (!canManage(project, user)) {
            throw new AccessDeniedException("Only the project manager or an administrator can do this.");
        }
        return project;
    }

    /** Admins see every project; everyone else only the projects they are a member of. */
    public boolean canView(Project project, CurrentUser user) {
        return user.isAdmin() || isMember(project.getId(), user.id());
    }

    /** Admins manage every project; otherwise only the project's own manager. */
    public boolean canManage(Project project, CurrentUser user) {
        return user.isAdmin() || project.isManagedBy(user.id());
    }

    /** Single existence query; used for visibility checks and to validate task assignees. */
    public boolean isMember(UUID projectId, UUID userId) {
        return memberRepository.existsByProjectIdAndUserId(projectId, userId);
    }
}
