package com.enterpriseflow.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The task workflow as a table of transitions: each row states whether moving from one status to another is allowed. */
class TaskStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "TODO, IN_PROGRESS, true",
            "TODO, BLOCKED, true",
            "TODO, DONE, false",
            "TODO, IN_REVIEW, false",
            "IN_PROGRESS, IN_REVIEW, true",
            "IN_PROGRESS, DONE, false",
            "IN_REVIEW, DONE, true",
            "IN_REVIEW, IN_PROGRESS, true",
            "BLOCKED, IN_PROGRESS, true",
            "BLOCKED, DONE, false",
            "DONE, IN_PROGRESS, true",
            "DONE, TODO, false"
    })
    void transitions(TaskStatus from, TaskStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
