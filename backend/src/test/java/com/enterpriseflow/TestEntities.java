package com.enterpriseflow;

import com.enterpriseflow.project.Project;
import com.enterpriseflow.user.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds entities for unit tests without a database. Ids are set by reflection, as Hibernate would. */
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(String name) {
        User user = new User(name.toLowerCase().replace(' ', '.') + "@test.local", "hash", name, List.of());
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    public static Project project(User manager) {
        Project project = new Project("Project", null, manager, null, null, null, manager.getId(), Instant.now());
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        return project;
    }
}
