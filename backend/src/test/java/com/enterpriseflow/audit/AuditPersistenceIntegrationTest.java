package com.enterpriseflow.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterpriseflow.IntegrationTestSupport;
import com.enterpriseflow.project.ProjectService;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.user.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

/** Database-level guarantees: audit entries are written with each change, cannot be altered, and emails are unique ignoring case. */
class AuditPersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditQueryService auditQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void businessActionsWriteAnAuditEntryWithActorAndMetadata() {
        TestUser manager = createUser(RoleName.MANAGER);
        UUID projectId = projectService.create(manager.principal(),
                new CreateProjectRequest("Audited " + UUID.randomUUID(), null, null, null, null, null)).id();

        List<AuditLogResponse> trail = auditQueryService.forEntity(AuditEntityType.PROJECT, projectId);

        assertThat(trail).extracting(AuditLogResponse::action).contains(AuditAction.PROJECT_CREATED);
        AuditLogResponse created = trail.stream()
                .filter(e -> e.action() == AuditAction.PROJECT_CREATED).findFirst().orElseThrow();
        assertThat(created.actorId()).isEqualTo(manager.id());
        assertThat(created.metadata()).containsKey("name");
    }

    @Test
    void auditLogsAreAppendOnlyAtTheDatabaseLevel() {
        TestUser manager = createUser(RoleName.MANAGER);
        UUID projectId = projectService.create(manager.principal(),
                new CreateProjectRequest("Immutable " + UUID.randomUUID(), null, null, null, null, null)).id();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' WHERE project_id = ?", projectId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_logs WHERE project_id = ?", projectId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void auditEntriesCannotBeWrittenOutsideABusinessTransaction() {
        // Propagation.MANDATORY: an audit record without the change it describes is a programming error.
        assertThatThrownBy(() -> auditService.record(UUID.randomUUID(), AuditAction.PROJECT_UPDATED,
                AuditEntityType.PROJECT, UUID.randomUUID(), null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void emailUniquenessIsCaseInsensitive() {
        String email = "Case-" + UUID.randomUUID() + "@Test.Local";
        userService.createUser(email, "First", null, PASSWORD, List.of(RoleName.EMPLOYEE));

        assertThatThrownBy(() -> userService.createUser(email.toUpperCase(), "Second", null, PASSWORD,
                List.of(RoleName.EMPLOYEE)))
                .isInstanceOf(com.enterpriseflow.common.error.ConflictException.class);
    }
}
