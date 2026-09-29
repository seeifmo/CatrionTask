package com.task.userauth.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 72) String password) {

    /** Hides the password when a request is logged or printed. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
