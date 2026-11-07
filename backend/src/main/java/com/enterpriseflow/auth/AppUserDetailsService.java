package com.enterpriseflow.auth;

import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Used only by the login flow (AuthenticationManager → DaoAuthenticationProvider). Regular requests are
 * authenticated from the JWT and never touch the database here.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads the account for a login attempt. Deactivated users are returned as disabled, so Spring Security rejects
     * them with the same generic 401 as a wrong password.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // The user id becomes the principal name, so the login flow knows exactly which row authenticated.
        return org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(user.roleNames().stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList())
                .build();
    }
}
