package com.enterpriseflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterpriseflow.TestEntities;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.project.dto.ProjectDtos;
import com.enterpriseflow.user.User;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the Project aggregate's invariants (membership, archiving, dates, progress). No Spring context. */
class ProjectTest {

    private User manager;
    private Project project;

    @BeforeEach
    void setUp() {
        manager = TestEntities.user("Mona Manager");
        project = TestEntities.project(manager);
    }

    @Test
    void theManagerIsAlwaysAMemberAndCannotBeRemoved() {
        assertThat(project.getMembers()).extracting(m -> m.getUser().getId()).containsExactly(manager.getId());

        assertThatThrownBy(() -> project.removeMember(manager.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void addingAnExistingMemberIsANoOp() {
        User employee = TestEntities.user("Eli Employee");

        assertThat(project.addMember(employee, Instant.now())).isPresent();
        assertThat(project.addMember(employee, Instant.now())).isEmpty();
        assertThat(project.getMembers()).hasSize(2);
    }

    @Test
    void archivedProjectsRejectChangesUntilRestored() {
        project.archive();

        assertThatThrownBy(() -> project.update("New", null, null, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> project.addMember(TestEntities.user("X"), Instant.now()))
                .isInstanceOf(ConflictException.class);

        project.restore();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ON_HOLD);
    }

    @Test
    void archivingIsOnlyPossibleThroughTheDedicatedOperation() {
        assertThatThrownBy(() -> project.changeStatus(ProjectStatus.ARCHIVED)).isInstanceOf(ConflictException.class);
    }

    @Test
    void dueDateCannotPrecedeStartDate() {
        LocalDate start = LocalDate.of(2026, 1, 10);

        assertThatThrownBy(() -> project.update("P", null, start, start.minusDays(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void progressIsRoundedPercentageOfDoneTasks() {
        assertThat(ProjectDtos.progressPercent(0, 0)).isZero();
        assertThat(ProjectDtos.progressPercent(3, 1)).isEqualTo(33);
        assertThat(ProjectDtos.progressPercent(3, 2)).isEqualTo(67);
        assertThat(ProjectDtos.progressPercent(4, 4)).isEqualTo(100);
    }
}
