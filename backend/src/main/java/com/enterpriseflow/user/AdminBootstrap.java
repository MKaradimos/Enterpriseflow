package com.enterpriseflow.user;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on a fresh installation when BOOTSTRAP_ADMIN_EMAIL and
 * BOOTSTRAP_ADMIN_PASSWORD are set. Does nothing once any active admin exists, so the variables can be removed
 * after the first start.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final String email;
    private final String password;

    public AdminBootstrap(UserService userService, UserRepository userRepository,
                          @Value("${app.bootstrap-admin.email:}") String email,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || userRepository.countActiveAdmins() > 0) {
            return;
        }
        userService.createUser(email, "Administrator", null, password, Set.of(RoleName.ADMIN));
        log.info("Bootstrap administrator created: {}", email);
    }
}
