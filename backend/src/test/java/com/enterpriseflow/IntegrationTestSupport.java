package com.enterpriseflow;

import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.security.JwtTokenService;
import com.enterpriseflow.user.RoleName;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests: full application context, real PostgreSQL in a container, MockMvc.
 *
 * <p>All subclasses share one cached Spring context and therefore one database. Tests isolate themselves by
 * creating their own uniquely named users and projects instead of cleaning tables (audit_logs is append-only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    public static final String PASSWORD = "Password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    @Autowired
    protected JwtTokenService jwtTokenService;

    /** Creates a user directly through the service and issues a valid access token for them. */
    protected TestUser createUser(RoleName role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        User user = userService.createUser(email, role.name() + " User", null, PASSWORD, Set.of(role));
        String token = jwtTokenService.issue(user).value();
        return new TestUser(user.getId(), email, token, new CurrentUser(user.getId(), email, Set.of(role)));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    public record TestUser(UUID id, String email, String token, CurrentUser principal) {

        public String bearer() {
            return "Bearer " + token;
        }
    }
}
