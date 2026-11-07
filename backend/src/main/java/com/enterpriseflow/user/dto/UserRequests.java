package com.enterpriseflow.user.dto;

import com.enterpriseflow.user.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Request bodies for the user and profile endpoints, grouped because each is a few lines. */
public final class UserRequests {

    /**
     * At least 10 characters with a letter and a digit. Length matters more than complexity rules;
     * 72 is BCrypt's input limit, beyond which extra characters are silently ignored.
     */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{10,72}$";
    public static final String PASSWORD_MESSAGE =
            "must be 10-72 characters and contain at least one letter and one digit";

    private UserRequests() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 120) String jobTitle,
            @NotNull @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotEmpty Set<RoleName> roles) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
    }

    public record UpdateStatusRequest(@NotNull Boolean active) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 120) String jobTitle) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotNull @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
