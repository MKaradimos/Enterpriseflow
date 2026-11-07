package com.enterpriseflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseflow.IntegrationTestSupport;
import com.enterpriseflow.user.RoleName;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Login, token validation, refresh rotation, CSRF, logout, deactivation and password change. */
class AuthIntegrationTest extends IntegrationTestSupport {

    private static final String COOKIE = "ef_refresh";

    @Test
    void registerThenLoginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "fullName", "New Person", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        MvcResult login = login(email, PASSWORD);
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void registrationRejectsWeakPasswordsWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "weak@test.local", "fullName", "Weak", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGeneric401() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);

        String wrongPassword = mockMvc.perform(loginRequest(user.email(), "WrongPassword1"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(loginRequest("nobody-" + UUID.randomUUID() + "@test.local", PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        // Identical bodies: the response must not reveal whether the account exists.
        assertThat(objectMapper.readTree(wrongPassword).get("detail"))
                .isEqualTo(objectMapper.readTree(unknownEmail).get("detail"));
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        var admin = createUser(RoleName.ADMIN);
        var user = createUser(RoleName.EMPLOYEE);
        userService.setActive(admin.principal(), user.id(), false);

        mockMvc.perform(loginRequest(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void changedPasswordReplacesTheOldOne() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        String newPassword = "NewPassword456";

        mockMvc.perform(post("/api/v1/profile/password").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", newPassword))))
                .andExpect(status().isNoContent());

        // Regression: the new hash used to be lost when sessions were revoked in the same transaction.
        mockMvc.perform(loginRequest(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
        login(user.email(), newPassword);
    }

    @Test
    void requestsWithoutTokenOrWithTamperedTokenAre401() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);

        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, user.bearer() + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = jwtTokenService.encode(JwtClaimsSet.builder()
                .issuer(jwtTokenService.issuer())
                .subject(user.id().toString())
                .issuedAt(past)
                .expiresAt(past.plus(5, ChronoUnit.MINUTES))
                .claim("email", user.email())
                .claim("roles", List.of("EMPLOYEE"))
                .build());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        String foreign = jwtTokenService.encode(JwtClaimsSet.builder()
                .issuer("someone-else")
                .subject(user.id().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .claim("roles", List.of("ADMIN"))
                .build());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        Cookie first = login(user.email(), PASSWORD).getResponse().getCookie(COOKIE);

        Cookie second = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getCookie(COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated token looks like theft: rejected, and the whole family is revoked...
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first).with(csrf()))
                .andExpect(status().isUnauthorized());
        // ...so even the legitimate newest token stops working.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(second).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCsrfTokenIsForbidden() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        Cookie refresh = login(user.email(), PASSWORD).getResponse().getCookie(COOKIE);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        var user = createUser(RoleName.EMPLOYEE);
        Cookie refresh = login(user.email(), PASSWORD).getResponse().getCookie(COOKIE);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().path(COOKIE, "/api/v1/auth"))
                .andReturn();
    }

    private MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }
}
