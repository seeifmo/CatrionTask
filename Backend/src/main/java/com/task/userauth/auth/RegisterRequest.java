package com.task.userauth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank @Email @Size(max = 254) String email,

        @NotBlank @Size(max = 100) String fullName,

        // 72 is BCrypt's input limit.
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String password) {

    /** Hides the password when a request is logged or printed. */
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", fullName=" + fullName
                + ", password=****]";
    }
}
