package com.task.userauth.user;

/** Public view of a user. Never exposes the password hash. */
public record UserResponse(Long id, String username, String email, String fullName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
