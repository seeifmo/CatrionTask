package com.task.userauth.dev;

import com.task.userauth.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Test accounts created at startup in the dev profile ({@code app.seed.users}). */
@Validated
@ConfigurationProperties("app.seed")
public record SeedProperties(@Valid List<SeedUser> users) {

    public SeedProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record SeedUser(@NotBlank String username,
                           @NotBlank String password,
                           @NotBlank String email,
                           @NotBlank String fullName,
                           @NotNull Role role) {

        /** Hides the password if the properties are ever logged. */
        @Override
        public String toString() {
            return "SeedUser[username=" + username + ", role=" + role + "]";
        }
    }
}
