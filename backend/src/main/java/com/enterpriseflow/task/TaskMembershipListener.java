package com.enterpriseflow.task;

import com.enterpriseflow.project.ProjectMemberRemovedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Unassigns open tasks when someone leaves a project. */
@Component
class TaskMembershipListener {

    private final TaskRepository taskRepository;

    TaskMembershipListener(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /** Runs inside the removal transaction: the member is removed and unassigned together, or neither. */
    @EventListener
    void onMemberRemoved(ProjectMemberRemovedEvent event) {
        taskRepository.unassignOpenTasks(event.projectId(), event.userId());
    }
}
