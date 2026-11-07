package com.enterpriseflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Smoke tests for the running application: health, migrations, reference data, public docs and deny-by-default security. */
class ApplicationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthEndpointIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void allFlywayMigrationsAreApplied() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        assertThat(applied).isEqualTo(7);
    }

    @Test
    void rolesReferenceDataIsSeeded() {
        List<String> roles = jdbcTemplate.queryForList("SELECT name FROM roles ORDER BY id", String.class);
        assertThat(roles).containsExactly("ADMIN", "MANAGER", "EMPLOYEE");
    }

    @Test
    void openApiSpecIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("EnterpriseFlow API"));
    }

    @Test
    void unknownApiEndpointsAreDeniedByDefault() throws Exception {
        // Deny-by-default: even a route that doesn't exist is rejected before routing,
        // so unauthenticated callers cannot probe which endpoints exist.
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void nonGetRequestsToPublicPathsAreNotAllowedAnonymously() throws Exception {
        mockMvc.perform(post("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }
}
