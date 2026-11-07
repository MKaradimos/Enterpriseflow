package com.enterpriseflow.task;

import java.util.EnumSet;
import java.util.Set;

/**
 * Task workflow as an explicit state machine. Keeping the allowed transitions in the enum makes the rules
 * readable in one place and trivial to unit-test.
 *
 * <ul>
 *   <li>TODO → IN_PROGRESS, BLOCKED</li>
 *   <li>IN_PROGRESS → TODO, IN_REVIEW, BLOCKED</li>
 *   <li>IN_REVIEW → IN_PROGRESS (changes requested), DONE</li>
 *   <li>BLOCKED → TODO, IN_PROGRESS</li>
 *   <li>DONE → IN_PROGRESS (reopen; managers only, enforced by the service)</li>
 * </ul>
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    BLOCKED;

    public Set<TaskStatus> allowedNext() {
        return switch (this) {
            case TODO -> EnumSet.of(IN_PROGRESS, BLOCKED);
            case IN_PROGRESS -> EnumSet.of(TODO, IN_REVIEW, BLOCKED);
            case IN_REVIEW -> EnumSet.of(IN_PROGRESS, DONE);
            case BLOCKED -> EnumSet.of(TODO, IN_PROGRESS);
            case DONE -> EnumSet.of(IN_PROGRESS);
        };
    }

    public boolean canTransitionTo(TaskStatus target) {
        return allowedNext().contains(target);
    }
}
