package com.enterpriseflow.project;

import java.util.UUID;

/** Lets the task module react to membership changes without the project module depending on tasks. */
public record ProjectMemberRemovedEvent(UUID projectId, UUID userId) {
}
